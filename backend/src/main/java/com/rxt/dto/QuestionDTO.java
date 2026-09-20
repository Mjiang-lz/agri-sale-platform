package com.rxt.dto;

import lombok.Data;

@Data
public class QuestionDTO {
    private String content;    // 提问内容
    private Long expertId;     // 指定专家（可为空，为空表示公开提问）
}