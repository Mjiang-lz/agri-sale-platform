package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参：手机号 + 密码。
 */
@Data
public class LoginDTO {

    /** 手机号，同时作为登录账号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 图形验证码凭据，由 POST /api/auth/captcha 下发，提交时原样带回。
     * 不校验为空串 —— 空串在 {@code CaptchaService#validate} 里会被判为不通过，
     * 这里再加 @NotBlank 只是让错误信息更明确一点。
     */
    private String captchaKey;

    /** 用户在图里看到的字符（忽略大小写） */
    private String captchaCode;
}
