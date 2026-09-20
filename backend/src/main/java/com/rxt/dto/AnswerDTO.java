package com.rxt.dto;

import lombok.Data;

@Data
public class AnswerDTO {
    private Long questionId;   // 要回答哪个问题
    private String content;    // 回答内容
}