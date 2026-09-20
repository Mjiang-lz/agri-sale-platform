package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.BizException;
import com.rxt.common.ResultCode;
import com.rxt.entity.AuditRecord;
import com.rxt.entity.Comment;
import com.rxt.entity.Knowledge;
import com.rxt.entity.Product;
import com.rxt.entity.User;
import com.rxt.mapper.AuditRecordMapper;
import com.rxt.mapper.CommentMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.service.AuditService;
import com.rxt.vo.AuditTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内容审核模块业务实现（M6 / REQ-15）。
 *
 * <p>数据来源：待审队列查业务表 t_product / t_knowledge / t_comment；
 * 已审记录查 t_audit 流水。详见 {@link AuditService} 的说明。</p>
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    /** 内容类型 */
    private static final String TYPE_PRODUCT = "PRODUCT";
    private static final String TYPE_KNOWLEDGE = "KNOWLEDGE";
    private static final String TYPE_COMMENT = "COMMENT";

    /**
     * 本模块管辖的内容类型白名单。
     *
     * <p>t_audit 是所有审核的公共流水表，除了商品/知识/评论，还记录了 USER、EXPERT
     * 等<b>主体审核</b>（属 M1 范围，无对应内容表可回查标题）。本接口只服务内容审核，
     * 因此查流水时用 in 限定在这三种类型内，避免返回一堆标题为 null 的杂项记录。</p>
     */
    private static final List<String> CONTENT_TYPES = List.of(TYPE_PRODUCT, TYPE_KNOWLEDGE, TYPE_COMMENT);

    /** 业务表使用的审核状态词表 */
    private static final String STATUS_PENDING = "待审核";
    private static final String STATUS_PASSED = "已通过";
    private static final String STATUS_REJECTED = "未通过";

    /** t_audit.audit_result 使用的审核结论词表 */
    private static final String RESULT_PASS = "通过";
    private static final String RESULT_REJECT = "不通过";

    /** 评论摘要最大长度，超出截断 */
    private static final int COMMENT_SUMMARY_LENGTH = 50;

    private final ProductMapper productMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final CommentMapper commentMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final UserMapper userMapper;
    private final PermissionChecker permissionChecker;

    // =====================================================================
    // 一、列表类接口
    // =====================================================================

    @Override
    public IPage<AuditTaskVO> pageAudits(String targetType, String auditStatus, long pageNum, long pageSize) {
        // 内容审核是平台治理职能，仅管理员可访问（t_role 5 的菜单权限本就含 audit）
        permissionChecker.requireAdmin();

        // 0) 类型白名单校验：传了就必须是本模块管辖的三种内容类型之一
        //    （不校验的话，前端传 EXPERT 会查出一批没有标题的流水，看着像 bug）
        if (StringUtils.hasText(targetType) && !CONTENT_TYPES.contains(targetType)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "内容类型不合法：" + targetType);
        }

        // 1) 状态兜底：不传状态时按"待审核"处理（审核后台首屏就是待办队列）
        String status = StringUtils.hasText(auditStatus) ? auditStatus : STATUS_PENDING;

        // 2) 按状态路由到两个不同的数据源
        if (STATUS_PENDING.equals(status)) {
            // 待审核：业务表里 audit_status = '待审核' 的内容
            return pagePendingContents(targetType, pageNum, pageSize);
        }
        if (STATUS_PASSED.equals(status) || STATUS_REJECTED.equals(status)) {
            // 已通过 / 未通过：t_audit 审核流水
            return pageAuditedRecords(targetType, status, pageNum, pageSize);
        }

        // 3) 非法状态直接拒绝，避免拼出恒不成立的 SQL 让前端看到"暂无数据"
        throw new BizException(ResultCode.BAD_REQUEST.getCode(), "审核状态不合法：" + auditStatus);
    }

    @Override
    public IPage<AuditTaskVO> pagePendingAudits(String targetType, long pageNum, long pageSize) {
        // 语义更明确的快捷方式，逻辑完全复用统一入口（权限校验也在统一入口内）
        return pageAudits(targetType, STATUS_PENDING, pageNum, pageSize);
    }

    // ---------------------------------------------------------------------
    // 待审核分支：数据来自三张业务表
    // ---------------------------------------------------------------------

    /**
     * 待审队列分页。
     *
     * <p>指定类型时走单表分页；不限类型时三张表归并后分页（见 {@link #pageAllPendingContents}）。</p>
     */
    private IPage<AuditTaskVO> pagePendingContents(String targetType, long pageNum, long pageSize) {
        if (StringUtils.hasText(targetType)) {
            return pagePendingByType(targetType, pageNum, pageSize);
        }
        return pageAllPendingContents(pageNum, pageSize);
    }

    /**
     * 指定类型的待审队列：直接对单张业务表分页，交给数据库做 LIMIT。
     */
    private IPage<AuditTaskVO> pagePendingByType(String targetType, long pageNum, long pageSize) {
        IPage<AuditTaskVO> result = switch (targetType) {
            case TYPE_PRODUCT -> {
                Page<Product> page = new Page<>(pageNum, pageSize);
                LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Product::getAuditStatus, STATUS_PENDING)
                        .orderByDesc(Product::getCreateTime);
                yield productMapper.selectPage(page, wrapper).convert(this::pendingProductVO);
            }
            case TYPE_KNOWLEDGE -> {
                Page<Knowledge> page = new Page<>(pageNum, pageSize);
                LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Knowledge::getAuditStatus, STATUS_PENDING)
                        .orderByDesc(Knowledge::getCreateTime);
                yield knowledgeMapper.selectPage(page, wrapper).convert(this::pendingKnowledgeVO);
            }
            case TYPE_COMMENT -> {
                Page<Comment> page = new Page<>(pageNum, pageSize);
                LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Comment::getAuditStatus, STATUS_PENDING)
                        .orderByDesc(Comment::getCreateTime);
                yield commentMapper.selectPage(page, wrapper).convert(this::pendingCommentVO);
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST.getCode(), "内容类型不合法：" + targetType);
        };

        // 待审数据是从业务表取出来的，标题/提交时间已有，只差提交人姓名
        fillNames(result.getRecords());
        return result;
    }

    /**
     * 不限类型的待审队列：三张表结构不同，无法交给数据库做跨表分页，改为应用层归并。
     *
     * <p>关键思路：全局第 N 页（每页 size 条）的记录，一定落在各表各自的前 N*size 条之内。
     * 因此每张表各取前 N*size 条待审记录，归并排序后切出当前页窗口，结果与真值一致。
     * 待审数据量小（审核后即离开队列），这个窗口足以覆盖。</p>
     */
    private IPage<AuditTaskVO> pageAllPendingContents(long pageNum, long pageSize) {
        // 每张表需要取的行数：pageNum * pageSize
        long fetchSize = pageNum * pageSize;
        List<AuditTaskVO> merged = new ArrayList<>();
        long total = 0;

        // 1) 商品
        Page<Product> productPage = productMapper.selectPage(
                new Page<>(1, fetchSize),
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getAuditStatus, STATUS_PENDING)
                        .orderByDesc(Product::getCreateTime));
        total += productPage.getTotal();
        productPage.getRecords().forEach(item -> merged.add(pendingProductVO(item)));

        // 2) 知识
        Page<Knowledge> knowledgePage = knowledgeMapper.selectPage(
                new Page<>(1, fetchSize),
                new LambdaQueryWrapper<Knowledge>()
                        .eq(Knowledge::getAuditStatus, STATUS_PENDING)
                        .orderByDesc(Knowledge::getCreateTime));
        total += knowledgePage.getTotal();
        knowledgePage.getRecords().forEach(item -> merged.add(pendingKnowledgeVO(item)));

        // 3) 评论
        Page<Comment> commentPage = commentMapper.selectPage(
                new Page<>(1, fetchSize),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getAuditStatus, STATUS_PENDING)
                        .orderByDesc(Comment::getCreateTime));
        total += commentPage.getTotal();
        commentPage.getRecords().forEach(item -> merged.add(pendingCommentVO(item)));

        // 2) 归并排序：提交时间倒序，时间相同则按类型 + ID 兜底保证顺序稳定；null 排最后
        //    显式写出泛型参数（Comparator.<LocalDateTime>reverseOrder），避免类型推断歧义
        merged.sort(Comparator
                .comparing(AuditTaskVO::getSubmitTime,
                        Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                .thenComparing(AuditTaskVO::getTargetType,
                        Comparator.nullsLast(Comparator.<String>naturalOrder()))
                .thenComparing(AuditTaskVO::getTargetId,
                        Comparator.nullsLast(Comparator.<Long>naturalOrder())));

        // 3) 切出当前页窗口
        int from = (int) ((pageNum - 1) * pageSize);
        List<AuditTaskVO> records;
        if (from >= merged.size()) {
            records = new ArrayList<>();
        } else {
            records = new ArrayList<>(merged.subList(from, (int) Math.min(from + pageSize, merged.size())));
        }

        Page<AuditTaskVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(total);
        result.setRecords(records);
        fillNames(records);
        return result;
    }

    /**
     * 已审核分支：数据来自 t_audit。
     *
     * @param auditStatus 统一口径状态（已通过 / 未通过），需翻译成 t_audit 的结论词表
     */
    private IPage<AuditTaskVO> pageAuditedRecords(String targetType, String auditStatus,
                                                  long pageNum, long pageSize) {
        // 词表转换：已通过 → 通过，未通过 → 不通过
        String auditResult = STATUS_PASSED.equals(auditStatus) ? RESULT_PASS : RESULT_REJECT;

        Page<AuditRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditRecord::getAuditResult, auditResult);
        if (StringUtils.hasText(targetType)) {
            wrapper.eq(AuditRecord::getTargetType, targetType);
        } else {
            // 不限类型时也要排除 USER / EXPERT 主体审核流水（本模块不管这些）
            wrapper.in(AuditRecord::getTargetType, CONTENT_TYPES);
        }
        wrapper.orderByDesc(AuditRecord::getAuditTime);

        IPage<AuditTaskVO> result = auditRecordMapper.selectPage(page, wrapper).convert(this::toVO);
        // 已审记录来自 t_audit，只有 target_type/target_id/auditor_id
        // → 标题和提交人回业务表补，姓名回用户表补，各一次 SQL
        fillTargetInfo(result.getRecords());
        fillNames(result.getRecords());
        return result;
    }

    @Override
    public IPage<AuditTaskVO> pageMyAudits(Long auditorId, String targetType, long pageNum, long pageSize) {
        permissionChecker.requireAdmin();

        // 身份来自登录态（UserContext），正常情况不会为空；为空说明拦截器被绕过
        // 防御性兜底：绝不让 null 拼进 SQL（会生成恒不成立的 order by auditor_id = NULL）
        if (auditorId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // 1) 分页对象：第几页、每页多少条
        Page<AuditRecord> page = new Page<>(pageNum, pageSize);

        // 2) 条件构造：审核人是我 —— "我的审核记录"的核心过滤条件
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditRecord::getAuditorId, auditorId);

        // 内容类型可选：传了就按类型筛，没传看全部内容类型（排除主体审核流水）
        // 用 hasText 而非 != null：空字符串同样视为"没传"，避免拼出恒不成立的条件
        if (StringUtils.hasText(targetType)) {
            wrapper.eq(AuditRecord::getTargetType, targetType);
        } else {
            wrapper.in(AuditRecord::getTargetType, CONTENT_TYPES);
        }

        // 最近审的排前面
        wrapper.orderByDesc(AuditRecord::getAuditTime);

        // 3) 分页查询后把 AuditRecord 转成 AuditTaskVO
        //    convert 是 MyBatis-Plus 提供的批量转换，一行顶一个 for 循环
        Page<AuditRecord> recordPage = auditRecordMapper.selectPage(page, wrapper);
        IPage<AuditTaskVO> result = recordPage.convert(this::toVO);

        // 4) 补齐标题 / 提交人 / 时间与两个姓名（共 2 次 SQL，而非每条 1 次）
        fillTargetInfo(result.getRecords());
        fillNames(result.getRecords());
        return result;
    }

    // =====================================================================
    // 二、详情类接口
    // =====================================================================

    @Override
    public AuditTaskVO auditDetail(Long auditId) {
        permissionChecker.requireAdmin();

        // 1) 按主键查审核流水；查不到说明前端传了个不存在的 ID，必须报错而不是返回 null
        AuditRecord record = auditRecordMapper.selectById(auditId);
        if (record == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "审核记录不存在：" + auditId);
        }

        // 2) 先转成直接字段（含 auditStatus 口径统一）
        AuditTaskVO vo = toVO(record);

        // 3) t_audit 只存 target_type/target_id，标题和提交人得回业务表补
        //    这里只有一条记录，但复用列表的批量方法——一套代码两种场景，不存在"详情版/列表版"两套逻辑
        List<AuditTaskVO> single = new ArrayList<>(1);
        single.add(vo);
        fillTargetInfo(single);
        fillNames(single);
        return vo;
    }

    @Override
    public AuditTaskVO contentDetail(String targetType, Long targetId) {
        permissionChecker.requireAdmin();

        // 1) 按类型分流，取内容全文与提交人；同时把业务表里的 audit_status 带出来
        //    （审核员可能点开一条已审过的内容，所以状态不能硬编码成"待审核"）
        AuditTaskVO vo = switch (targetType) {
            case TYPE_PRODUCT -> {
                Product product = productMapper.selectById(targetId);
                if (product == null) {
                    throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在：" + targetId);
                }
                AuditTaskVO item = newPendingVO(TYPE_PRODUCT, product.getProductId(),
                        product.getProductName(), product.getSellerId(), product.getCreateTime());
                item.setContent(product.getDescription());
                item.setAuditStatus(product.getAuditStatus());
                yield item;
            }
            case TYPE_KNOWLEDGE -> {
                Knowledge knowledge = knowledgeMapper.selectById(targetId);
                if (knowledge == null) {
                    throw new BizException(ResultCode.NOT_FOUND.getCode(), "知识不存在：" + targetId);
                }
                AuditTaskVO item = newPendingVO(TYPE_KNOWLEDGE, knowledge.getKnowledgeId(),
                        knowledge.getTitle(), knowledge.getAuthorId(), knowledge.getCreateTime());
                item.setContent(knowledge.getContent());
                item.setAuditStatus(knowledge.getAuditStatus());
                yield item;
            }
            case TYPE_COMMENT -> {
                Comment comment = commentMapper.selectById(targetId);
                if (comment == null) {
                    throw new BizException(ResultCode.NOT_FOUND.getCode(), "评论不存在：" + targetId);
                }
                AuditTaskVO item = newPendingVO(TYPE_COMMENT, comment.getCommentId(),
                        summarize(comment.getContent()), comment.getUserId(), comment.getCreateTime());
                item.setContent(comment.getContent());
                item.setAuditStatus(comment.getAuditStatus());
                yield item;
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST.getCode(), "内容类型不合法：" + targetType);
        };

        // 2) 挂上最近一次审核记录（该内容可能从未被审过，此时 latest 为 null，属正常情况）
        AuditRecord latest = latestAuditRecord(targetType, targetId);
        if (latest != null) {
            vo.setAuditId(latest.getAuditId());
            vo.setAuditResult(latest.getAuditResult());
            vo.setAuditRemark(latest.getAuditRemark());
            vo.setAuditorId(latest.getAuditorId());
            vo.setAuditTime(latest.getAuditTime());
        }

        // 3) 补齐提交人 / 审核人姓名
        List<AuditTaskVO> single = new ArrayList<>(1);
        single.add(vo);
        fillNames(single);
        return vo;
    }

    /**
     * 取某条内容最近一次审核记录；从未被审核过时返回 null。
     */
    private AuditRecord latestAuditRecord(String targetType, Long targetId) {
        return auditRecordMapper.selectOne(new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getTargetType, targetType)
                .eq(AuditRecord::getTargetId, targetId)
                .orderByDesc(AuditRecord::getAuditTime)
                // selectOne 要求结果唯一，靠 LIMIT 1 兜底；不加这句若存在多条流水会直接抛异常
                .last("LIMIT 1"));
    }

    // =====================================================================
    // 三、历史类接口
    // =====================================================================

    @Override
    public List<AuditTaskVO> listAuditLogs(String targetType, Long targetId) {
        permissionChecker.requireAdmin();

        // 1) 构造条件构造器
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<>();

        // 2) 两个等值条件：目标类型、目标 ID（联合定位一条内容）
        wrapper.eq(AuditRecord::getTargetType, targetType)
                .eq(AuditRecord::getTargetId, targetId);

        // 3) 按审核时间倒序：最近一次审核排最前
        wrapper.orderByDesc(AuditRecord::getAuditTime);

        // 4) 执行查询
        List<AuditRecord> records = auditRecordMapper.selectList(wrapper);

        // 5) 逐条转 VO 后返回（预分配容量，避免列表扩容开销）
        List<AuditTaskVO> logs = new ArrayList<>(records.size());
        for (AuditRecord record : records) {
            logs.add(toVO(record));
        }

        // 6) 补齐标题 / 提交人与两个姓名
        fillTargetInfo(logs);
        fillNames(logs);
        return logs;
    }

    // =====================================================================
    // 四、转换辅助方法（下一步实现，先给出签名供你调用）
    // =====================================================================

    /**
     * 把 t_audit 记录转成列表视图对象（仅直接字段，关联字段由补齐方法填充）。
     */
    private AuditTaskVO toVO(AuditRecord record) {
        AuditTaskVO vo = new AuditTaskVO();
        vo.setAuditId(record.getAuditId());
        vo.setTargetType(record.getTargetType());
        vo.setTargetId(record.getTargetId());
        vo.setAuditResult(record.getAuditResult());
        vo.setAuditRemark(record.getAuditRemark());
        vo.setAuditorId(record.getAuditorId());
        vo.setAuditTime(record.getAuditTime());
        // 审核结论统一口径：前端只看 auditStatus 决定标签文案与颜色
        vo.setAuditStatus(toAuditStatus(record.getAuditResult()));
        return vo;
    }

    /**
     * 审核结论 → 统一审核状态：通过 → 已通过，不通过 → 未通过。
     */
    private String toAuditStatus(String auditResult) {
        // 常量在前、变量在后：auditResult 为 null 时也不会抛 NPE
        if (RESULT_PASS.equals(auditResult)) {
            return STATUS_PASSED;
        }
        if (RESULT_REJECT.equals(auditResult)) {
            return STATUS_REJECTED;
        }
        // 兜底：未知结论原样返回，便于排查数据问题，而不是静默变成 null
        return auditResult;
    }

    /**
     * 评论内容取摘要，超出 COMMENT_SUMMARY_LENGTH 截断并加省略号。
     */
    private String summarize(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= COMMENT_SUMMARY_LENGTH) {
            return content;
        }
        return content.substring(0, COMMENT_SUMMARY_LENGTH) + "...";
    }

    // =====================================================================
    // 五、待审内容的类型适配
    // 三张业务表的字段名完全不同（商品叫 product_name/seller_id、知识叫 title/author_id、
    // 评论没有标题只有 content/user_id），在这里收敛成统一的 AuditTaskVO。
    // =====================================================================

    /** 商品 → 待审视图：标题取商品名，提交人取卖家，提交时间取创建时间 */
    private AuditTaskVO pendingProductVO(Product product) {
        return newPendingVO(TYPE_PRODUCT, product.getProductId(), product.getProductName(),
                product.getSellerId(), product.getCreateTime());
    }

    /** 知识 → 待审视图：标题取 title，提交人取 author_id */
    private AuditTaskVO pendingKnowledgeVO(Knowledge knowledge) {
        return newPendingVO(TYPE_KNOWLEDGE, knowledge.getKnowledgeId(), knowledge.getTitle(),
                knowledge.getAuthorId(), knowledge.getCreateTime());
    }

    /** 评论 → 待审视图：没有标题，用内容摘要代替；提交人取 user_id */
    private AuditTaskVO pendingCommentVO(Comment comment) {
        return newPendingVO(TYPE_COMMENT, comment.getCommentId(), summarize(comment.getContent()),
                comment.getUserId(), comment.getCreateTime());
    }

    /**
     * 三类待审内容共用的 VO 组装：差异只在主键、标题、提交人、时间四个来源。
     */
    private AuditTaskVO newPendingVO(String targetType, Long targetId, String targetTitle,
                                     Long submitterId, LocalDateTime submitTime) {
        AuditTaskVO vo = new AuditTaskVO();
        vo.setTargetType(targetType);
        vo.setTargetId(targetId);
        vo.setTargetTitle(targetTitle);
        vo.setSubmitterId(submitterId);
        vo.setSubmitTime(submitTime);
        // 待审队列里的内容状态恒为"待审核"；auditId 等审核结论字段保持 null
        vo.setAuditStatus(STATUS_PENDING);
        return vo;
    }

    // =====================================================================
    // 六、关联信息批量补齐
    // t_audit 只存 target_type / target_id / auditor_id，列表要显示"内容标题 + 提交人姓名 +
    // 审核人姓名"，这些都得回业务表和用户表补。核心要求：一次 SQL 补齐一整页，绝不逐行查。
    // =====================================================================

    /**
     * 补齐被审内容的标题、提交人、提交时间（已审记录专用）。
     *
     * <p>待审队列自己就是从业务表查出来的，标题和提交人天然就有，不需要补；
     * 而已审记录来自 t_audit，只有 target_type + target_id，必须回表。</p>
     *
     * <p>做法：先按类型分组，每类只发一次 IN 查询，再用 Map 回填。
     * 若逐条 vo 去查业务表，一页 10 条就是 10 次 SQL，这就是 N+1。</p>
     */
    private void fillTargetInfo(List<AuditTaskVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }

        // 1) 按内容类型分组：PRODUCT 一组、KNOWLEDGE 一组、COMMENT 一组
        Map<String, List<AuditTaskVO>> groups = vos.stream()
                .filter(vo -> StringUtils.hasText(vo.getTargetType()) && vo.getTargetId() != null)
                .collect(Collectors.groupingBy(AuditTaskVO::getTargetType));

        // 2) 每类一次 IN 查询，各自回填
        for (Map.Entry<String, List<AuditTaskVO>> entry : groups.entrySet()) {
            String targetType = entry.getKey();
            List<AuditTaskVO> group = entry.getValue();
            List<Long> targetIds = group.stream()
                    .map(AuditTaskVO::getTargetId)
                    .distinct()
                    .collect(Collectors.toList());

            switch (targetType) {
                case TYPE_PRODUCT -> {
                    // 只 select 需要的 4 列：description 是 TEXT，列表用不上，别白查回来
                    List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                            .select(Product::getProductId, Product::getProductName,
                                    Product::getSellerId, Product::getCreateTime)
                            .in(Product::getProductId, targetIds));
                    Map<Long, Product> map = products.stream()
                            .collect(Collectors.toMap(Product::getProductId, p -> p));
                    for (AuditTaskVO vo : group) {
                        Product product = map.get(vo.getTargetId());
                        if (product != null) {
                            vo.setTargetTitle(product.getProductName());
                            vo.setSubmitterId(product.getSellerId());
                            vo.setSubmitTime(product.getCreateTime());
                        }
                    }
                }
                case TYPE_KNOWLEDGE -> {
                    List<Knowledge> knowledgeList = knowledgeMapper.selectList(
                            new LambdaQueryWrapper<Knowledge>()
                                    .select(Knowledge::getKnowledgeId, Knowledge::getTitle,
                                            Knowledge::getAuthorId, Knowledge::getCreateTime)
                                    .in(Knowledge::getKnowledgeId, targetIds));
                    Map<Long, Knowledge> map = knowledgeList.stream()
                            .collect(Collectors.toMap(Knowledge::getKnowledgeId, k -> k));
                    for (AuditTaskVO vo : group) {
                        Knowledge knowledge = map.get(vo.getTargetId());
                        if (knowledge != null) {
                            vo.setTargetTitle(knowledge.getTitle());
                            vo.setSubmitterId(knowledge.getAuthorId());
                            vo.setSubmitTime(knowledge.getCreateTime());
                        }
                    }
                }
                case TYPE_COMMENT -> {
                    // 评论没有标题，标题用内容摘要；summary 需要 content，所以这里得查出来
                    List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                            .select(Comment::getCommentId, Comment::getContent,
                                    Comment::getUserId, Comment::getCreateTime)
                            .in(Comment::getCommentId, targetIds));
                    Map<Long, Comment> map = comments.stream()
                            .collect(Collectors.toMap(Comment::getCommentId, c -> c));
                    for (AuditTaskVO vo : group) {
                        Comment comment = map.get(vo.getTargetId());
                        if (comment != null) {
                            vo.setTargetTitle(summarize(comment.getContent()));
                            vo.setSubmitterId(comment.getUserId());
                            vo.setSubmitTime(comment.getCreateTime());
                        }
                    }
                }
                default -> {
                    // 未知类型不处理：可能是历史脏数据，静默跳过好过让整个列表接口 500
                }
            }
        }
    }

    /**
     * 批量补齐提交人 / 审核人姓名。
     */
    private void fillNames(List<AuditTaskVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }

        // 1) 收集这一页涉及到的所有用户 ID（提交人 + 审核人去重）
        Set<Long> userIds = new HashSet<>();
        for (AuditTaskVO vo : vos) {
            if (vo.getSubmitterId() != null) {
                userIds.add(vo.getSubmitterId());
            }
            if (vo.getAuditorId() != null) {
                userIds.add(vo.getAuditorId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }

        // 2) 一次查完，再回填
        Map<Long, String> nameMap = loadUserNames(userIds);
        for (AuditTaskVO vo : vos) {
            // HashMap.get(null) 返回 null，不会抛异常，所以不必先判空
            vo.setSubmitterName(nameMap.get(vo.getSubmitterId()));
            vo.setAuditorName(nameMap.get(vo.getAuditorId()));
        }
    }

    /**
     * 批量查用户姓名：入参是若干用户 ID，返回 id → realName 的映射。
     * 一次 IN 查询替代逐行查询（N+1 问题的解法）。
     */
    private Map<Long, String> loadUserNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        // 只查 userId + realName 两列；phone/password 这类敏感字段不要带出来
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getUserId, User::getRealName)
                .in(User::getUserId, userIds));

        Map<Long, String> nameMap = new HashMap<>(users.size());
        for (User user : users) {
            nameMap.put(user.getUserId(), user.getRealName());
        }
        return nameMap;
    }
}
