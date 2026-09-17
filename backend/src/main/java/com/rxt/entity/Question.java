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
 * 提问实体，对应表 t_question。
 */
@Data
@TableName("t_question")
public class Question implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提问主键 */
    @TableId(value = "question_id", type = IdType.AUTO)
    private Long questionId;

    /** 提问农户 */
    private Long askerId;

    /** 指定专家；为空表示公开提问，任何专家可认领 */
    private Long expertId;

    /** 提问内容 */
    private String content;

    /** 状态：待回复、已回复、已关闭 */
    private String status;

    /** 提问时间 */
    private LocalDateTime askTime;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
