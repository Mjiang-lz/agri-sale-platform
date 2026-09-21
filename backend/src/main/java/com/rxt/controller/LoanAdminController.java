package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.LoanApproveDTO;
import com.rxt.security.UserContext;
import com.rxt.service.LoanAdminService;
import com.rxt.vo.FinanceApplyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 融资管理（银行侧）接口。基础路径 /api/loan-admin。
 *
 * <p>该模块仅对「银行用户」角色开放，角色校验在 Service 层由 {@code PermissionChecker} 完成。
 * 平台管理员不在本模块内 —— 管理员看的是只读台账（/api/admin/finance/applies），
 * 不参与融资业务闭环，也就不应具备审批能力。</p>
 *
 * <p>审批人身份一律取自登录态 {@link UserContext}，不接受客户端传入。</p>
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

    /**
     * 提交审批结论（通过 / 不通过）。
     */
    @PostMapping("/applies/{applyId}/approve")
    public Result<Void> approve(@PathVariable Long applyId,
                                @RequestBody @Valid LoanApproveDTO dto) {
        loanAdminService.approve(applyId, UserContext.currentUserId(),
                "通过".equals(dto.getApprovalResult()), dto.getLoanAmount(), dto.getApprovalOpinion());
        return Result.success();
    }
}
