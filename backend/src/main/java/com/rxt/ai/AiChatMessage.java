package com.rxt.ai;

/**
 * 一条对话消息（OpenAI 兼容格式的 {@code {role, content}}）。
 *
 * <p>role 只有三种：{@code system}（角色设定）、{@code user}（提问）、{@code assistant}（上一轮的AI回答）。
 * 刻意不做成枚举：这个值最终要原样写进发给上游的 JSON，多一层枚举只是多一处翻译。</p>
 */
public record AiChatMessage(String role, String content) {

    public static AiChatMessage system(String content) {
        return new AiChatMessage("system", content);
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage("user", content);
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage("assistant", content);
    }
}
