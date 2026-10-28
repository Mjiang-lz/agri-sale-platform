package com.rxt.ai;

import com.rxt.common.BizException;
import com.rxt.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * DeepSeek 对话接口的调用封装（OpenAI 兼容格式）。
 *
 * <p><b>为什么零新增依赖：</b>Spring Boot 4 自带 {@code spring-web}，
 * 其中的 {@link RestClient} 就是同步 HTTP 客户端。为一次 POST 引入 OkHttp / WebClient
 * 只会给部署和依赖审计添麻烦。</p>
 *
 * <p><b>为什么密钥只在这里出现：</b>本类是整条链路上唯一能接触到 API Key 的地方。
 * 前端永远与本站的 {@code /api/ai/**} 通信，Key 只存在于服务端进程内存中 ——
 * 也就是说它既不会被打进前端产物，也不会出现在任何响应体里。</p>
 *
 * <p><b>错误一律翻译成同一句通用话术：</b>上游的 401（Key 失效）、402（余额不足）、
 * 429（限速）对农户来说没有任何可操作性，把它们透出去只会暴露运维信息、并把
 * 「找平台管理员充值」这件事变成农户的困扰。真实原因只写日志。</p>
 */
@Slf4j
@Component
public class AiChatClient {

    /** 上游出问题时对用户说的话。刻意不区分原因，见类注释 */
    private static final String USER_FACING_FAILURE = "AI 助手暂时忙不过来，请稍后重试，或直接把问题提交给专家";

    private final AiProperties properties;

    private final RestClient restClient;

    public AiChatClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 发起一次对话补全。
     *
     * @param messages 完整消息列表（system + 历史 + 本轮提问）
     * @return 回答正文与用量
     * @throws BizException 上游异常、超时或返回结构不合法（文案为通用话术）
     */
    public AiChatResult chat(List<AiChatMessage> messages) {
        ChatRequest request = new ChatRequest(
                properties.getModel(),
                messages,
                properties.getTemperature(),
                properties.getMaxOutputTokens(),
                // ★ 必须显式传 disabled：DeepSeek 的思考模式**默认打开**，
                //   不传的话思维链会和最终回答抢同一个输出上限，症状是
                //   「HTTP 200 + completion_tokens 打满 + content 为空串」。
                new ChatRequest.Thinking(properties.isThinking() ? "enabled" : "disabled"),
                false);

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException ex) {
            // ★ 只记状态码与响应体片段。请求体不能打：里面既有用户提问，Authorization 头也在同一条链路上
            log.error("[ai] 上游返回 {}，原因只记日志、不回传用户。响应体前 300 字：{}",
                    ex.getStatusCode().value(), abbreviate(ex.getResponseBodyAsString(), 300));
            throw new BizException(USER_FACING_FAILURE);
        } catch (RestClientException ex) {
            // 建连失败 / 读超时 / DNS 不通。同样是「用户无能为力」的一类，回同一句话
            log.error("[ai] 调用上游失败（超时或网络不通）", ex);
            throw new BizException(USER_FACING_FAILURE);
        }

        String content = firstContent(response);
        if (!StringUtils.hasText(content)) {
            // ★ 把「思考模式吃光预算」这一种单独认出来并说清楚。
            //   它的症状极具误导性：HTTP 200、上游没有任何报错、用量也正常返回，
            //   只有 content 是空的 —— 不点明的话，下一个排查的人会去怀疑网络、
            //   怀疑 key、怀疑参数名，唯独不会想到「思维链和回答共用输出上限」。
            String reasoning = firstReasoning(response);
            if (StringUtils.hasText(reasoning)) {
                log.error("[ai] content 为空但 reasoning_content 非空（{} 字）—— 思维链吃光了输出预算。"
                                + "请确认 rxt.ai.thinking=false，或调大 rxt.ai.max-output-tokens（当前 {}）",
                        reasoning.length(), properties.getMaxOutputTokens());
            } else {
                log.error("[ai] 上游返回结构不合法（choices 为空或 content 为空）：{}",
                        abbreviate(String.valueOf(response), 300));
            }
            throw new BizException(USER_FACING_FAILURE);
        }

        return new AiChatResult(
                content.trim(),
                StringUtils.hasText(response.model()) ? response.model() : properties.getModel(),
                tokenOf(response.usage() == null ? null : response.usage().promptTokens()),
                tokenOf(response.usage() == null ? null : response.usage().completionTokens()));
    }

    private static String firstContent(ChatResponse response) {
        ChatResponse.Choice first = firstChoice(response);
        return first == null || first.message() == null ? null : first.message().content();
    }

    /** 思维链内容，仅在思考模式下返回。只用于诊断，不返回给用户 */
    private static String firstReasoning(ChatResponse response) {
        ChatResponse.Choice first = firstChoice(response);
        return first == null || first.message() == null ? null : first.message().reasoningContent();
    }

    private static ChatResponse.Choice firstChoice(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        return response.choices().get(0);
    }

    private static int tokenOf(Integer value) {
        return value == null ? 0 : value;
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    // ——— 上游的请求 / 响应结构。字段名要跟 DeepSeek 的 OpenAI 兼容接口逐字对齐 ———

    record ChatRequest(String model,
                       List<AiChatMessage> messages,
                       double temperature,
                       @com.fasterxml.jackson.annotation.JsonProperty("max_tokens") Integer maxTokens,
                       Thinking thinking,
                       boolean stream) {

        /**
         * 思考模式开关。★ 字段名与取值都由 DeepSeek 的 OpenAI 兼容协议规定：
         * {@code {"thinking": {"type": "enabled" | "disabled"}}}。
         * 传成别的名字不会报错，只会被静默忽略 —— 然后你就得到一个空的 content。
         */
        record Thinking(String type) {
        }
    }

    record ChatResponse(String model, List<Choice> choices, Usage usage) {

        record Choice(Message message) {
        }

        /** {@code reasoningContent} 只在思考模式下出现，这里收下来仅用于诊断 */
        record Message(String content,
                       @com.fasterxml.jackson.annotation.JsonProperty("reasoning_content") String reasoningContent) {
        }

        record Usage(@com.fasterxml.jackson.annotation.JsonProperty("prompt_tokens") Integer promptTokens,
                     @com.fasterxml.jackson.annotation.JsonProperty("completion_tokens") Integer completionTokens) {
        }
    }
}
