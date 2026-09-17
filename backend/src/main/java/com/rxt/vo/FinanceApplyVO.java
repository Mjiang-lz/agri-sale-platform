package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 融资申请视图对象，农户侧与银行侧共用。
 */
@Data
public class FinanceApplyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long applyId;
    private String applyNo;
    private BigDecimal applyAmount;
    private Integer applyTerm;
    private String applyStatus;
    private LocalDateTime applyTime;
}
