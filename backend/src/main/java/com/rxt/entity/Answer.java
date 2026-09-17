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
 * 回答实体，对应表 t_answer。
 */
@Data
@TableName("t_answer")
public class Answer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回答主键 */
    @TableId(value = "answer_id", type = IdType.AUTO)
    private Long answerId;

    /** 关联提问 */
    private Long questionId;

    /** 回复专家 */
    private Long expertId;

    /** 回复内容 */
    private String content;

    /** 回复时间 */
    private LocalDateTime answerTime;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
