package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.service.AdminDashboardService;
import com.rxt.vo.DashboardStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板接口（管理后台 · 仪表盘）。基础路径 /api/admin/dashboard。
 *
 * <p>仅管理员可访问，角色校验在 Service 层由 {@code PermissionChecker} 完成。</p>
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * 平台运行指标汇总。
     */
    @GetMapping("/statistics")
    public Result<DashboardStatisticsVO> statistics() {
        return Result.success(adminDashboardService.statistics());
    }
}
