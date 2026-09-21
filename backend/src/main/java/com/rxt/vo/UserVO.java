package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人中心视图对象。
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String phone;
    private String realName;
    private String avatarUrl;
    private String roleName;
    private Integer status;

    /** 角色主键 */
    private Long roleId;

    /**
     * 菜单权限标识集合（英文逗号分隔），取自 t_role.menu_perms。
     * 供前端在刷新页面后重新渲染菜单，避免只能靠登录响应里的那一份数据。
     */
    private String menuPerms;

    /** 农户经营档案；非农户角色为 null */
    private FarmerProfileVO farmerProfile;
}
