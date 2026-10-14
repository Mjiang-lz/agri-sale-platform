package com.rxt.ai;

/**
 * 一次模型调用的结果。
 *
 * <p>{@code inputTokens} / {@code outputTokens} 只用于打日志估账 ——
 * 这是唯一能把「这个月的钱花在哪了」说清楚的东西，别省。</p>
 */
public record AiChatResult(String content, String model, int inputTokens, int outputTokens) {
}
