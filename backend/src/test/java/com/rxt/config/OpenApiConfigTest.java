package com.rxt.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OpenApiConfig} 的装配结果测试。
 *
 * <p><b>为什么值得单独测这两个断言：</b>本类只做「声明安全方案」是不够的 ——
 * Swagger UI / swagger-client 决定"要不要把 token 附到请求上"的规则是
 * <b>先看 operation 级 {@code security}，再回落到文档顶层 {@code security}；
 * 两处都没有，就视为该接口无需鉴权</b>，此时即使页面上的 Authorize 已填了值也不会附上
 * {@code Authorization} 头。所以「只声明方案、不声明要求」的后果不是"少画一个锁图标"，
 * 而是 <b>Authorize 整体失效</b>：所有需要登录的接口在页面里试都返回 {@code code=401}。</p>
 *
 * <p>这个坑真实发生过（提交 f0eb3a2 引入，李东阳的缺陷报告指出），本测试就是为了让它不再发生。
 * 纯单元测试：直接 new 出配置对象调工厂方法，不起 Spring。</p>
 */
class OpenApiConfigTest {

    private static OpenAPI openApi() {
        ApiDocsProperties properties = new ApiDocsProperties();
        properties.setTitle("测试用标题");
        properties.setVersion("1.0.0");
        return new OpenApiConfig(properties).rxtOpenApi();
    }

    @Test
    @DisplayName("声明了 bearerAuth 安全方案（Authorize 弹框里能看到它）")
    void declaresBearerScheme() {
        OpenAPI api = openApi();
        assertNotNull(api.getComponents(), "components 不能为空");
        assertNotNull(api.getComponents().getSecuritySchemes(), "必须声明安全方案");
        assertTrue(api.getComponents().getSecuritySchemes().containsKey("bearerAuth"),
                "安全方案名必须叫 bearerAuth，页面 Authorize 与文档说明都按这个名字写");
    }

    @Test
    @DisplayName("★ 必须有文档级 security 要求 —— 缺了它 Swagger 的 Authorize 就是摆设")
    void requiresBearerGlobally() {
        OpenAPI api = openApi();
        assertNotNull(api.getSecurity(),
                "缺少文档级 security 时，swagger-client 不会把 token 附到任何请求上，"
                        + "Authorize 填了也白填（需要登录的接口一律 401）");
        assertFalse(api.getSecurity().isEmpty(), "security 列表不能是空的");
        assertTrue(api.getSecurity().stream().anyMatch(requirement -> requirement.containsKey("bearerAuth")),
                "文档级 security 必须引用 bearerAuth 方案");
    }

    @Test
    @DisplayName("标题与版本来自配置，不是写死的")
    void titleAndVersionComeFromProperties() {
        OpenAPI api = openApi();
        assertTrue("测试用标题".equals(api.getInfo().getTitle()));
        assertTrue("1.0.0".equals(api.getInfo().getVersion()));
    }
}
