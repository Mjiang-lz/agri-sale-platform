package com.rxt.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品评论视图对象。
 *
 * <p>只暴露展示需要的字段。用户表里的 {@code phone}、{@code password}
 * 属敏感信息，**任何情况下都不带出**；批量回填时也只 select 姓名与头像两列。</p>
 */
@Data
public class CommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评论主键 */
    private Long commentId;

    /** 评论人用户主键 */
    private Long userId;

    /** 评论人姓名（关联 t_user 补齐，用户被删时为 null） */
    private String userName;

    /** 评论人头像地址 */
    private String avatarUrl;

    /** 评论正文 */
    private String content;

    /** 审核状态：待审核、已通过、未通过 */
    private String auditStatus;

    /** 评论时间 */
    private LocalDateTime createTime;
}
