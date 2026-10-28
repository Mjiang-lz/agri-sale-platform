package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色视图对象。
 *
 * <p>供管理后台的「角色下拉框」与「用户管理筛选条件」使用，
 * 数据取自 t_role，不在前端硬编码角色列表 ——
 * 硬编码会让新增角色时前端静默漏掉一个选项。</p>
 */
@Data
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色主键 */
    private Long roleId;

    /** 角色名称 */
    private String roleName;

    /** 菜单权限标识集合 */
    private String menuPerms;

    /** 角色职责描述 */
    private String description;
}
