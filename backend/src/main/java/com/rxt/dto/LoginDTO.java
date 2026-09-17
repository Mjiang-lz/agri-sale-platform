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
}
