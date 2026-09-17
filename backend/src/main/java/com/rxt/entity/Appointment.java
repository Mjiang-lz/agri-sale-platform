package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 专家预约实体，对应表 t_appointment。
 */
@Data
@TableName("t_appointment")
public class Appointment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预约主键 */
    @TableId(value = "appt_id", type = IdType.AUTO)
    private Long apptId;

    /** 预约农户 */
    private Long farmerId;

    /** 被预约专家 */
    private Long expertId;

    /** 预约时间 */
    private LocalDateTime apptTime;

    /** 咨询主题 */
    private String apptTopic;

    /** 状态：待处理、已接受、已拒绝、已完成、已取消 */
    private String apptStatus;

    /** 备注（专家处理意见或取消原因） */
    private String remark;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
