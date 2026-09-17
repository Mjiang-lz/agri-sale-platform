package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 贷款产品实体，对应表 t_loan_product。
 */
@Data
@TableName("t_loan_product")
public class LoanProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 贷款产品主键 */
    @TableId(value = "loan_product_id", type = IdType.AUTO)
    private Long loanProductId;

    /** 产品名称 */
    private String productName;

    /** 最高可贷额度 */
    private BigDecimal maxAmount;

    /** 年利率，如 0.0450 表示 4.5% */
    private BigDecimal annualRate;

    /** 贷款期限（月） */
    private Integer termMonths;

    /** 起贷所需联合人数下限 */
    private Integer minGroupSize;

    /** 产品说明与申请条件 */
    private String description;

    /** 状态：1 在售，0 下架 */
    private Integer status;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
