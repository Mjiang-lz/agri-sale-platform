package com.rxt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启停用账号入参（管理后台 · 用户管理）。
 *
 * <p>只接收状态值，被操作用户由路径变量给出；
 * 操作人身份一律取自登录态，不接受客户端传入。</p>
 */
@Data
public class UserStatusUpdateDTO {

    /** 目标状态：1 正常，0 停用（与 t_user.status 词表一致） */
    @NotNull(message = "请选择账号状态")
    private Integer status;
}
