package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.security.CaptchaService;
import com.rxt.service.AuthService;
import com.rxt.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户与认证接口。基础路径 /api/auth。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    /**
     * 登录：校验手机号、密码与图形验证码，成功返回 JWT 令牌与身份信息。
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    /**
     * 注册：按所选身份创建账号，同样要过图形验证码。
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    /**
     * 取一张图形验证码。
     *
     * <p>返回 {@code { captchaKey, captchaImage }}，其中 captchaImage 是完整的
     * data URL，前端直接塞进 {@code <img src>} 即可，不需要再拼域名 ——
     * 换服务器/IP 时历史登录页不会出现图裂。</p>
     *
     * <p>每个 key 只能用一次（无论对错），所以「点击图片刷新」必须重新请求本接口，
     * 不能在前端反复提交同一个 key。</p>
     */
    @PostMapping("/captcha")
    public Result<CaptchaService.Captcha> captcha() {
        return Result.success(captchaService.create());
    }
}
