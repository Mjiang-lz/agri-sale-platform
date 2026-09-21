package com.rxt.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，保证任何异常都以统一的 Result 结构返回。
 *
 * <p><b>为什么要把 404 / 400 单独拎出来：</b>兜底的 {@code Exception} 分支会把一切
 * 未识别异常都说成「系统繁忙，请稍后重试」，排查时方向会被带偏 ——
 * 项目里已经出现过「以为是 500，其实路径写错了（404）」的误判。
 * 下面三个分支专门消除这类歧义。</p>
 *
 * <p><b>HTTP 状态码约定：</b>本处理器一律返回 HTTP 200，真正的状态放在 {@code Result.code}。
 * 前端 {@code api/request.js} 正是按 body 里的 {@code code} 判断，
 * 若这里改成真 404/400，axios 会落到错误分支弹出「网络异常」，反而盖掉真实提示。
 * 全项目（含 {@code LoginInterceptor}）统一按此约定，勿单独改动。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    /** 参数校验异常（@RequestBody + @Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** 参数绑定异常（表单参数） */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        String msg = ex.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /**
     * 接口不存在。
     *
     * <p>两种触发方式都要接住：{@link NoResourceFoundException} 是 Spring 6.1+ 找不到
     * handler 时（含静态资源兜底）抛的；{@link NoHandlerFoundException} 是显式开启
     * {@code throw-exception-if-no-handler-found} 后抛的。两者都曾被兜底分支吞成 500。</p>
     *
     * <p>补齐这个分支的现实收益：项目里已有多处控制器把 {@code /api} 前缀写重，
     * 真实路径变成 {@code /api/api/xxx}。有它就能一眼看出是路径问题而不是服务出错。</p>
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Result<Void> handleNotFound(Exception ex) {
        log.warn("接口不存在: {}", ex.getMessage());
        return Result.error(ResultCode.NOT_FOUND.getCode(), "接口不存在，请核对请求路径");
    }

    /**
     * 缺少必填的请求参数。
     *
     * <p>例如 {@code GET /comment/product/list} 不带 {@code productId}。
     * 点名参数名，避免前端拿到一句「系统繁忙」无从下手。</p>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("缺少必填参数: {}", ex.getParameterName());
        return Result.error(ResultCode.BAD_REQUEST.getCode(),
                "缺少必填参数：" + ex.getParameterName());
    }

    /**
     * 请求参数类型不匹配。
     *
     * <p>例如 {@code ?pageNum=abc}。只回参数名，不回执 {@code ex.getMessage()} ——
     * 它会把内部类型与嵌套异常一并带出去。</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {} = {}", ex.getName(), ex.getValue());
        return Result.error(ResultCode.BAD_REQUEST.getCode(),
                "参数「" + ex.getName() + "」格式不正确");
    }

    /**
     * 请求方法不支持，例如对只收 POST 的接口发 GET。
     *
     * <p>同属「调用方用错了接口」这一类，回 405 比回「系统繁忙」有用得多。</p>
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("请求方法不支持: {}", ex.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(),
                "请求方法 " + ex.getMethod() + " 不被该接口支持");
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}
