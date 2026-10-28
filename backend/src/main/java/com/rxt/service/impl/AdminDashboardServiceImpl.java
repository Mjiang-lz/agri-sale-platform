package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rxt.entity.Comment;
import com.rxt.entity.Demand;
import com.rxt.entity.FinanceApply;
import com.rxt.entity.Knowledge;
import com.rxt.entity.LoanGroup;
import com.rxt.entity.Order;
import com.rxt.entity.Product;
import com.rxt.entity.Role;
import com.rxt.entity.User;
import com.rxt.mapper.CommentMapper;
import com.rxt.mapper.DemandMapper;
import com.rxt.mapper.FinanceApplyMapper;
import com.rxt.mapper.KnowledgeMapper;
import com.rxt.mapper.LoanGroupMapper;
import com.rxt.mapper.OrderMapper;
import com.rxt.mapper.ProductMapper;
import com.rxt.mapper.RoleMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.security.PermissionChecker;
import com.rxt.service.AdminDashboardService;
import com.rxt.vo.CountItemVO;
import com.rxt.vo.DailyCountVO;
import com.rxt.vo.DashboardStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据看板业务实现（M6 平台治理）。
 *
 * <p>对应验收判据「数据看板显示真实统计」：所有数字都是当场 COUNT 出来的，
 * 没有写死的常量，也没有历史快照表 —— 演示时新增一条数据、刷新看板，
 * 数字就要跟着动。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /** 审核状态（与 3.4 数据字典 audit_status 词表一致） */
    private static final String AUDIT_PENDING = "待审核";
    private static final String AUDIT_PASSED = "已通过";
    private static final String AUDIT_REJECTED = "未通过";

    /** 融资申请状态词表，顺序即看板展示顺序 */
    private static final List<String> FINANCE_STATUSES = List.of(
            "待匹配", "已组建小组", "已推送银行", "审批通过", "审批未通过", "已放款");

    /** 银行侧待办状态 */
    private static final String FINANCE_STATUS_PUSHED = "已推送银行";

    /** 商品上下架状态：上架 */
    private static final int PRODUCT_ON_SALE = 1;

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    private final PermissionChecker permissionChecker;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final ProductMapper productMapper;
    private final DemandMapper demandMapper;
    private final OrderMapper orderMapper;
    private final FinanceApplyMapper financeApplyMapper;
    private final LoanGroupMapper loanGroupMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final CommentMapper commentMapper;

    @Override
    public DashboardStatisticsVO statistics() {
        permissionChecker.requireAdmin();

        DashboardStatisticsVO vo = new DashboardStatisticsVO();

        // —————— 总量 ——————
        vo.setUserTotal(nz(userMapper.selectCount(null)));
        vo.setProductTotal(nz(productMapper.selectCount(null)));
        vo.setProductOnSale(nz(productMapper.selectCount(
                Wrappers.<Product>lambdaQuery().eq(Product::getStatus, PRODUCT_ON_SALE))));
        vo.setDemandTotal(nz(demandMapper.selectCount(null)));
        vo.setOrderTotal(nz(orderMapper.selectCount(null)));
        vo.setFinanceApplyTotal(nz(financeApplyMapper.selectCount(null)));
        vo.setLoanGroupTotal(nz(loanGroupMapper.selectCount(null)));
        vo.setKnowledgeTotal(nz(knowledgeMapper.selectCount(null)));
        vo.setCommentTotal(nz(commentMapper.selectCount(null)));

        // —————— 待办 ——————
        List<CountItemVO> auditDistribution = buildAuditDistribution();
        vo.setAuditStatusDistribution(auditDistribution);
        vo.setPendingAuditTotal(auditDistribution.stream()
                .filter(item -> AUDIT_PENDING.equals(item.getName()))
                .findFirst()
                .map(CountItemVO::getCount)
                .orElse(0L));
        vo.setPendingFinanceTotal(nz(financeApplyMapper.selectCount(
                Wrappers.<FinanceApply>lambdaQuery().eq(FinanceApply::getApplyStatus, FINANCE_STATUS_PUSHED))));

        // —————— 分布 ——————
        vo.setRoleUserDistribution(buildRoleDistribution());
        vo.setFinanceStatusDistribution(buildFinanceStatusDistribution());

        // —————— 近 7 日趋势 ——————
        vo.setNewUserTrend(dailyTrend((from, to) -> nz(userMapper.selectCount(
                Wrappers.<User>lambdaQuery().ge(User::getCreateTime, from).lt(User::getCreateTime, to)))));
        vo.setNewOrderTrend(dailyTrend((from, to) -> nz(orderMapper.selectCount(
                Wrappers.<Order>lambdaQuery().ge(Order::getCreateTime, from).lt(Order::getCreateTime, to)))));
        vo.setNewFinanceTrend(dailyTrend((from, to) -> nz(financeApplyMapper.selectCount(
                Wrappers.<FinanceApply>lambdaQuery().ge(FinanceApply::getApplyTime, from).lt(FinanceApply::getApplyTime, to)))));

        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    /**
     * 角色分布。角色取自 t_role，不在代码里写死角色名 ——
     * 写死会让新增角色在看板上凭空少一行。
     */
    private List<CountItemVO> buildRoleDistribution() {
        List<CountItemVO> distribution = new ArrayList<>();
        List<Role> roles = roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getRoleId));
        for (Role role : roles) {
            distribution.add(new CountItemVO(role.getRoleName(), nz(userMapper.selectCount(
                    Wrappers.<User>lambdaQuery().eq(User::getRoleId, role.getRoleId())))));
        }
        return distribution;
    }

    /**
     * 融资申请状态分布。按数据字典的固定词表输出，数量为 0 的状态也保留 ——
     * 隐藏空状态会让「待匹配 0 笔」这件事看起来像功能缺失。
     */
    private List<CountItemVO> buildFinanceStatusDistribution() {
        List<CountItemVO> distribution = new ArrayList<>();
        for (String status : FINANCE_STATUSES) {
            distribution.add(new CountItemVO(status, nz(financeApplyMapper.selectCount(
                    Wrappers.<FinanceApply>lambdaQuery().eq(FinanceApply::getApplyStatus, status)))));
        }
        return distribution;
    }

    /**
     * 内容审核状态分布：商品、知识、评论三类内容按状态合计。
     */
    private List<CountItemVO> buildAuditDistribution() {
        List<CountItemVO> distribution = new ArrayList<>();
        for (String status : List.of(AUDIT_PENDING, AUDIT_PASSED, AUDIT_REJECTED)) {
            long count = nz(productMapper.selectCount(
                            Wrappers.<Product>lambdaQuery().eq(Product::getAuditStatus, status)))
                    + nz(knowledgeMapper.selectCount(
                            Wrappers.<Knowledge>lambdaQuery().eq(Knowledge::getAuditStatus, status)))
                    + nz(commentMapper.selectCount(
                            Wrappers.<Comment>lambdaQuery().eq(Comment::getAuditStatus, status)));
            distribution.add(new CountItemVO(status, count));
        }
        return distribution;
    }

    /**
     * 近 {@value #TREND_DAYS} 日按天新增趋势。
     *
     * <p>逐日一条 COUNT，而不是用 {@code GROUP BY DATE(create_time)} 换一条 SQL：
     * 本平台是演示级数据量，多跑十几次毫秒级 COUNT 无感；而按日期函数分组拿到的
     * 结果集列名大小写在不同 MySQL 版本与驱动下并不一致，为了省这点查询去赌一个
     * 解析细节不划算。缺数据的日期补 0，前端不必自己补空。</p>
     */
    private List<DailyCountVO> dailyTrend(DailyCountQuery query) {
        LocalDate today = LocalDate.now();
        List<DailyCountVO> trend = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long count = query.count(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            trend.add(new DailyCountVO(day.toString(), count));
        }
        return trend;
    }

    /** COUNT 结果兜底：MyBatis-Plus 查不到时可能返回 null。 */
    private long nz(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 单日区间计数回调。
     */
    @FunctionalInterface
    private interface DailyCountQuery {
        long count(LocalDateTime from, LocalDateTime to);
    }
}
