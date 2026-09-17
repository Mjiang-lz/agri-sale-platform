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
 * 专家信息实体，对应表 t_expert。
 */
@Data
@TableName("t_expert")
public class ExpertInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 专家主键 */
    @TableId(value = "expert_id", type = IdType.AUTO)
    private Long expertId;

    /** 关联用户账号 */
    private Long userId;

    /** 专业领域（种植、养殖、农技、金融等） */
    private String expertDomain;

    /** 专家介绍与服务范围 */
    private String introduction;

    /** 服务工作量统计（问答数、预约数等） */
    private String workloadStat;

    /** 审核状态：待审核、已通过、未通过 */
    private String auditStatus;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
