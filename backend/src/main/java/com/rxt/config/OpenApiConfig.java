package com.rxt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
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
 *       不是 Spring Security。所以这里要同时做两件事：声明一个 bearer 安全方案，
 *       并把文档级 {@code security} 指向它 —— 缺任何一件，页面上的
 *       <b>Authorize</b> 都不会把 token 附到请求上（详见下面那段）。</li>
 * </ol>
 *
 * <p><b>安全方案必须连「安全要求」一起声明，否则 Authorize 是个摆设（已踩过一次）。</b>
 * Swagger UI / swagger-client 决定"要不要把 token 附到请求上"的规则是：
 * <b>先看 operation 级 {@code security}，再回落到文档级 {@code security}；
 * 两处都没有，就视为该操作无需鉴权</b> —— 此时即使页面上的 Authorize 已经填好值，
 * 请求里也不会出现 {@code Authorization} 头。所以「只声明方案、不声明要求」的后果
 * <b>不是"少画一个小锁图标"，而是所有需要登录的接口在页面里试都返回 401</b>。
 * 本类第一版就漏了 {@code addSecurityItem}（提交 f0eb3a2），
 * 由李东阳的缺陷报告指出，{@code OpenApiConfigTest} 现已把这一行钉住。</p>
 *
 * <p><b>已知代价（可接受）：</b>文档级 security 会让 {@code POST /auth/login} 这类免登录接口
 * 也被画上小锁图标 —— 纯观感问题，实测无副作用：
 * {@link com.rxt.security.LoginInterceptor} 对白名单路径是"尽力解析令牌、
 * 识别不出当游客放行"，多带一个 token 不会导致 401。
 * 想把图标也做精确，需要给免登录接口逐个置 {@code security: []}
 * （可用 {@code OperationCustomizer} 从 {@code PublicEndpoints} 推导）——
 * 但那要多一层"由 HandlerMethod 反推真实路径"的逻辑，而一旦推错，
 * 就会在<b>文档上</b>把一个需要鉴权的接口标成免鉴权（比多画一个锁图标危险得多），故暂不做。</p>
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
                                （这几个接口在这个页面上也会显示小锁图标 —— 那是文档级
                                security 的副作用，**可以忽略**；给它们带上 token 也不会出错。）
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
                                .description("登录接口返回的 data.token（不要带 Bearer 前缀）")))

                // ★ 这一行不能省。只声明方案（上面那段）只够让 Authorize 弹框里出现 bearerAuth，
                //   但 swagger-client 要在「operation 级 security」或「文档级 security」里
                //   找到要求，才会把 token 附到请求上；两处都没有就当作无需鉴权。
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
