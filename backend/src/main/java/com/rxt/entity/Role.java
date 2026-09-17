package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色实体，对应表 t_role。
 */
@Data
@TableName("t_role")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色主键 */
    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;

    /** 角色名称：农户、采购方、专家、银行用户、管理员 */
    private String roleName;

    /** 菜单权限标识集合，供前端动态渲染菜单 */
    private String menuPerms;

    /** 角色职责描述 */
    private String description;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
