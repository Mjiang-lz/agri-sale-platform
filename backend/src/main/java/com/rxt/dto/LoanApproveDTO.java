package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 贷款审批结论入参（银行侧 · 融资管理）。
 *
 * <p>「通过时放款金额必填、不通过时审批意见必填」这两条业务规则不放在注解上，
 * 而是放在 Service 里校验 —— 注解表达不了「按另一个字段的取值决定本字段是否必填」，
 * 硬写只会得到一句对用户没用的报错。</p>
 */
@Data
public class LoanApproveDTO {

    /** 审批结果：通过、不通过（与 t_approval.approval_result 词表一致） */
    @NotBlank(message = "请选择审批结论")
    @Pattern(regexp = "^(通过|不通过)$", message = "审批结论只能是「通过」或「不通过」")
    private String approvalResult;

    /** 核准放款金额，审批通过时必填，须大于 0 */
    @Positive(message = "放款金额必须大于 0")
    private BigDecimal loanAmount;

    /** 审批意见；不通过时必填 */
    @Size(max = 200, message = "审批意见不能超过 200 字")
    private String approvalOpinion;
}
