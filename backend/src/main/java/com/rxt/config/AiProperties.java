package com.rxt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 大模型助手（DeepSeek）配置，绑定 {@code application.yml} 的 {@code rxt.ai} 段。
 *
 * <p><b>{@link #enabled} 默认 false，与 OBS 开关同一条纪律：</b>
 * 仓库保持「clone 下来就能跑」—— 队友没有 API Key 也能起后端，
 * 问答页只是不显示 AI 入口，不会报错、不会卡在启动阶段。</p>
 *
 * <p><b>密钥不进版本库：</b>{@code api-key} 在 {@code application.yml} 里留空，
 * 真实值只通过环境变量 {@code RXT_AI_API_KEY} 或 {@code application-local.yml}
 * （已在 .gitignore 中）注入。这与 OBS 的 AK/SK、{@code rxt.jwt.secret} 是同一条既有纪律 ——
 * 《概要设计说明书》§8.3 要求「配置文件中的数据库口令、对象存储密钥与 JWT 密钥与代码分离」。
 * 大模型密钥同样是可直接扣费的凭据，一并适用。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rxt.ai")
public class AiProperties {

    /** 是否启用 AI 助手。false 时前端整块隐藏入口，接口也只回「未开启」 */
    private boolean enabled = false;

    /** DeepSeek 的 OpenAI 兼容基础地址，不含 {@code /chat/completions} */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key。留空表示未配置 */
    private String apiKey;

    /**
     * 模型名。
     *
     * <p>★ 注意：旧名 {@code deepseek-chat} / {@code deepseek-reasoner} 已于 2026-07-24 停用，
     * 调用会直接失败。当前可用的是 {@code deepseek-flash}（= V4.1-Flash）与 {@code deepseek-v4-pro}。</p>
     */
    private String model = "deepseek-flash";

    /** 建连超时。上游连不上时宁可快速失败，不要拖着用户等 */
    private int connectTimeoutMs = 3000;

    /** 读超时。问答类响应通常在 3~10 秒，30 秒留足余量 */
    private int readTimeoutMs = 30000;

    /** 单次回答的输出上限。农技建议 500 字足够，卡住它是控成本最有效的一招 */
    private int maxOutputTokens = 800;

    /** 采样温度。农技建议要稳不要花，取低值。★ 思考模式下该参数**不生效**（官方文档明确） */
    private double temperature = 0.3d;

    /**
     * 是否开启思考模式。
     *
     * <p>★ 必须显式关掉，因为 DeepSeek 的**思考模式是默认打开的、且 effort 默认为 high**。
     * 不传这个参数时，模型会先输出一大段思维链，而思维链和最终回答**共用同一个输出上限** ——
     * 于是出现最迷惑的一种失败：HTTP 200、上游一切正常、`completionTokens` 正好等于上限，
     * 但 `content` 是**空字符串**（全部预算被思考吃掉），页面上什么都没有。</p>
     *
     * <p>关掉它的三个理由：农技建议不需要长链推理；思考模式不支持 {@code temperature}；
     * 输出 token 要多花 2~3 倍、延迟从 1~3 秒升到 10~30 秒。</p>
     */
    private boolean thinking = false;

    /** 每个用户每小时调用上限 */
    private int perUserPerHour = 20;

    /** 每个用户每天调用上限 */
    private int perUserPerDay = 200;

    /** 全体用户每天调用上限。单用户限额拦不住「很多人各刷一点」，这条是兜底 */
    private int globalPerDay = 500;

    /** 允许随问题一起回传的历史轮数（1 轮 = 1 问 + 1 答）。超出部分服务端丢弃 */
    private int maxHistoryRounds = 3;

    /** 问题长度上限，与农户侧输入框的 maxlength 对齐 */
    private int maxQuestionChars = 500;

    /**
     * 是否真正可用：开关打开且密钥非空。
     *
     * <p>把「开关」与「有没有 key」合成一个判据，是因为这两种情况对用户是同一件事 ——
     * 都是「这个功能现在用不了」。分开判断只会让「开了开关但忘配 key」变成一类
     * 页面显示入口、点了却报错的中间态。</p>
     */
    public boolean isReady() {
        return enabled && StringUtils.hasText(apiKey);
    }
}
