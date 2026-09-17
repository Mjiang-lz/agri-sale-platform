package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 注册入参。
 */
@Data
public class RegisterDTO {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 密码（明文传输由 HTTPS 保证，入库前加盐哈希） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 注册角色：农户 / 采购方 / 专家 / 银行用户 */
    @NotBlank(message = "请选择注册身份")
    private String roleName;
}
