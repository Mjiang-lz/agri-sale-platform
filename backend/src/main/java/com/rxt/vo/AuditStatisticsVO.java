package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 审核工作量统计视图（M6 · 管理后台审核页的统计卡片与趋势图）。
 *
 * <p>本对象只承载<b>看板给不了</b>的两个数字：「审核动作发生了多少」。
 * 「内容现在处于什么状态」（待审总数、审核状态分布）仍走管理端看板
 * {@code GET /api/admin/dashboard/statistics} —— 一处口径一处来源，不重复提供。</p>
 *
 * <p>数据源是审核流水表 t_audit（非三张业务表），且严格限定
 * {@code target_type IN (PRODUCT, KNOWLEDGE, COMMENT)}：该表同时记录 USER、EXPERT
 * 主体审核（属 M1），不限定类型会把它们一起算进来。</p>
 *
 * <p>字段形状由前端确定，见 {@code docs/接口约定-M6内容审核.md} 第 8 节。</p>
 */
@Data
public class AuditStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前登录审核员的<b>累计</b>已审条数（不限时间）。
     *
     * <p>用它当主卡而不是「今日已审数」：按天口径一旦当天还没审过就是 0，
     * 演示时看起来像接口坏了；累计值稳定且始终有说服力。</p>
     */
    private long myReviewedTotal;

    /**
     * 近 7 日审核趋势，<b>固定 7 个元素</b>：从「今天往前数 6 天」到今天，按日期升序、含今天。
     *
     * <p>某天没有任何审核记录时补 0（而不是缺项），前端可以放心按固定长度画折线。</p>
     */
    private List<AuditDailyStatVO> last7Days;
}
