package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.FinanceApplyDTO;
import com.rxt.security.UserContext;
import com.rxt.service.FinanceApplyService;
import com.rxt.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
public class FinanceApplyController {
    @Autowired
    private FinanceApplyService financeApplyService;

    @PostMapping("/apply")
    public Result<Void> apply(@RequestBody FinanceApplyDTO dto) {
        // ⚠️ 团队红线：用户ID必须从JWT取，严禁前端传！
        // 如果 SecurityUtils 报错，去 security 包里找获取当前用户的工具类
        Long userId = UserContext.currentUserId();
        financeApplyService.submitApply(userId, dto);
        return Result.success();
    }
    @GetMapping("/recommend")
    public Result<List<UserVO>> recommend() {
        Long userId = UserContext.currentUserId();
        return Result.success(financeApplyService.recommendGuarantors(userId));
    }
}