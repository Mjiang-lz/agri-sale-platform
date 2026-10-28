package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 专家处理预约入参。
 *
 * 专家身份从登录态获取，不接受前端传 expertId。
 */
@Data
public class AppointmentHandleDTO {

    @NotBlank(message = "预约处理状态不能为空")
    @Pattern(regexp = "已接受|已拒绝", message = "预约状态只能是已接受或已拒绝")
    private String apptStatus;

    @Size(max = 200, message = "备注不能超过 200 个字")
    private String remark;
}
