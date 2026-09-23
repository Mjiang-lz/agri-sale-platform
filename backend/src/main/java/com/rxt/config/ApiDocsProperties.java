package com.rxt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 接口文档（OpenAPI / Swagger UI）配置，绑定 {@code application.yml} 的 {@code rxt.api-docs} 段。
 *
 * <p><b>{@link #enabled} 是接口文档的开关，默认关闭。</b>把它默认成 false 而不是 true，
 * 是因为开与不开的差别不只是"多一个页面"：</p>
 * <ul>
 *   <li>springdoc 会注册 {@code GET /v3/api-docs} 与 {@code GET /swagger-ui/**} 两组端点；</li>
 *   <li>这两个端点必须进 {@link com.rxt.security.PublicEndpoints} 的免登录白名单，
 *       <b>否则 Swagger UI 页面本身要登录、点进去却因为拿不到页面资源而白屏</b>；</li>
 *   <li>而「免登录接口 + 可交互调试台」在公网等价于把全部接口清单连同调用入口一起交出去 ——
 *       白名单是攻击面，不是便利开关（见 {@code PublicEndpoints} 的类注释）。</li>
 * </ul>
 *
 * <p>所以这里跟 {@code rxt.obs.enabled} 用同一套处理方式：<b>代码里默认关，
 * 由 {@code application-dev.yml} 打开</b>。开发/答辩环境照常可用，
 * 换到其它 profile（如 {@code --spring.profiles.active=prod}）时自动不暴露，
 * 不需要任何人记得手动去关。</p>
 *
 * <p>开关是**单一开关**：{@code application.yml} 里 {@code springdoc.api-docs.enabled} 与
 * {@code springdoc.swagger-ui.enabled} 都引用本字段。这样"白名单放行了、端点却不存在"
 * 或"端点存在、白名单没放行"这两种错位都不可能发生 ——
 * 与 {@code rxt.obs.enabled} 同时控制资源映射和白名单是同一个思路。</p>
 *
 * @see OpenApiConfig 文档内容的装配
 * @see com.rxt.security.PublicEndpoints 白名单里对应的放行规则
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rxt.api-docs")
public class ApiDocsProperties {

    /** 是否启用 OpenAPI 文档与 Swagger UI 页面。默认关，dev profile 打开 */
    private boolean enabled = false;

    /** 文档标题，显示在 Swagger UI 顶部 */
    private String title = "融销通（rxt）平台 · 后端接口";

    /** 文档版本，仅用于展示 */
    private String version = "1.0.0";
}
