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
}
