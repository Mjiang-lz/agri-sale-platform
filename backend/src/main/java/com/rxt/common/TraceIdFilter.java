package com.rxt.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路标识过滤器：为每个请求分配一个 traceId，写入 MDC 与响应头。
 *
 * <p>这是 M0「统一日志规范 —— 日志含链路标识」验收项的落点。在此之前全项目
 * 搜不到任何 {@code MDC} / {@code traceId}，出了问题只能靠时间戳在日志里猜哪几行属于同一次请求。</p>
 *
 * <p>三件事：</p>
 * <ol>
 *   <li>请求进来时确定 traceId：客户端带了合法的 {@code X-Trace-Id} 就沿用（便于跨系统串联），
 *       否则新生成一个 32 位十六进制串；</li>
 *   <li>放进 MDC，配合 application.yml 里 logging pattern 的 {@code [%X{traceId}]}
 *       让该请求产生的每一行日志都带上它；</li>
 *   <li>回写同名的响应头，前端报错时可以把 traceId 直接甩给后端定位。</li>
 * </ol>
 *
 * <p>必须 {@link Ordered#HIGHEST_PRECEDENCE}：要早于其它过滤器与拦截器，
 * 才能保证它们打出的日志也带 traceId。</p>
 *
 * <p>必须在 finally 里 {@link MDC#remove(String)}：Tomcat 复用线程，
 * 不清理会把上一次请求的 traceId 留在下一次的日志里 —— 那比没有链路标识更误导人。</p>
 *
 * <p><b>已知边界：</b>若将来引入异步接口（返回 {@code Callable} / {@code DeferredResult}），
 * 异步阶段跑在另一个线程上，MDC 不会跟着过去，那一段日志仍然是空的。
 * 届时需要在异步任务入口手动把 traceId 塞进 MDC（或改用
 * {@code TaskDecorator} 包装线程池）。当前项目全部是同步接口，不涉及。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 放进 MDC 的键名，与 application.yml 中 logging.pattern 的 %X{traceId} 对应 */
    public static final String MDC_KEY = "traceId";

    /** 对外响应头名 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 允许沿用的入站 traceId 形态：8~64 位的字母数字、下划线、连字符。
     *
     * <p>必须校验：这个值会被写进日志，放任任意字符串等于开了一个
     * 「往日志里插换行、伪造日志行」的口子。</p>
     */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(MDC_KEY, traceId);
        // 必须在 doFilter 之前写：响应一旦提交就无法再改响应头
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 沿用客户端传入的合法 traceId，否则新生成 */
    private String resolveTraceId(HttpServletRequest request) {
        String inbound = request.getHeader(TRACE_ID_HEADER);
        if (inbound != null && SAFE_TRACE_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
