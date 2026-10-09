package com.rxt.security;

import com.rxt.config.ApiDocsProperties;
import com.rxt.config.FileStorageProperties;
import com.rxt.storage.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 免登录白名单：按「HTTP 方法 + 路径」双条件匹配。
 *
 * <p><b>为什么不用 {@code excludePathPatterns}：</b>
 * {@link org.springframework.web.servlet.config.annotation.InterceptorRegistry}
 * 的 {@code excludePathPatterns} <b>只能按路径匹配，完全没有方法的概念</b>。
 * 一旦把 {@code /trade/products} 写进去，同路径下的
 * {@code POST /trade/products}（发布商品）、{@code PUT /trade/products/{id}}（编辑商品）
 * 会被<b>一起放行</b>，拦截器根本不执行 —— 写接口的登录校验被静默摘掉，
 * 只能靠业务层再补一道角色校验兜底，多一层和少一层全靠人记。</p>
 *
 * <p>本类把白名单从「配置」提升为「可读的规则表」：一条规则 =
 * 一个 HTTP 方法 + 一个路径模式。想放行 {@code GET /trade/products/{id}}
 * 就只写 GET，写接口天然不受影响。</p>
 *
 * <p><b>匹配用 {@link PathPatternParser} 而不是 {@code AntPathMatcher}。</b>
 * 后者更宽松（例如把尾斜杠 {@code /trade/products/} 也判为命中），
 * 于是会出现「白名单放行了、DispatcherServlet 其实匹配不上」的错位 ——
 * 放宽的那部分没有任何收益，只是把攻击面往外多画了一格。
 * 用同一套 PathPattern 语义，白名单放行的请求集合正好是「真能被公开接口接住的请求」。</p>
 *
 * <p><b>新增规则前先自问：这个接口真的允许游客访问吗？</b>
 * 若只是为了「联调方便」，请勿加进来 —— 白名单是攻击面，不是便利开关。</p>
 *
 * <p><b>接口文档（Swagger）是这条原则的唯一例外，而且是被明确收口的例外：</b>
 * 放行 {@code /v3/api-docs**} 与 {@code /swagger-ui**} 确实只为「联调方便」，
 * 所以做了三件事把它约束住 ——
 * ① 跟着 {@code rxt.api-docs.enabled} 开关（代码默认关，只有 dev profile 打开）；
 * ② 只放行 GET，文档前缀下不存在任何写接口；
 * ③ 与 springdoc 自身的启停共用同一个开关值，关了文档就没有这两个端点，
 * 白名单里也不会留下"放行了却没内容"的规则。
 * 换句话说：它不是"放宽了原则"，而是"把这份便利限定在开发环境、可一键关掉"。</p>
 *
 * @see LoginInterceptor 规则的唯一消费方
 */
@Component
public class PublicEndpoints {

    /** 与 Spring MVC 默认路由策略同源，保证「白名单语义 == 路由语义」 */
    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    /**
     * 一条放行规则。
     *
     * @param method  HTTP 方法名，<b>null 表示不限方法</b>（仅用于框架内部路径）
     * @param pattern 路径模式，相对于 context-path（即不含开头的 {@code /api}）
     */
    private record Rule(String method, PathPattern pattern) {

        boolean matches(String actualMethod, PathContainer path) {
            if (method != null && !method.equalsIgnoreCase(actualMethod)) {
                return false;
            }
            return pattern.matches(path);
        }
    }

    /** 构造规则时把字符串编译成 PathPattern，避免每次请求重复解析 */
    private static Rule rule(String method, String pattern) {
        return new Rule(method, PARSER.parse(pattern));
    }

    /**
     * 与存储实现无关的固定规则。改这里就是改攻击面，改动请连同 README 的接口约定一起更新。
     */
    private static final List<Rule> BASE_RULES = List.of(
            // —— 认证：未登录也必须能访问，否则无法登录 ——
            // 只放行 POST。登录/注册都是写操作，没有 GET 的必要。
            rule(HttpMethod.POST.name(), "/auth/login"),
            rule(HttpMethod.POST.name(), "/auth/register"),

            // —— 游客可浏览的公开只读数据。全部限定 GET ——
            rule(HttpMethod.GET.name(), "/home/**"),          // 首页推荐商品、农业知识
            rule(HttpMethod.GET.name(), "/trade/products"),   // 商品货源列表
            rule(HttpMethod.GET.name(), "/demand/list"),      // 求购需求列表

            // 商品详情：游客点开货源卡片就能看到价格、产地与发布农户，无需先注册。
            // 这是 LX-4 ③ 的裁决结果（此前只放行了列表、详情仍要求登录，
            // 于是游客能翻列表却点不进任何一件商品，行为是自相矛盾的）。
            //
            // 路径写成 {productId} 而<b>不是</b>通配 /trade/products/*，两个原因：
            //   ① 精确到「一段路径变量」，天然不会吃掉 /trade/products/1/status
            //      这类多段写接口 —— 那条是农户改上下架用的，必须仍然要求登录；
            //   ② 与 TradeController 的 @GetMapping("/products/{productId}") 同形，
            //      白名单放行的集合正好是「真能被该接口接住的请求」。
            // 仍然只放 GET：编辑（PUT）与上下架（PUT .../status）都是写操作。
            rule(HttpMethod.GET.name(), "/trade/products/{productId}"),

            // —— 框架内部。不限方法 ——
            // 404 / 500 时 Spring 会内部转发到这里，若被要求登录，
            // 「接口不存在」会被改写成「未登录」，排查方向直接被带偏。
            rule(null, "/error")
    );

    /**
     * 实际生效的规则表 = 上面那张固定表 + 随存储实现启用的规则 + 随接口文档开关启用的规则
     */
    private final List<Rule> rules;

    /**
     * 构造时组装规则表。
     *
     * <p><b>本地文件访问规则为什么要跟着 {@code rxt.obs.enabled} 一起开关：</b>
     * 只有 {@code enabled=false}（本地兜底模式）时，后端才真正提供 {@code /files/**} 这个
     * 路径 —— 对应的资源映射见 {@link com.rxt.config.WebResourceConfig}。
     * 若不管开关一律放行，OBS 模式下白名单里就会常驻一条「永远没有内容、但攻击面照算」的规则。
     * 白名单是攻击面，不是便利开关，宁可少一条。两边读同一个配置值，
     * 也就不会出现「放行了却没内容」或「有内容却访问不了」的错位。</p>
     *
     * <p><b>只放行 GET：</b>上传走 {@code POST /common/upload}，是写操作，仍然要求登录。</p>
     *
     * <p><b>接口文档（Swagger UI）的放行同理，跟着 {@code rxt.api-docs.enabled} 开关：</b>
     * 文档页要能打开，页面本身和它拉取的 OpenAPI 描述就必须免登录 ——
     * 否则打开页面只拿到一个 401 的 JSON，表现为白屏。而这个"能打开"是有代价的：
     * 一个可交互的接口清单 + 调用台，等于把全部接口连同试调入口一起交出去，
     * 所以只在开了文档时才放行，并且**同样只放 GET**
     * （{@code /v3/api-docs} 是读描述，没有任何写操作需要走这两个前缀）。</p>
     */
    public PublicEndpoints(FileStorageProperties fileStorageProperties, ApiDocsProperties apiDocsProperties) {
        List<Rule> merged = new ArrayList<>(BASE_RULES);
        if (!fileStorageProperties.isEnabled()) {
            // 上传产物（商品图片 / 知识配图 / 头像）是公开内容：游客本就能浏览商品列表
            // （GET /trade/products 早已放行），图片却要登录才能看是自相矛盾的。
            // OBS 模式下图片 URL 直接指向对象存储、同样公网可读；
            // 本地兜底保持一致的可见性，两种环境的行为才不会分裂。
            merged.add(rule(HttpMethod.GET.name(), FileStorageService.LOCAL_PATH_PATTERN + "/**"));
        }
        if (apiDocsProperties.isEnabled()) {
            // ① OpenAPI 描述本身（Swagger UI 打开后先去拉它）
            //    /v3/api-docs 是 JSON、/v3/api-docs.yaml 是 YAML，两者是并列的路径而不是
            //    「目录与文件」的关系，所以 /v3/api-docs/** 匹配不到 .yaml，必须各写一条。
            merged.add(rule(HttpMethod.GET.name(), "/v3/api-docs"));
            merged.add(rule(HttpMethod.GET.name(), "/v3/api-docs.yaml"));
            merged.add(rule(HttpMethod.GET.name(), "/v3/api-docs/**"));

            // ② Swagger UI 页面与它的静态资源（JS / CSS / swagger-config）
            //    /swagger-ui.html 是 springdoc 注册的入口，会跳到 /swagger-ui/index.html；
            //    资源本体都在 /swagger-ui/** 下，两条都要。
            merged.add(rule(HttpMethod.GET.name(), "/swagger-ui.html"));
            merged.add(rule(HttpMethod.GET.name(), "/swagger-ui/**"));
        }
        this.rules = List.copyOf(merged);
    }

    /**
     * 该请求是否命中白名单。
     *
     * @param request 当前请求
     * @return true 表示免登录（但不代表「忽略身份」，见 {@link LoginInterceptor}）
     */
    public boolean isPublic(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(pathWithinApplication(request));
        String method = request.getMethod();
        for (Rule r : rules) {
            if (r.matches(method, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取相对于 context-path 的路径。
     *
     * <p>不用 {@code UrlPathHelper} 是为了避开它在 Spring 各小版本间的 API 变动 ——
     * 这里只需要「去掉开头的 {@code /api}」，手写反而更稳、更好读。</p>
     */
    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
