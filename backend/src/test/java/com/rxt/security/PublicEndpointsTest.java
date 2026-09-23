package com.rxt.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rxt.config.ApiDocsProperties;
import com.rxt.config.FileStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link PublicEndpoints} 的规则表测试。
 *
 * <p><b>为什么要给它写测试：</b>这张表是唯一决定"哪些请求不需要登录"的地方，
 * 而且 {@link LoginInterceptor} 的实现方式是「不命中白名单就必须有有效 token」——
 * 也就是说<b>白名单多一条，等于某个接口的登录校验被整条摘掉，静态看代码很难发现</b>。
 * 接口文档那几条规则确实是"为联调方便"加的，正因为如此更需要有断言把它钉住：
 * 只能放 GET、关了开关就不放行、不能顺带放松别的路径。</p>
 *
 * <p>纯单元测试，不起 Spring 上下文、不连库 —— {@code PublicEndpoints} 的依赖只是一个
 * 配置对象，构造出来直接问它 problem 即可。用 {@link MockHttpServletRequest} 而不引
 * MockMvc，是为了让这个测试保持毫秒级、谁都能随手跑。</p>
 */
class PublicEndpointsTest {

    private static final String CONTEXT_PATH = "/api";

    private static PublicEndpoints endpoints(boolean apiDocsEnabled, boolean obsEnabled) {
        FileStorageProperties obs = new FileStorageProperties();
        obs.setEnabled(obsEnabled);
        ApiDocsProperties apiDocs = new ApiDocsProperties();
        apiDocs.setEnabled(apiDocsEnabled);
        return new PublicEndpoints(obs, apiDocs);
    }

    /**
     * 造一个带 context-path 的请求。真实链路里 {@code getRequestURI()} 是含 {@code /api} 的，
     * 被测方法负责把这层前缀剥掉，所以这里必须还原成真实形态，否则测的是假场景。
     */
    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, CONTEXT_PATH + path);
        request.setContextPath(CONTEXT_PATH);
        return request;
    }

    private static boolean isPublic(PublicEndpoints endpoints, String method, String path) {
        return endpoints.isPublic(request(method, path));
    }

    @Nested
    @DisplayName("接口文档白名单：开了文档才放行")
    class ApiDocs {

        private final PublicEndpoints on = endpoints(true, false);

        @Test
        @DisplayName("放行 OpenAPI 描述（JSON 与 YAML 是并列路径，都要命中）")
        void allowsApiDocs() {
            assertTrue(isPublic(on, "GET", "/v3/api-docs"));
            assertTrue(isPublic(on, "GET", "/v3/api-docs.yaml"));
            assertTrue(isPublic(on, "GET", "/v3/api-docs/swagger-config"));
        }

        @Test
        @DisplayName("放行 Swagger UI 页面与静态资源")
        void allowsSwaggerUi() {
            assertTrue(isPublic(on, "GET", "/swagger-ui.html"));
            assertTrue(isPublic(on, "GET", "/swagger-ui/index.html"));
            assertTrue(isPublic(on, "GET", "/swagger-ui/swagger-ui.css"));
            assertTrue(isPublic(on, "GET", "/swagger-ui/swagger-ui-bundle.js"));
        }

        @Test
        @DisplayName("★ 文档前缀下只放 GET —— 不能因为要开文档就把这两个前缀的所有方法放开")
        void onlyGet() {
            assertFalse(isPublic(on, "POST", "/v3/api-docs"));
            assertFalse(isPublic(on, "PUT", "/swagger-ui/index.html"));
            assertFalse(isPublic(on, "DELETE", "/swagger-ui.html"));
        }

        @Test
        @DisplayName("开了文档也不放松别的路径（管理员接口、写接口仍须登录）")
        void doesNotLoosenOthers() {
            assertFalse(isPublic(on, "GET", "/admin/users"));
            assertFalse(isPublic(on, "GET", "/audit/pending"));
            assertFalse(isPublic(on, "POST", "/trade/products"));
            assertFalse(isPublic(on, "PUT", "/trade/products/1"));
            assertFalse(isPublic(on, "GET", "/user/profile"));
        }
    }

    @Nested
    @DisplayName("开关关掉时，文档端点立刻回到需要登录")
    class ApiDocsDisabled {

        private final PublicEndpoints off = endpoints(false, false);

        @Test
        @DisplayName("关闭后 /v3/api-docs 与 /swagger-ui/** 都不再免登录")
        void notPublic() {
            assertFalse(isPublic(off, "GET", "/v3/api-docs"));
            assertFalse(isPublic(off, "GET", "/v3/api-docs.yaml"));
            assertFalse(isPublic(off, "GET", "/swagger-ui.html"));
            assertFalse(isPublic(off, "GET", "/swagger-ui/index.html"));
        }
    }

    @Nested
    @DisplayName("原有规则未被这次改动影响（回归）")
    class Regression {

        private final PublicEndpoints base = endpoints(false, false);

        @Test
        @DisplayName("登录注册仍放行，且仍只放 POST")
        void authRules() {
            assertTrue(isPublic(base, "POST", "/auth/login"));
            assertTrue(isPublic(base, "POST", "/auth/register"));
            assertFalse(isPublic(base, "GET", "/auth/login"));
        }

        @Test
        @DisplayName("游客可读的公开数据仍放行")
        void publicReadRules() {
            // HomeController 实际有 /home/products 与 /home/knowledge，这里用真路径而不是编一个，
            // 免得后来的人以为存在一个 /home/banners 接口
            assertTrue(isPublic(base, "GET", "/home/products"));
            assertTrue(isPublic(base, "GET", "/trade/products"));
            assertTrue(isPublic(base, "GET", "/demand/list"));
            // 同前缀的写接口不能被只读规则顺带放过 —— 这正是当初弃用 excludePathPatterns 的原因
            assertFalse(isPublic(base, "POST", "/trade/products"));
            assertFalse(isPublic(base, "PUT", "/trade/products/1/status"));
        }

        @Test
        @DisplayName("框架内部错误转发不限方法")
        void errorRule() {
            assertTrue(isPublic(base, "GET", "/error"));
            assertTrue(isPublic(base, "POST", "/error"));
        }

        @Test
        @DisplayName("本地文件规则仍只随 rxt.obs.enabled=false 生效")
        void localFileRule() {
            assertTrue(isPublic(endpoints(false, false), "GET", "/files/product/2026/a.jpg"));
            assertFalse(isPublic(endpoints(false, true), "GET", "/files/product/2026/a.jpg"));
        }
    }
}
