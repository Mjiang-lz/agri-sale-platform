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
 * 联合贷款小组实体，对应表 t_loan_group。
 */
@Data
@TableName("t_loan_group")
public class LoanGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 联合贷款小组主键 */
    @TableId(value = "group_id", type = IdType.AUTO)
    private Long groupId;

    /** 小组名称，可自动生成 */
    private String groupName;

    /** 组长，即发起融资申请的农户 */
    private Long leaderId;

    /** 目标融资金额合计 */
    private BigDecimal targetAmount;

    /** 当前成员数 */
    private Integer memberCount;

    /** 小组状态：组建中、已提交、已受理、已放款、未通过 */
    private String groupStatus;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
