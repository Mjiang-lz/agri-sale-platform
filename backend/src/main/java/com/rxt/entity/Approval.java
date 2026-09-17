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
 * 审批记录实体，对应表 t_approval。
 */
@Data
@TableName("t_approval")
public class Approval implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审批记录主键 */
    @TableId(value = "approval_id", type = IdType.AUTO)
    private Long approvalId;

    /** 被审批的融资申请 */
    private Long applyId;

    /** 审批人（银行用户） */
    private Long bankUserId;

    /** 审批结果：通过、不通过 */
    private String approvalResult;

    /** 核准放款金额，审批通过时必填 */
    private BigDecimal loanAmount;

    /** 审批意见；不通过时须填写原因 */
    private String approvalOpinion;

    /** 审批时间 */
    private LocalDateTime approvalTime;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
