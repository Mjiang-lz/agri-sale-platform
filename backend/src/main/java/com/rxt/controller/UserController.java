package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.security.UserContext;
import com.rxt.service.UserService;
import com.rxt.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
     * 查询当前登录用户的个人资料。
     */
    @GetMapping("/profile")
    public Result<UserVO> getProfile() {
        return Result.success(userService.getProfile(UserContext.currentUserId()));
    }
}
