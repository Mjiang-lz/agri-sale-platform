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
 * 用户实体，对应表 t_user。
 */
@Data
@TableName("t_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户主键 */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /** 手机号，同时作为登录账号 */
    private String phone;

    /** 密码，加盐哈希后存储，不可逆 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 所属角色 */
    private Long roleId;

    /** 用户头像地址（对象存储 URL） */
    private String avatarUrl;

    /** 账号状态：1 正常，0 停用 */
    private Integer status;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
