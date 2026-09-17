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
 * 农业知识实体，对应表 t_knowledge。
 */
@Data
@TableName("t_knowledge")
public class Knowledge implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 知识条目主键 */
    @TableId(value = "knowledge_id", type = IdType.AUTO)
    private Long knowledgeId;

    /** 标题 */
    private String title;

    /** 分类 */
    private String category;

    /** 正文内容 */
    private String content;

    /** 配图地址（对象存储 URL） */
    private String imageUrl;

    /** 发布专家 */
    private Long authorId;

    /** 审核状态：待审核、已通过、未通过 */
    private String auditStatus;

    /** 浏览量 */
    private Integer viewCount;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
