package com.rxt.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 分页参数下界校验：拦下小于 1 的页码与单页条数。
 *
 * <h3>要解决的问题</h3>
 * <p>MyBatis-Plus 把 {@code size < 0} 当作「不分页」处理 —— 既不跑 count，
 * 又把整表数据一次性返回。分页插件的 {@code maxLimit} 只拦上界
 * （{@code pageSize=100000} 会被夹到 200），<b>负数这条支路绕过了它</b>：
 * 实测 {@code ?pageSize=-5} 返回全量 24 条且 {@code total=0}，接口看起来「成功」。
 * 这是典型的「平时不报错、一碰边界就出事」。</p>
 *
 * <h3>为什么放在请求入口，而不是每个 Controller 上加 @Min(1)</h3>
 * <p>项目里有 11 处分页入参（分布在 8 个 Controller）。逐个加注解需要同时给每个类加
 * {@code @Validated}，改动 8 个文件、横跨三个人负责的模块，而且<b>新增接口还会再忘一次</b>
 * —— 放行白名单就是这么从 5 处涨到 7 处的。这里一处收口，覆盖全部模块，
 * 新接口自动受保护。</p>
 *
 * <h3>为什么不在 MyBatis 分页插件里拦</h3>
 * <p>已经试过，不行：MyBatis 的 {@code BaseExecutor} 会把查询链路上抛出的任何异常包成
 * {@code PersistenceException}，自定义的 {@code BizException} 被包在里面，
 * 全局异常处理器认不出来，最终以 500「系统繁忙」返回（实测复现）。</p>
 *
 * <h3>边界</h3>
 * <p>只拦「能解析成整数且小于 1」的值。非数字（如 {@code ?pageSize=abc}）交给 Spring 的类型
 * 绑定去报 {@code MethodArgumentTypeMismatchException}，避免和框架抢同一件事；
 * 参数不存在的请求直接放行。参数名沿用项目现有的四种写法
 * （{@code pageNum/pageSize}、{@code page/size}）。</p>
 *
 * <p>注册在 {@code WebMvcConfig}，位于登录拦截器<b>之后</b> ——
 * 未登录的请求应该先拿到 401，而不是先被告知分页参数有误。</p>
 */
@Component
public class PageParamInterceptor implements HandlerInterceptor {

    /** 单页条数类参数名 */
    private static final String[] SIZE_PARAMS = {"pageSize", "size"};

    /** 页码类参数名 */
    private static final String[] PAGE_PARAMS = {"pageNum", "page"};

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        for (String name : SIZE_PARAMS) {
            requireAtLeastOne(request, name, "每页条数必须大于 0");
        }
        for (String name : PAGE_PARAMS) {
            requireAtLeastOne(request, name, "页码必须大于 0");
        }
        return true;
    }

    private void requireAtLeastOne(HttpServletRequest request, String name, String message) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return;
        }
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException notANumber) {
            // 交给 Spring 的类型绑定去报错，这里不抢
            return;
        }
        if (value < 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), message + "，当前为 " + value);
        }
    }
}
