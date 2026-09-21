package com.rxt.security;

import com.rxt.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 Authorization 头中的 JWT，通过后写入 UserContext，请求结束清理。
 *
 * <p>放行判断交给 {@link PublicEndpoints}（路径 + 方法双条件），
 * 不再用 {@code excludePathPatterns} —— 后者没有方法概念，会把同路径的写接口一起放过。</p>
 *
 * <p><b>白名单 ≠ 忽略身份。</b>命中白名单的请求仍会「尽力」解析令牌：
 * 带且有效 → 写入 UserContext；没带或无效 → 当游客放行。
 * 这样「游客能看的接口」在已登录用户手里也能拿到自己的身份，
 * 不必为了区分视角再拆一个接口出来。</p>
 *
 * <p>注意：Spring Boot 4 基于 Jakarta EE 11，Servlet 类位于 jakarta.servlet 包。</p>
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    private final PublicEndpoints publicEndpoints;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 预检请求直接放行：CORS 预检不带 Authorization 头，卡住它会让所有跨域写请求失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);

        if (publicEndpoints.isPublic(request)) {
            // 尽力而为地识别身份，识别不出也不算错
            if (token != null) {
                UserContext.LoginUser user = jwtUtils.parseToken(token);
                if (user != null) {
                    UserContext.set(user);
                }
            }
            return true;
        }

        if (token == null) {
            writeUnauthorized(response);
            return false;
        }

        UserContext.LoginUser user = jwtUtils.parseToken(token);
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
        // 白名单请求也可能写入过 UserContext，这里必须无条件清理：
        // UserContext 是 ThreadLocal，而 Tomcat 的工作线程是复用的，
        // 漏清一次就会把上一个用户的身份带给下一个请求。
        UserContext.clear();
    }

    /**
     * 从 Authorization 头里取出裸令牌；头缺失或格式不对返回 null。
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
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
