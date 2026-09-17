package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录成功返回：令牌与基本身份信息。
 */
@Data
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT 令牌，前端存入 localStorage 并在 Authorization 头回传 */
    private String token;

    /** 用户主键 */
    private Long userId;

    /** 手机号 */
    private String phone;

    /** 真实姓名 */
    private String realName;

    /** 角色名称，前端据此渲染菜单 */
    private String roleName;
}
