package com.rxt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 调整用户角色入参（管理后台 · 用户管理）。
 */
@Data
public class UserRoleUpdateDTO {

    /** 目标角色主键，取值须存在于 t_role */
    @NotNull(message = "请选择目标角色")
    private Long roleId;
}
