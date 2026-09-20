package com.rxt.config;

import com.rxt.security.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录拦截器并声明放行路径。
 *
 * <p>放行原则：只放行「不需要身份就能浏览」的只读接口
 * （登录/注册、首页数据、商品列表、求购需求列表）。
 * 凡是涉及「我的数据」或写操作的接口一律要求登录 ——
 * 由 {@link LoginInterceptor} 从 Authorization 头解析 JWT 并写入 UserContext，
 * 业务层据此判定数据归属，避免客户端自报 userId 造成的越权。</p>
 *
 * <p>新增放行路径前先确认：该接口是否真的允许游客访问？
 * 若只是为了「联调方便」，请勿加进来。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // —— 认证：未登录也必须能访问 ——
                        "/auth/login",
                        "/auth/register",

                        // —— 游客可浏览的公开只读数据 ——
                        "/home/**",              // 首页推荐商品、农业知识
                        "/trade/products",       // 商品货源列表
                        "/demand/list",          // 求购需求列表

                        // —— 框架内部 ——
                        "/error"
                );
    }
}
