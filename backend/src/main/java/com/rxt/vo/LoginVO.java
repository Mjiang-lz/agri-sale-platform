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

    /**
     * 头像地址（本地模式形如 /api/files/avatar/2026/10/xxx.jpg；从未设置过则为 null）。
     *
     * <p>必须随登录一起下发。此前漏了这个字段，链路是：
     * 个人中心传头像 → 保存成功（库里 avatar_url 已更新）→
     * 但前端 saveLogin 会用登录响应**整体覆盖**本地 profile，
     * 而登录响应里没有头像地址 —— 于是重新登录后顶栏又退回昵称首字，
     * 用户看到的就是「上传了但头像框没变化」。</p>
     */
    private String avatarUrl;
}
