package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户管理列表行视图对象（管理后台 · 用户管理页）。
 *
 * <p>只暴露治理所需的字段：账号、姓名、角色、状态、注册时间。
 * 与 {@link UserVO} 分开而不是复用：{@code UserVO} 面向「个人中心」，
 * 带农户经营档案，且其手机号是本人可见；用户管理需要的是跨用户的行列表，
 * 两者的字段与可见性规则并不一致。</p>
 *
 * <p>{@code password} 在任何情况下都不出现在本对象中。</p>
 */
@Data
public class AdminUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户主键 */
    private Long userId;

    /** 手机号（登录账号） */
    private String phone;

    /** 真实姓名 */
    private String realName;

    /** 头像地址 */
    private String avatarUrl;

    /** 角色主键，供调整角色时回显 */
    private Long roleId;

    /** 角色名称 */
    private String roleName;

    /** 账号状态：1 正常，0 停用 */
    private Integer status;

    /** 注册时间 */
    private LocalDateTime createTime;
}
