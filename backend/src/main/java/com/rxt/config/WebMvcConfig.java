package com.rxt.config;

import com.rxt.common.PageParamInterceptor;
import com.rxt.security.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录拦截器与分页参数拦截器。
 *
 * <p><b>放行规则不在本类里，见 {@link com.rxt.security.PublicEndpoints}。</b>
 * 之前在 {@code excludePathPatterns} 里列白名单，但那个 API <b>只能按路径匹配、
 * 没有 HTTP 方法的概念</b> —— 把 {@code /trade/products} 写进去，
 * 同路径下的 {@code POST /trade/products}（发布商品）与
 * {@code PUT /trade/products/{id}}（编辑商品）会被一起放行。
 * 现在改为「路径 + 方法」双条件，写接口不再被只读规则顺带放过。</p>
 *
 * <p>放行原则不变：只放行「不需要身份就能浏览」的只读接口。
 * 凡是涉及「我的数据」或写操作的接口一律要求登录 ——
 * 由 {@link com.rxt.security.LoginInterceptor} 从 Authorization 头解析 JWT 并写入
 * UserContext，业务层据此判定数据归属，避免客户端自报 userId 造成的越权。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    private final PageParamInterceptor pageParamInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                // 这里只排除「框架内部的错误转发路径」，不再承担业务白名单职责。
                // /error 一旦被要求登录，「接口不存在（404）」就会被改写成
                // 「未登录（401）」，把排查方向带偏 —— 这类误导比漏放行更难查。
                .excludePathPatterns("/error");

        // 分页参数下界校验。注册在登录拦截器之后：未登录的请求应先拿到 401，
        // 而不是先被告知分页参数有误。详见 PageParamInterceptor 的类注释。
        registry.addInterceptor(pageParamInterceptor)
                .addPathPatterns("/**");
    }
}
