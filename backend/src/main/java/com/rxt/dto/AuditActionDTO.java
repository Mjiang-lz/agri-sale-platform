package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审核提交入参（通过 / 驳回）。
 */
@Data
public class AuditActionDTO {

    /** 审核结论：通过、不通过（与 t_audit.audit_result 词表一致） */
    @NotBlank(message = "请选择审核结论")
    private String auditResult;

    /**
     * 审核意见。
     *
     * <p>不用 @NotBlank 约束：审核通过时意见可省略，驳回时才必填，
     * 「驳回必填」这条业务规则放在 Service 里校验，提示语更贴业务。</p>
     */
    private String auditRemark;
}
