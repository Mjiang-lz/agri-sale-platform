package com.rxt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档内容装配：标题 / 版本 / 鉴权方式。
 *
 * <p><b>只做三件事，刻意不做第四件。</b>本类不给任何 Controller 加
 * {@code @Tag} / {@code @Operation} 注解 —— 那要改 18 个控制器文件，
 * 属于「为了文档好看去动业务代码」，合并冲突面大而收益小。
 * springdoc 会按控制器类名自动分组、按 {@code @RequestMapping} 自动展开路径与方法，
 * 对"联调时能对着页面点一下"这个真实需求已经够用。</p>
 *
 * <p><b>文档里必须说清的两条项目约定</b>（否则拿到页面的前端会按 REST 惯例误判成败）：</p>
 * <ol>
 *   <li><b>本项目不用 HTTP 状态码表达业务结果</b> —— 一律 HTTP 200，真实状态在响应体的
 *       {@code code} 字段里。看 Swagger 页面上"200 OK"就以为成功了，是这里最容易踩的坑。</li>
 *   <li><b>鉴权是自实现的 JWT</b>（{@code LoginInterceptor} 解析 {@code Authorization} 头），
 *       不是 Spring Security。所以这里只声明一个 bearer 安全方案供页面上的
 *       <b>Authorize</b> 按钮用，让调试带写接口时不必手写请求头。</li>
 * </ol>
 *
 * <p><b>为什么不把安全方案设成全局必需（{@code addSecurityItem}）：</b>
 * 设成全局后每个接口（含 {@code POST /auth/login} 这种本来就免登录的）都会被画上锁图标，
 * 想精确就得给免登录接口逐个挂 {@code @SecurityRequirements}，等于又要改一批控制器。
 * 只声明方案不设全局，页面右上角照样有 Authorize 按钮，代价是接口上的小锁图标不显示 ——
 * 用文档里的文字说明来补，比改代码便宜。</p>
 *
 * @see ApiDocsProperties 开关与标题
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rxt.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfig {

    /** 安全方案名，需与页面 Authorize 里显示的保持一致 */
    private static final String BEARER_SCHEME = "bearerAuth";

    private final ApiDocsProperties properties;

    @Bean
    public OpenAPI rxtOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.getTitle())
                        .version(properties.getVersion())
                        .description("""
                                ### 用之前先知道这两条，否则会误判成败

                                **1）成功与否看响应体的 `code`，不要看 HTTP 状态码。**
                                本项目一律返回 HTTP 200，真实状态在 body 里：
                                `{"code":200,"message":"...","data":{...}}`。
                                未登录、无权限、参数错，也都是 HTTP 200，只是 `code` 分别为 401 / 403 / 400。
                                前端 `api/request.js` 就是这么判的 —— 在 Swagger 页面上看到 "200 OK"
                                并不代表业务成功，请展开 Response body 看 `code`。

                                **2）除免登录接口外，都要带 JWT。**
                                先调 `POST /auth/login`（body：`{"phone":"...","password":"..."}`）
                                拿 `data.token`，再点右上角 **Authorize** 把 token 填进去
                                （**只填 token 本身，不要带 `Bearer ` 前缀**，页面会自己加）。
                                仅以下接口免登录：`/auth/login`、`/auth/register`、
                                `GET /home/**`、`GET /trade/products`、`GET /demand/list`。
                                演示账号：管理员 `13800000001`，密码统一 `123456`。

                                **3）分页：请求参数是 `pageNum` / `pageSize`（从 1 开始），
                                取列表要读 `data.records`，总条数读 `data.total`。**
                                响应体是 MyBatis-Plus `IPage` 的序列化结果，分页字段名是
                                `current` / `size` / `total` / `records` ——
                                **响应里没有 `pageNum` / `pageSize`**，别照请求参数的名字去取。
                                （`common/PageResult` 这个类目前没有任何接口在用，别照它写前端。）
                                """))

                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 data.token（不要带 Bearer 前缀）")));
    }
}
