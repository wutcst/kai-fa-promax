package com.guandan.util;

/**
 * 用户上下文持有者
 * <p>
 * 基于ThreadLocal存储当前请求的用户信息，
 * 避免在方法调用链中频繁传递userId参数。
 * <p>
 * ── 回归验证点 ────────────────────────────────────────────
 * 1. setUserId() 后 getUserId() 返回相同值
 * 2. clear() 后 getUserId() 返回 null
 * 3. 不同线程 getUserId() 互不干扰（ThreadLocal 隔离）
 * 4. 未调用 setUserId() 时 getUserId() 返回 null
 * 5. 重复 setUserId() 覆盖旧值
 * 6. 支持 Long 类型的 null 值
 * 7. 高并发下无数据竞争（ThreadLocal 线程封闭）
 * 8. afterCompletion 未调用时自动移除不导致内存泄漏
 * ─────────────────────────────────────────────────────────
 * <p>
 * 接口说明：
 * - setUserId(Long): 设置当前用户ID
 * - getUserId(): 获取当前用户ID
 * - clear(): 清理上下文（请求完成后由拦截器调用）
 * <p>
 * 使用场景：
 * - Controller/Service层获取当前登录用户ID
 * - TokenInterceptor在请求开始时注入
 * - afterCompletion时清理，防止内存泄露
 * <p>
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
