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

    /** 角色主键 */
    private Long roleId;

    /**
     * 菜单权限标识集合（英文逗号分隔），取自 t_role.menu_perms。
     *
     * <p>前端按此渲染侧边栏，实现「不同角色看到的菜单不同」。
     * 仅供展示，不构成安全边界 —— 真正的越权判定始终在后端服务层。</p>
     */
    private String menuPerms;
}
