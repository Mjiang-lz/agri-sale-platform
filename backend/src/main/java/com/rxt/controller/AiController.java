package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.AiAskDTO;
import com.rxt.security.UserContext;
import com.rxt.service.AiAssistantService;
import com.rxt.vo.AiAnswerVO;
import com.rxt.vo.AiStatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手接口。
 *
 * <p><b>为什么用独立的 {@code /ai} 前缀，而不是挂到 {@code /expert/qa/} 下面：</b>
 * 后者在 Controller 层面套着既有的 {@code requireRole} 语义，混进去容易被顺带限制，
 * 或反过来把它的语义搞乱。{@code /ai} 自己把鉴权边界写清楚 —— 需要登录，但不需要特定角色。</p>
 *
 * <p><b>不加入 {@code PublicEndpoints} 白名单：</b>这两个接口都是登录用户才用的，
 * 而白名单是攻击面不是便利开关。「游客也能问 AI」没有任何产品理由，
 * 却会让平台为匿名流量付费。</p>
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;

    /**
     * AI 可用状态。前端进页面时调一次，决定「问 AI 助手」这个入口显不显示。
     */
    @GetMapping("/status")
    public Result<AiStatusVO> status() {
        return Result.success(aiAssistantService.status());
    }

    /**
     * 农户提问，直接返回参考意见。
     */
    @PostMapping("/qa/ask")
    public Result<AiAnswerVO> ask(@RequestBody @Valid AiAskDTO dto) {
        return Result.success(aiAssistantService.ask(UserContext.currentUserId(), dto));
    }
}
