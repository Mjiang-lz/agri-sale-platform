package com.rxt.service;

import com.rxt.vo.DashboardStatisticsVO;

/**
 * 数据看板业务接口（M6 平台治理 · 管理后台仪表盘）。
 *
 * <p>实现类位于 com.rxt.service.impl。指标全部来自业务表实时统计，
 * 角色校验在实现类内完成，非管理员一律 403。</p>
 */
public interface AdminDashboardService {

    /**
     * 汇总平台运行指标：总量、待办、分布与近 7 日趋势。
     */
    DashboardStatisticsVO statistics();
}
