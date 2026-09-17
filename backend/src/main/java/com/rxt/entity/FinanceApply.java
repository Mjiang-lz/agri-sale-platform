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
 * 融资申请实体，对应表 t_finance_apply。
 */
@Data
@TableName("t_finance_apply")
public class FinanceApply implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 融资申请主键 */
    @TableId(value = "apply_id", type = IdType.AUTO)
    private Long applyId;

    /** 申请编号，对外展示与银行侧引用 */
    private String applyNo;

    /** 申请农户 */
    private Long farmerId;

    /** 申请的贷款产品 */
    private Long loanProductId;

    /** 申请额度 */
    private BigDecimal applyAmount;

    /** 申请期限（月） */
    private Integer applyTerm;

    /** 资金用途说明 */
    private String applyPurpose;

    /** 所属联合贷款小组，匹配成功后回填 */
    private Long groupId;

    /** 申请状态 */
    private String applyStatus;

    /** 推送至银行侧的时间 */
    private LocalDateTime pushTime;

    /** 提交申请时间 */
    private LocalDateTime applyTime;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
