package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnswerDTO {
    @NotNull(message = "questionId 不能为空")
    private Long questionId;

    @NotBlank(message = "回答内容不能为空")
    @Size(min = 2, max = 500, message = "回答内容需 2~500 字")
    private String content;
}