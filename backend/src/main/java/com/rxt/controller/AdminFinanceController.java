package com.rxt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rxt.common.Result;
import com.rxt.service.AdminFinanceService;
import com.rxt.vo.FinanceApplyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 融资台账接口（管理后台 · 融资管理页）。基础路径 /api/admin/finance。
 *
 * <p>管理员视角的只读台账：看得见全部融资申请与银行侧审批结果，但不出结论。
 * 审批动作在 {@link LoanAdminController}（/api/loan-admin），只对银行用户开放。</p>
 */
@RestController
@RequestMapping("/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final AdminFinanceService adminFinanceService;

    /**
     * 融资申请分页台账。
     */
    @GetMapping("/applies")
    public Result<IPage<FinanceApplyVO>> pageApplies(@RequestParam(required = false) String applyStatus,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminFinanceService.pageApplies(applyStatus, keyword, pageNum, pageSize));
    }
}
