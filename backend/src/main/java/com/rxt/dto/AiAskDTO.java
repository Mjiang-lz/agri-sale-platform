package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 向 AI 助手提问的入参。
 *
 * <p><b>{@code history} 是可信度最低的一处输入</b>（完全由前端拼装），
 * 因此服务端对它做三重收口：只认 user / assistant 两种角色、只取末尾若干条、
 * 每条截断到固定长度。没有这三条，调用方就能把本接口当成一个「免费的大模型 API」——
 * 塞一份几万字的提示词进来，让平台替他付费。</p>
 */
@Data
public class AiAskDTO {

    @NotBlank(message = "请填写问题内容")
    @Size(min = 2, max = 500, message = "问题内容需 2~500 字")
    private String content;

    /** 最近几轮对话，按时间正序。可为空表示这是第一轮 */
    private List<HistoryItem> history;

    @Data
    public static class HistoryItem {

        /** 只认 {@code user} 与 {@code assistant}，其余一律当作用户提问处理 */
        private String role;

        private String content;
    }
}
