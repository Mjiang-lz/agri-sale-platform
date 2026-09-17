package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.service.LoanAdminService;
import com.rxt.vo.FinanceApplyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 融资管理（银行侧）接口。基础路径 /api/loan-admin。
 *
 * <p>该模块仅对「银行用户」角色开放，具体审批动作需在 Service 层用 PermissionChecker 校验。</p>
 */
@RestController
@RequestMapping("/loan-admin")
@RequiredArgsConstructor
public class LoanAdminController {

    private final LoanAdminService loanAdminService;

    /**
     * 查询已推送至银行侧的融资申请。
     */
    @GetMapping("/applies")
    public Result<List<FinanceApplyVO>> listPushedApplies(@RequestParam(required = false) String status) {
        return Result.success(loanAdminService.listPushedApplies(status));
    }
}
