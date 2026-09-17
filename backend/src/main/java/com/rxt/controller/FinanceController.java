package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.FinanceApplyDTO;
import com.rxt.security.UserContext;
import com.rxt.service.FinanceService;
import com.rxt.vo.FinanceApplyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 融资服务接口。基础路径 /api/finance。
 */
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    /**
     * 提交融资申请。
     */
    @PostMapping("/apply")
    public Result<Long> submitApply(@RequestBody @Valid FinanceApplyDTO dto) {
        return Result.success(financeService.submitApply(UserContext.currentUserId(), dto));
    }

    /**
     * 查询本人融资申请列表。
     */
    @GetMapping("/apply/my")
    public Result<List<FinanceApplyVO>> listMyApplies() {
        return Result.success(financeService.listMyApplies(UserContext.currentUserId()));
    }
}
