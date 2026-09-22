package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QuestionDTO {
    @NotBlank(message = "提问内容不能为空")
    @Size(min = 2, max = 500, message = "提问内容需 2~500 字")
    private String content;

    private Long expertId;
}