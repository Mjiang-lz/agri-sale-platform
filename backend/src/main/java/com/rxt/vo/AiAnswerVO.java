package com.rxt.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 助手的回答。
 *
 * <p>只回正文：模型名、用量、上游主机这些都是实现细节，前端用不上，
 * 透出去反而会让「AI 生成」变成一条带着技术参数的输出。</p>
 */
@Getter
@AllArgsConstructor
public class AiAnswerVO {

    /** 回答正文（已 trim） */
    private final String content;
}
