package com.rxt.security;

import com.rxt.common.Result;
import com.rxt.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 Authorization 头中的 JWT，通过后写入 UserContext，请求结束清理。
 *
 * <p>注意：Spring Boot 4 基于 Jakarta EE 11，Servlet 类位于 jakarta.servlet 包。</p>
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            writeUnauthorized(response);
            return false;
        }

        UserContext.LoginUser user = jwtUtils.parseToken(header.substring(PREFIX.length()));
        if (user == null) {
            writeUnauthorized(response);
            return false;
        }

        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":" + ResultCode.UNAUTHORIZED.getCode()
                        + ",\"message\":\"" + ResultCode.UNAUTHORIZED.getMessage()
                        + "\",\"data\":null}");
    }
}
