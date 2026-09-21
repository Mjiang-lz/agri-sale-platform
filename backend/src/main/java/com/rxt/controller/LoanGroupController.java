package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.LoanGroupDTO;
import com.rxt.security.UserContext;
import com.rxt.service.LoanGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/finance")
public class LoanGroupController {
    @Autowired
    private LoanGroupService loanGroupService;

    @PostMapping("/group")
    public Result<Void> createGroup(@RequestBody LoanGroupDTO dto) {
        loanGroupService.createGroup(UserContext.currentUserId(), dto);
        return Result.success();
    }

    @PutMapping("/group/{id}/join")
    public Result<Void> joinGroup(@PathVariable Long id) {
        loanGroupService.joinGroup(id, UserContext.currentUserId());
        return Result.success();
    }

    @DeleteMapping("/group/{id}/quit")
    public Result<Void> quitGroup(@PathVariable Long id) {
        loanGroupService.quitGroup(id, UserContext.currentUserId());
        return Result.success();
    }
}