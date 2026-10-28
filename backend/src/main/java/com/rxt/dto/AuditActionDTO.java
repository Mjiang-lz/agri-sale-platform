package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核提交入参（通过 / 驳回）。
 */
@Data
public class AuditActionDTO {

    /**
     * 审核结论：通过、不通过（与 t_audit.audit_result 词表一致）。
     *
     * <p>长度上限对齐 {@code t_audit.audit_result varchar(20)}。Service 里的词表校验
     * 已能挡住绝大多数非法值，这里仍显式声明上限：一来本项目的红线要求
     * 「DTO 校验上限逐字段对齐 DDL 列宽」，二来前端可据此生成输入限制，
     * 不必去读建表语句。</p>
     */
    @NotBlank(message = "请选择审核结论")
    @Size(max = 20, message = "审核结论不能超过 20 字")
    private String auditResult;

    /**
     * 审核意见。
     *
     * <p>不用 @NotBlank 约束：审核通过时意见可省略，驳回时才必填，
     * 「驳回必填」这条业务规则放在 Service 里校验，提示语更贴业务。</p>
     *
     * <p>长度上限对齐 {@code t_audit.audit_remark varchar(200)}。此前没有任何上限，
     * 传 201 字以上会一路走到「插入审核流水」那一步才撞库报错，最终被兜底分支回成
     * 「500 系统繁忙」——一个调用方少打几个字就能过的问题，被伪装成了服务端故障，
     * 前端也只能显示「请稍后重试」，用户无从下手。
     * {@code @Size} 对 null 放行，因此不影响「意见可省略」的语义。</p>
     */
    @Size(max = 200, message = "审核意见不能超过 200 字")
    private String auditRemark;
}
