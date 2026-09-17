package com.rxt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 融资申请入参。
 */
@Data
public class FinanceApplyDTO {

    /** 申请的贷款产品 */
    @NotNull(message = "请选择贷款产品")
    private Long loanProductId;

    /** 申请额度 */
    @NotNull(message = "请填写申请额度")
    private BigDecimal applyAmount;

    /** 申请期限（月） */
    @NotNull(message = "请填写申请期限")
    private Integer applyTerm;

    /** 资金用途说明 */
    private String applyPurpose;
}
