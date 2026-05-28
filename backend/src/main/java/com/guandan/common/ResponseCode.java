package com.guandan.common;

public enum ResponseCode {
    SUCCESS(200, "操作成功"),
    ERROR(500, "服务器内部错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_ERROR(400, "参数校验失败"),
    BUSINESS_ERROR(1001, "业务异常");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
