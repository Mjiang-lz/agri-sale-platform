package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据看板统计视图对象（M6 平台治理 · 管理后台仪表盘）。
 *
 * <p>全部指标取自业务表实时统计，不做缓存、不写死 ——
 * 演示时管理员在别处新增一条数据，刷新看板就要能看到数字变化。</p>
 */
@Data
public class DashboardStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // —————————————— 总量概览 ——————————————

    /** 用户总数 */
    private long userTotal;

    /** 商品总数 */
    private long productTotal;

    /** 在架商品数（上下架状态为 1） */
    private long productOnSale;

    /** 求购需求总数 */
    private long demandTotal;

    /** 订单总数 */
    private long orderTotal;

    /** 融资申请总数 */
    private long financeApplyTotal;

    /** 联合贷款小组总数 */
    private long loanGroupTotal;

    /** 农业知识条目数 */
    private long knowledgeTotal;

    /** 商品评论数 */
    private long commentTotal;

    // —————————————— 待办 ——————————————

    /** 待审核内容合计（商品 + 知识 + 评论） */
    private long pendingAuditTotal;

    /** 待审批融资申请数（状态为「已推送银行」） */
    private long pendingFinanceTotal;

    // —————————————— 分布 ——————————————

    /** 用户角色分布：名称取 t_role.role_name */
    private List<CountItemVO> roleUserDistribution;

    /** 融资申请状态分布：名称取 t_finance_apply.apply_status（含数量为 0 的状态） */
    private List<CountItemVO> financeStatusDistribution;

    /** 内容审核状态分布：待审核 / 已通过 / 未通过（商品 + 知识 + 评论合计） */
    private List<CountItemVO> auditStatusDistribution;

    // —————————————— 趋势（近 7 日含今天） ——————————————

    /** 近 7 日新增用户 */
    private List<DailyCountVO> newUserTrend;

    /** 近 7 日新增订单 */
    private List<DailyCountVO> newOrderTrend;

    /** 近 7 日新增融资申请 */
    private List<DailyCountVO> newFinanceTrend;

    /** 统计生成时间 */
    private LocalDateTime generatedAt;
}
