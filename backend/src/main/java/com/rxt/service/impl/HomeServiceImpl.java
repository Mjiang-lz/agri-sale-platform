package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.entity.Demand;
import com.rxt.entity.Knowledge;
import com.rxt.entity.Product;
import com.rxt.mapper.DemandMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.service.HomeService;
import com.rxt.vo.HomeStatisticsVO;
import com.rxt.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页模块业务实现。
 *
 * <p><b>关于「推荐」的口径：</b>数据库里没有推荐位配置表，也没有销量计数器，
 * 唯一能判定「值得放上首页」的客观依据就是内容本身已通过审核、且处于可交易状态。
 * 所以本实现的「推荐商品」= <b>审核通过 + 已上架 + 按发布时间倒序</b>的最新货源，
 * 「最新知识」= <b>审核通过 + 按发布时间倒序</b>。
 * 排序走 {@code create_time}，正好命中 {@code idx_product_audit_status(audit_status, create_time)}
 * 与 {@code idx_knowledge_audit_category(audit_status, category)} 的最左前缀，不需要新加索引。</p>
 *
 * <p>口径上有一条硬要求：<b>首页对游客开放，因此绝不能透出未审核内容。</b>
 * 待审核 / 未通过的商品和知识只对发布者和管理员可见，
 * 这个过滤不能只做在列表接口上，首页这条更短的路径同样要挡。</p>
 */
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    /**
     * 审核通过的状态值。
     *
     * <p>取值与 {@code TradeServiceImpl}、{@code KnowledgeServiceImpl} 一致。
     * 这是字符串枚举（数据库侧没有字典表），三处各写一份字面量是既有的现实，
     * 这里至少保证本类内部只有一处定义，不出现「同一个词写两遍、改漏一处」。</p>
     */
    private static final String AUDIT_APPROVED = "已通过";

    /** {@code t_product.status}：1 上架、0 下架 */
    private static final int PRODUCT_STATUS_ON = 1;

    /** {@code t_demand.status}：1 有效 */
    private static final int DEMAND_STATUS_OPEN = 1;

    /**
     * 单次返回条数上限。
     *
     * <p>首页是推荐位，无论请求多少条都只展示一屏。给 {@code limit} 加上限是为了防止
     * 「{@code ?limit=100000}」被直接翻译成一次全表扫描 —— 这是个免登录接口，
     * 参数攻击者可以随便填。</p>
     */
    private static final int MAX_LIMIT = 50;

    private final ProductMapper productMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final DemandMapper demandMapper;

    @Override
    public List<ProductVO> listHotProducts(int limit) {
        int size = normalizeLimit(limit);
        if (size == 0) {
            return List.of();
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 条件顺序与索引列顺序一致（audit_status 在前），便于命中索引最左前缀
        wrapper.eq(Product::getAuditStatus, AUDIT_APPROVED)
                .eq(Product::getStatus, PRODUCT_STATUS_ON)
                .orderByDesc(Product::getCreateTime);

        return productMapper.selectPage(newPage(size), wrapper)
                .convert(HomeServiceImpl::toProductVO)
                .getRecords();
    }

    @Override
    public List<Knowledge> listLatestKnowledge(int limit) {
        int size = normalizeLimit(limit);
        if (size == 0) {
            return List.of();
        }
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Knowledge::getAuditStatus, AUDIT_APPROVED)
                .orderByDesc(Knowledge::getCreateTime);

        return knowledgeMapper.selectPage(newPage(size), wrapper).getRecords();
    }

    @Override
    public HomeStatisticsVO statistics() {
        HomeStatisticsVO vo = new HomeStatisticsVO();

        vo.setProductCount(productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getAuditStatus, AUDIT_APPROVED)
                .eq(Product::getStatus, PRODUCT_STATUS_ON)));

        // 与求购大厅同一口径：status=1 且 valid_until >= NOW()。
        // 过期的求购在页面上已经看不到，统计里也不能还算它一份，
        // 否则首页写的数字和用户点进去看到的条数对不上。
        vo.setDemandCount(demandMapper.selectCount(new LambdaQueryWrapper<Demand>()
                .eq(Demand::getStatus, DEMAND_STATUS_OPEN)
                .ge(Demand::getValidUntil, LocalDateTime.now())));

        fillKnowledgeStatistics(vo);
        return vo;
    }

    /**
     * 统计已通过审核的知识总数与分类分布。
     *
     * <p>用一次 {@code GROUP BY} 聚合同时算出总数和分布，而不是「先 count 再逐个分类 count」。
     * 分类是自由文本（{@code t_knowledge.category VARCHAR(30)}，无字典表），
     * 逐个枚举分类名去 count 的话，专家新增一个分类就会被漏统计 ——
     * 分组聚合天然覆盖全部取值，不需要维护分类清单。</p>
     */
    private void fillKnowledgeStatistics(HomeStatisticsVO vo) {
        // 这里用 QueryWrapper 而非 LambdaQueryWrapper：select 部分需要写聚合表达式
        // COUNT(*)，Lambda 形式只能引用实体属性，表达不了聚合。
        QueryWrapper<Knowledge> groupBy = new QueryWrapper<>();
        groupBy.select("category", "COUNT(*) AS cnt")
                .eq("audit_status", AUDIT_APPROVED)
                .groupBy("category")
                // 按条数倒序，让前端页签的顺序稳定且有意义（多的在前）
                .orderByDesc("cnt");

        List<Map<String, Object>> rows = knowledgeMapper.selectMaps(groupBy);

        Map<String, Long> byCategory = new LinkedHashMap<>();
        long total = 0L;
        for (Map<String, Object> row : rows) {
            long count = asLong(row.get("cnt"));
            Object category = row.get("category");
            // category 可空；用「未分类」兜底，避免把 null 当 map 键
            // （Jackson 序列化 null 键会写成字符串 "null"，前端就多出一个假分类）
            byCategory.merge(category == null ? "未分类" : category.toString(), count, Long::sum);
            total += count;
        }

        vo.setKnowledgeCount(total);
        vo.setKnowledgeByCategory(byCategory);
    }

    /**
     * 构造「只要前 N 条」的分页对象。
     *
     * <p>第三个参数 {@code searchCount=false} 用来跳过 COUNT 查询。
     * 默认的分页会先查一次总数，而首页只关心前 N 条、根本不用总数，
     * 那次 COUNT 是纯粹的浪费。</p>
     */
    private static <T> Page<T> newPage(int size) {
        return new Page<>(1, size, false);
    }

    /**
     * 把 {@code limit} 收敛到 [0, MAX_LIMIT]。
     *
     * <p>0 是合法输入（调用方明确不要数据），负数一律按 0 处理 ——
     * 负数直接丢给分页插件会得到难以预期的 SQL，不如在这里就截断。</p>
     */
    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 实体 → 视图对象。
     *
     * <p>{@link ProductVO} 的字段是 {@link Product} 的子集且同名同类型，
     * 用 {@code BeanUtils.copyProperties} 比手写十几个 setter 更不容易漏字段。
     * 这里刻意不返回 {@code description} / {@code images} 等详情字段 ——
     * 首页只要卡片信息，正文留在详情接口里按需取。</p>
     */
    private static ProductVO toProductVO(Product product) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        return vo;
    }

    /** MySQL 的 COUNT(*) 在 JDBC 侧可能是 Long / BigInteger 等，统一按 Number 取长整型 */
    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

}
