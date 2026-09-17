package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审核记录实体，对应表 t_audit。
 */
@Data
@TableName("t_audit")
public class AuditRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审核记录主键 */
    @TableId(value = "audit_id", type = IdType.AUTO)
    private Long auditId;

    /** 审核对象类型：USER、PRODUCT、KNOWLEDGE、EXPERT、COMMENT */
    private String targetType;

    /** 审核对象主键，与 target_type 联合定位 */
    private Long targetId;

    /** 审核结果：通过、不通过 */
    private String auditResult;

    /** 审核意见 */
    private String auditRemark;

    /** 审核人（管理员） */
    private Long auditorId;

    /** 审核时间 */
    private LocalDateTime auditTime;

}
