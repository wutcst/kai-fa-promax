package com.guandan.util;

/**
 * 用户上下文持有者
 *
 * 基于ThreadLocal存储当前请求的用户信息，
 * 避免在方法调用链中频繁传递userId参数。
 *
 * 接口说明：
 * - setUserId(Long): 设置当前用户ID
 * - getUserId(): 获取当前用户ID
 * - clear(): 清理上下文（请求完成后由拦截器调用）
 *
 * 使用场景：
 * - Controller/Service层获取当前登录用户ID
 * - TokenInterceptor在请求开始时注入
 * - afterCompletion时清理，防止内存泄露
 *
 * 注意事项：
 * - 必须确保每个请求完成后调用clear()
 * - 异步任务中不可直接使用（ThreadLocal不跨线程传播）
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
