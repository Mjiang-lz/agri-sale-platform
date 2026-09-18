package com.rxt.config;

import com.rxt.security.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录拦截器并声明放行路径。
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
                        "/auth/login",
                        "/auth/register",
                        "/auth/captcha",
                        "/home/**",
                        "/trade/products",          // ← 新增：商品列表公开
                        "/trade/product/**",        // ← 新增：商品详情公开
                        "/trade/cart",              // ← 临时放开：加购物车（登录鉴权未做，便于联调）
                        "/trade/order",             // ← 临时放开：下单（同上）
                        "/error"
                );
    }
}
