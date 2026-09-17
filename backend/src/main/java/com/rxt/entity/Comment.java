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
 * 评论实体，对应表 t_comment。
 */
@Data
@TableName("t_comment")
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评论主键 */
    @TableId(value = "comment_id", type = IdType.AUTO)
    private Long commentId;

    /** 评论人 */
    private Long userId;

    /** 评论对象类型：PRODUCT、KNOWLEDGE */
    private String targetType;

    /** 评论对象主键 */
    private Long targetId;

    /** 评论内容 */
    private String content;

    /** 审核状态：待审核、已通过、未通过 */
    private String auditStatus;

    /** 评论时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
