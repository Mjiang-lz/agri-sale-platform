package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.FarmerProfileUpdateDTO;
import com.rxt.dto.UserProfileUpdateDTO;
import com.rxt.security.UserContext;
import com.rxt.service.UserService;
import com.rxt.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人信息接口。基础路径 /api/user。
 *
 * <p>用户主键一律取自登录态（UserContext），不接受前端传入，避免越权访问他人数据。</p>
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 查询当前登录用户的个人资料。农户角色会附带经营档案。
     */
    @GetMapping("/profile")
    public Result<UserVO> getProfile() {
        return Result.success(userService.getProfile(UserContext.currentUserId()));
    }

    /**
     * 更新当前登录用户的基本资料：昵称、头像。
     * 手机号不在可改范围（登录账号，变更需短信验证）。
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody @Valid UserProfileUpdateDTO dto) {
        userService.updateProfile(UserContext.currentUserId(), dto);
        return Result.success();
    }

    /**
     * 维护当前登录用户的农户经营档案（仅农户角色）。
     * 档案首次维护时自动建档。
     */
    @PutMapping("/farmer-profile")
    public Result<Void> updateFarmerProfile(@RequestBody @Valid FarmerProfileUpdateDTO dto) {
        userService.updateFarmerProfile(UserContext.currentUserId(), dto);
        return Result.success();
    }
}
