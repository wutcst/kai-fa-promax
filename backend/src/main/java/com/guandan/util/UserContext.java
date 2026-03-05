package com.guandan.util;

public class UserContext {
    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> usernameHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> tokenHolder = new ThreadLocal<>();

    public static void setContext(Long userId, String username, String token) {
        userIdHolder.set(userId);
        usernameHolder.set(username);
        tokenHolder.set(token);
    }

    public static Long getUserId() { return userIdHolder.get(); }
    public static String getUsername() { return usernameHolder.get(); }
    public static String getToken() { return tokenHolder.get(); }

    public static void clear() {
        userIdHolder.remove();
        usernameHolder.remove();
        tokenHolder.remove();
    }
}
