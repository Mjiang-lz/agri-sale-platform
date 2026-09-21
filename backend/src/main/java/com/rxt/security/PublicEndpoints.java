package com.rxt.security;

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

            // —— 框架内部。不限方法 ——
            // 404 / 500 时 Spring 会内部转发到这里，若被要求登录，
            // 「接口不存在」会被改写成「未登录」，排查方向直接被带偏。
            rule(null, "/error")
    );

    /** 实际生效的规则表 = 上面那张固定表 + 随存储实现启用的规则 */
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
     */
    public PublicEndpoints(FileStorageProperties fileStorageProperties) {
        List<Rule> merged = new ArrayList<>(BASE_RULES);
        if (!fileStorageProperties.isEnabled()) {
            // 上传产物（商品图片 / 知识配图 / 头像）是公开内容：游客本就能浏览商品列表
            // （GET /trade/products 早已放行），图片却要登录才能看是自相矛盾的。
            // OBS 模式下图片 URL 直接指向对象存储、同样公网可读；
            // 本地兜底保持一致的可见性，两种环境的行为才不会分裂。
            merged.add(rule(HttpMethod.GET.name(), FileStorageService.LOCAL_PATH_PATTERN + "/**"));
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
