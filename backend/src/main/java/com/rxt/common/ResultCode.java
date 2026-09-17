package com.rxt.common;

/**
 * 统一响应状态码，与《软件需求规格说明书》5.3 接口约定一致。
 */
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "操作成功"),
    /** 参数错误 */
    BAD_REQUEST(400, "请求参数有误"),
    /** 未登录或登录已失效 */
    UNAUTHORIZED(401, "登录状态已失效，请重新登录"),
    /** 无权限 */
    FORBIDDEN(403, "没有该操作的权限"),
    /** 资源不存在 */
    NOT_FOUND(404, "请求的资源不存在"),
    /** 业务处理失败 */
    BIZ_ERROR(500, "业务处理失败"),
    /** 系统异常 */
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
