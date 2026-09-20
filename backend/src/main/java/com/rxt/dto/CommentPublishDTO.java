package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表商品评论入参。
 *
 * <p>这里**故意不设 userId 字段**：评论人一律由服务端从登录态
 * （{@code UserContext.currentUserId()}）取。若让客户端传 userId，
 * 任何登录用户都能伪造身份替别人发评论 —— 与购物车、订单同一套收口原则。</p>
 */
@Data
public class CommentPublishDTO {

    /** 被评论的商品主键 */
    @NotNull(message = "请选择要评论的商品")
    private Long productId;

    /** 评论正文 */
    @NotBlank(message = "评论内容不能为空")
    @Size(min = 2, max = 500, message = "评论内容需 2~500 字")
    private String content;
}
