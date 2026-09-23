package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 专家新增农业知识入参。
 *
 * 作者由登录态自动解析为 t_expert.expert_id，前端不得传 authorId。
 */
@Data
public class KnowledgeCreateDTO {

    @NotBlank(message = "知识标题不能为空")
    @Size(max = 100, message = "知识标题不能超过 100 个字")
    private String title;

    @NotBlank(message = "知识分类不能为空")
    @Size(max = 30, message = "知识分类不能超过 30 个字")
    private String category;

    @NotBlank(message = "知识正文不能为空")
    private String content;

    @Size(max = 255, message = "配图地址不能超过 255 个字符")
    private String imageUrl;
}
