package com.guandan.dto;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * 统一 API 响应结果封装（dto 层副本）
 *
 * 与 com.guandan.common.Result 保持同步，提供相同的能力。
 * 包含完整的 ErrorCode 枚举和国际化消息支持，确保全端异常信息一致。
 *
 * @param <T> 数据类型
 */
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    // ── 无参构造 ──
    public Result() {}

    // ── 全参构造 ──
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ── Getter / Setter ──
    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    // ── 国际化消息缓存 ──
    private static final Map<String, ResourceBundle> I18N_BUNDLES = new HashMap<>();

    static {
        try {
            I18N_BUNDLES.put("zh_CN", ResourceBundle.getBundle("i18n.messages", Locale.CHINA));
            I18N_BUNDLES.put("en_US", ResourceBundle.getBundle("i18n.messages", Locale.US));
        } catch (Exception e) {
            // 如果没有国际化配置文件，使用默认消息
        }
    }

    /**
     * 错误码枚举
     *
     * 定义全系统统一的错误码，每个错误码对应一个数字标识和一个默认消息模板。
     * 前端和后端使用相同的错误码体系，确保异常信息一致。
     *
     * 编码规则：
     * - 通用错误 1xxx
     * - 用户认证 2xxx
     * - 房间相关 3xxx
     * - 游戏相关 4xxx
     * - WebSocket 5xxx
     * - 数据相关 6xxx
     */
    public enum ErrorCode {
        // ── 通用错误 (1xxx) ──
        SUCCESS(200, "success"),
        BAD_REQUEST(400, "请求参数错误"),
        UNAUTHORIZED(401, "未登录或Token已过期"),
        FORBIDDEN(403, "无权限访问"),
        NOT_FOUND(404, "资源不存在"),
        METHOD_NOT_ALLOWED(405, "请求方法不允许"),
        CONFLICT(409, "资源冲突"),
        TOO_MANY_REQUESTS(429, "请求过于频繁"),
        INTERNAL_ERROR(500, "服务器内部错误"),
        SERVICE_UNAVAILABLE(503, "服务暂不可用"),

        // ── 用户认证 (2xxx) ──
        USER_NOT_FOUND(2001, "账号不存在"),
        USER_ALREADY_EXISTS(2002, "该账号已被注册"),
        USER_ALREADY_ONLINE(2003, "该账号已登录，请先退出"),
        PASSWORD_ERROR(2004, "密码错误"),
        PASSWORD_TOO_SHORT(2005, "密码长度必须在6-10位之间"),
        TOKEN_INVALID(2006, "Token格式无效"),
        TOKEN_EXPIRED(2007, "Token已过期"),
        LOGIN_FAILED(2008, "登录失败，请检查账号和密码"),
        REGISTER_FAILED(2009, "注册失败，请稍后重试"),

        // ── 房间相关 (3xxx) ──
        ROOM_NOT_FOUND(3001, "房间不存在"),
        ROOM_FULL(3002, "房间已满，无法加入"),
        ROOM_ALREADY_STARTED(3003, "游戏已开始，无法加入"),
        ROOM_NOT_WAITING(3004, "房间不在等待状态"),
        PLAYER_NOT_IN_ROOM(3005, "玩家不在该房间中"),
        PLAYER_ALREADY_IN_ROOM(3006, "玩家已在房间中"),
        NOT_ROOM_CREATOR(3007, "只有房主可以开始游戏"),
        NOT_ENOUGH_PLAYERS(3008, "人数不足，至少需要{0}名玩家"),
        PLAYER_NOT_READY(3009, "还有玩家未准备"),
        ROOM_DISSOLVED(3010, "房间已解散"),

        // ── 游戏相关 (4xxx) ──
        GAME_NOT_STARTED(4001, "游戏未开始或已结束"),
        GAME_ALREADY_STARTED(4002, "游戏已开始"),
        NOT_YOUR_TURN(4003, "现在不是你的回合"),
        INVALID_CARD_TYPE(4004, "牌型不合法"),
        CANNOT_BEAT(4005, "无法管住上一手牌"),
        CARD_NOT_IN_HAND(4006, "手牌中不包含指定的卡牌"),
        PLAY_FAILED(4007, "出牌失败，请检查牌型或回合"),
        PASS_FAILED(4008, "过牌处理失败"),
        GAME_ALREADY_ENDED(4009, "游戏已结束"),

        // ── WebSocket (5xxx) ──
        WS_CONNECTION_FAILED(5001, "WebSocket连接失败"),
        WS_SESSION_EXPIRED(5002, "会话已过期"),
        WS_MESSAGE_INVALID(5003, "消息格式无效"),
        WS_RECONNECT_FAILED(5004, "重连失败，请刷新页面重试"),

        // ── 数据相关 (6xxx) ──
        DATA_NOT_FOUND(6001, "数据不存在"),
        DATA_EXPIRED(6002, "数据已过期"),
        OPERATION_FAILED(6003, "操作失败"),
        DUPLICATE_OPERATION(6004, "重复操作，请勿重复提交"),

        // ── 观战相关 (7xxx) ──
        SPECTATOR_FULL(7001, "观战人数已满"),
        SPECTATOR_NOT_ALLOWED(7002, "当前房间不允许观战"),
        SPECTATOR_ALREADY_WATCHING(7003, "已在观战中"),
        SPECTATOR_ROOM_ENDED(7004, "房间游戏已结束"),

        // ── 扩展保留段 (8xxx, 9xxx) ──
        ;

        private final int code;
        private final String defaultMessage;

        ErrorCode(int code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }

        public int getCode() { return code; }
        public String getDefaultMessage() { return defaultMessage; }

        /**
         * 根据 code 查找对应的 ErrorCode 枚举
         */
        public static ErrorCode fromCode(int code) {
            for (ErrorCode ec : values()) {
                if (ec.code == code) {
                    return ec;
                }
            }
            return INTERNAL_ERROR;
        }
    }

    // ── 国际化相关 ──

    /**
     * 获取指定语言的消息文本
     */
    public static String getLocalizedMessage(ErrorCode errorCode, String locale, Object... args) {
        if (errorCode == null) return "";
        String bundleKey = locale != null && locale.contains("en") ? "en_US" : "zh_CN";
        ResourceBundle bundle = I18N_BUNDLES.get(bundleKey);
        if (bundle != null) {
            try {
                String pattern = bundle.getString("error." + errorCode.getCode());
                if (args != null && args.length > 0) {
                    return MessageFormat.format(pattern, args);
                }
                return pattern;
            } catch (Exception e) {
                // 退回默认消息
            }
        }
        if (args != null && args.length > 0) {
            try {
                return MessageFormat.format(errorCode.getDefaultMessage(), args);
            } catch (IllegalArgumentException e) {
                return errorCode.getDefaultMessage();
            }
        }
        return errorCode.getDefaultMessage();
    }

    /**
     * 获取中文消息
     */
    public static String getMessage(ErrorCode errorCode, Object... args) {
        return getLocalizedMessage(errorCode, "zh_CN", args);
    }

    /**
     * 获取英文消息
     */
    public static String getEnglishMessage(ErrorCode errorCode, Object... args) {
        return getLocalizedMessage(errorCode, "en_US", args);
    }

    // ── 工厂方法 ──

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setMessage(ErrorCode.SUCCESS.getDefaultMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.INTERNAL_ERROR.getCode());
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 使用错误码创建错误响应（自动填充默认消息）
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMessage(errorCode.getDefaultMessage());
        return result;
    }

    /**
     * 使用错误码创建错误响应（自定义消息）
     */
    public static <T> Result<T> error(ErrorCode errorCode, String customMessage) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMessage(customMessage != null ? customMessage : errorCode.getDefaultMessage());
        return result;
    }

    /**
     * 使用错误码创建错误响应（带参数填充的消息模板）
     */
    public static <T> Result<T> error(ErrorCode errorCode, Object... args) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMessage(getMessage(errorCode, args));
        return result;
    }

    // ── 便捷方法 ──

    public boolean isSuccess() {
        return code != null && code == ErrorCode.SUCCESS.getCode();
    }

    /**
     * 判断是否为指定错误码
     */
    public boolean isError(ErrorCode errorCode) {
        return code != null && errorCode != null && code.equals(errorCode.getCode());
    }

    @Override
    public String toString() {
        return "Result{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
