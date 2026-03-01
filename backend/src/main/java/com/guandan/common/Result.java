package com.guandan.common;

public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResponseCode.SUCCESS.getCode(), "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(ResponseCode.SUCCESS.getCode(), "success", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ResponseCode code) {
        return new Result<>(code.getCode(), code.getMessage(), null);
    }

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
}
