package com.rxt.service;

import com.rxt.ai.AiChatClient;
import com.rxt.ai.AiChatMessage;
import com.rxt.ai.AiChatResult;
import com.rxt.ai.AiPrompts;
import com.rxt.ai.AiRateLimiter;
import com.rxt.common.BizException;
import com.rxt.config.AiProperties;
import com.rxt.dto.AiAskDTO;
import com.rxt.vo.AiAnswerVO;
import com.rxt.vo.AiStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 助手业务编排：可用性判断 → 入参收口 → 限流 → 组装消息 → 调用 → 记录用量。
 *
 * <p><b>只产出文本，不落库、不发帖。</b>这是本功能最重要的一条设计决定：
 * 农户拿到的 AI 回答只是「页面上的参考意见」，不会写进 {@code t_question} / {@code t_answer}，
 * 也不会被当成平台或某位专家的公开答复。理由有三 ——</p>
 * <ol>
 *   <li>平台现有的内容审核只覆盖 PRODUCT / KNOWLEDGE / COMMENT，<b>不覆盖问答</b>；
 *       AI 的涉农建议（尤其是用药）出错是有实际后果的，不能绕过人工判断直接对外。</li>
 *   <li>「AI 生成的内容要不要过审核流」在本项目里没有既定口径，而「AI 只产出参考文本」
 *       让这个问题根本不需要被回答。</li>
 *   <li>无需为它新建表、改 {@code schema.sql} / {@code data.sql}，也就不会引入
 *       「成员各自导库导致数据结构不一致」这类既有坑。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    /** 历史里 assistant 单条消息的截断长度。AI 回答比提问长，单独给一个上限 */
    private static final int HISTORY_ASSISTANT_MAX_CHARS = 800;

    private final AiProperties properties;

    private final AiChatClient chatClient;

    private final AiRateLimiter rateLimiter;

    /** 供 {@code GET /ai/status} 使用：前端据此决定入口显不显示 */
    public AiStatusVO status() {
        boolean ready = properties.isReady();
        return new AiStatusVO(ready, ready ? properties.getModel() : null);
    }

    /**
     * 农户提问。
     *
     * <p>不限制角色：采购方或专家想问一句农业问题也一样合理，
     * 而按角色设限正是「专家进预约页却弹无权限」那类误报的来源。
     * 真正需要守住的是成本，那由限流负责，不由角色负责。</p>
     */
    public AiAnswerVO ask(Long userId, AiAskDTO dto) {
        if (!properties.isReady()) {
            throw new BizException("AI 助手未开启，请联系平台管理员");
        }

        String question = dto == null || dto.getContent() == null ? "" : dto.getContent().trim();
        if (question.length() < 2) {
            throw new BizException("请把问题描述得再具体一些");
        }
        if (question.length() > properties.getMaxQuestionChars()) {
            throw new BizException("问题太长了，请压缩到 " + properties.getMaxQuestionChars() + " 字以内");
        }

        rateLimiter.checkAndRecord(userId);

        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.system(AiPrompts.SYSTEM));
        messages.addAll(boundedHistory(dto.getHistory()));
        messages.add(AiChatMessage.user(question));

        AiChatResult result = chatClient.chat(messages);
        log.info("[ai] user={} model={} in={} out={}",
                userId, result.model(), result.inputTokens(), result.outputTokens());
        return new AiAnswerVO(result.content());
    }

    /**
     * 收口前端回传的历史。
     *
     * <p>三重限制缺一不可：角色白名单（防注入 system）、条数上限（防超长上下文）、
     * 单条截断（防单条塞爆）。三者合起来把「额外输入」的规模封在一个可预估的常数上，
     * 成本才不会被调用方左右。</p>
     */
    private List<AiChatMessage> boundedHistory(List<AiAskDTO.HistoryItem> history) {
        int limit = Math.max(0, properties.getMaxHistoryRounds()) * 2;
        if (history == null || history.isEmpty() || limit == 0) {
            return Collections.emptyList();
        }
        List<AiAskDTO.HistoryItem> tail = history.size() > limit
                ? history.subList(history.size() - limit, history.size())
                : history;

        List<AiChatMessage> messages = new ArrayList<>(tail.size());
        for (AiAskDTO.HistoryItem item : tail) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            boolean fromAssistant = "assistant".equals(item.getRole());
            String content = item.getContent().trim();
            int max = fromAssistant ? HISTORY_ASSISTANT_MAX_CHARS : properties.getMaxQuestionChars();
            if (content.length() > max) {
                content = content.substring(0, max);
            }
            messages.add(fromAssistant ? AiChatMessage.assistant(content) : AiChatMessage.user(content));
        }
        return messages;
    }
}
