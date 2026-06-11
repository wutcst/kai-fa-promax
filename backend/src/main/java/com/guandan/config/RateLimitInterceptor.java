package com.guandan.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * API限流和防刷拦截器
 *
 * <p>基于令牌桶算法和IP频次控制的双重限流策略。
 * 令牌桶用于平滑突发流量，IP频次控制用于防刷。
 *
 * <h3>令牌桶算法</h3>
 * <ul>
 *   <li>每秒生成 50 个令牌（可配置），最多累积 200 个</li>
 *   <li>每个请求消耗 1 个令牌</li>
 *   <li>令牌不足时返回 429 Too Many Requests</li>
 * </ul>
 *
 * <h3>IP频次控制</h3>
 * <ul>
 *   <li>单个 IP 每秒最多 20 次请求</li>
 *   <li>每分钟最多 200 次请求</li>
 *   <li>超出限制时返回 429</li>
 * </ul>
 *
 * <h3>异常场景</h3>
 * <ul>
 *   <li>令牌桶初始状态：桶满 200 个令牌，刚启动时容忍突发请求</li>
 *   <li>IP 首次访问：新建计数器，正常放行</li>
 *   <li>关闭限流：通过 toggleRateLimit(false) 关闭后所有请求放行</li>
 *   <li>限流日志按 5 秒聚合输出，避免日志洪峰</li>
 * </ul>
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 令牌桶容量 */
    private static final int BUCKET_CAPACITY = 200;

    /** 令牌生成速率（每秒） */
    private static final int TOKENS_PER_SECOND = 50;

    /** IP 每秒阈值 */
    private static final int IP_SECOND_LIMIT = 20;

    /** IP 每分钟阈值 */
    private static final int IP_MINUTE_LIMIT = 200;

    /** 全局令牌桶当前令牌数 */
    private long currentTokens;

    /** 令牌桶上次补充时间戳（毫秒） */
    private long lastRefillTime;

    /** 限流总开关 */
    private volatile boolean rateLimitEnabled = true;

    /** IP -> 每秒请求数记录 */
    private final ConcurrentHashMap<String, IpCounter> ipSecondCounters = new ConcurrentHashMap<>();

    /** IP -> 每分钟请求数记录 */
    private final ConcurrentHashMap<String, IpCounter> ipMinuteCounters = new ConcurrentHashMap<>();

    /** 上次日志输出时间（按 5 秒聚合） */
    private long lastLogTime = 0;

    /** 聚合周期内的拒绝总数 */
    private long rejectedCountInWindow = 0;

    public RateLimitInterceptor() {
        this.currentTokens = BUCKET_CAPACITY;
        this.lastRefillTime = System.currentTimeMillis();
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!rateLimitEnabled) {
            return true;
        }

        String clientIp = getClientIp(request);

        // 1. IP 频次检查
        if (!checkIpRate(clientIp)) {
            log.warn("IP {} 请求频次超限，已拦截", clientIp);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }

        // 2. 令牌桶检查
        if (!consumeToken()) {
            incrementRejected();
            log.warn("令牌桶耗尽，请求被限流，clientIp={}, uri={}", clientIp, request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"服务繁忙，请稍后再试\"}");
            return false;
        }

        return true;
    }

    // ============================================================
    //  令牌桶实现
    // ============================================================

    /**
     * 尝试消耗一个令牌
     * @return true 如果成功消耗
     */
    private synchronized boolean consumeToken() {
        refillBucket();

        if (currentTokens > 0) {
            currentTokens--;
            return true;
        }
        return false;
    }

    /**
     * 补充令牌桶
     */
    private void refillBucket() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;

        if (elapsed <= 0) {
            return;
        }

        // 计算应补充的令牌数
        long tokensToAdd = (elapsed * TOKENS_PER_SECOND) / 1000;

        if (tokensToAdd > 0) {
            currentTokens = Math.min(BUCKET_CAPACITY, currentTokens + tokensToAdd);
            lastRefillTime = now;
        }
    }

    // ============================================================
    //  IP 频次控制
    // ============================================================

    /**
     * 检查 IP 请求频次
     */
    private boolean checkIpRate(String clientIp) {
        long now = System.currentTimeMillis();

        // 每秒检查
        IpCounter secondCounter = ipSecondCounters.computeIfAbsent(clientIp, k -> new IpCounter());
        if (!secondCounter.checkAndIncrement(now, 1000, IP_SECOND_LIMIT)) {
            return false;
        }

        // 每分钟检查
        IpCounter minuteCounter = ipMinuteCounters.computeIfAbsent(clientIp, k -> new IpCounter());
        if (!minuteCounter.checkAndIncrement(now, 60000, IP_MINUTE_LIMIT)) {
            return false;
        }

        return true;
    }

    /**
     * IP 计数器（时间窗口滑动）
     */
    static class IpCounter {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        /**
         * 检查并递增计数
         * @param now 当前时间戳
         * @param windowSize 时间窗口大小（毫秒）
         * @param limit 阈值
         * @return 是否在限制内
         */
        synchronized boolean checkAndIncrement(long now, long windowSize, int limit) {
            if (now - windowStart >= windowSize) {
                // 窗口过期，重置
                windowStart = now;
                count = 0;
            }

            count++;

            if (count > limit) {
                return false;
            }

            return true;
        }

        synchronized int getCount() {
            return count;
        }
    }

    // ============================================================
    //  聚合拒绝计数与日志
    // ============================================================

    private synchronized void incrementRejected() {
        long now = System.currentTimeMillis();
        rejectedCountInWindow++;

        if (now - lastLogTime >= 5000) {
            if (rejectedCountInWindow > 0) {
                log.warn("限流聚合报告：近5秒内共拒绝 {} 个请求", rejectedCountInWindow);
            }
            rejectedCountInWindow = 0;
            lastLogTime = now;
        }
    }

    // ============================================================
    //  工具方法
    // ============================================================

    /**
     * 获取客户端真实 IP（考虑反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    // ============================================================
    //  管理接口
    // ============================================================

    /**
     * 获取令牌桶当前状态
     */
    public synchronized Map<String, Object> getBucketStatus() {
        refillBucket();
        return Map.of(
                "currentTokens", currentTokens,
                "capacity", BUCKET_CAPACITY,
                "tokensPerSecond", TOKENS_PER_SECOND,
                "enabled", rateLimitEnabled
        );
    }

    /**
     * 开启或关闭限流
     */
    public void toggleRateLimit(boolean enabled) {
        this.rateLimitEnabled = enabled;
        log.info("限流功能已{}", enabled ? "开启" : "关闭");
    }

    /**
     * 重置所有计数器
     */
    public void resetAllCounters() {
        ipSecondCounters.clear();
        ipMinuteCounters.clear();
        synchronized (this) {
            currentTokens = BUCKET_CAPACITY;
            lastRefillTime = System.currentTimeMillis();
            rejectedCountInWindow = 0;
            lastLogTime = 0;
        }
        log.info("所有限流计数器已重置");
    }

    /**
     * 获取各IP的请求统计概览
     */
    public Map<String, Object> getIpStats() {
        int secondCount = ipSecondCounters.size();
        int minuteCount = ipMinuteCounters.size();
        return Map.of(
                "activeSecondIpCount", secondCount,
                "activeMinuteIpCount", minuteCount,
                "enabled", rateLimitEnabled
        );
    }

    /**
     * 清理过期的 IP 计数器（定时任务调用）
     */
    public void cleanupExpiredCounters() {
        long now = System.currentTimeMillis();
        long secondThreshold = now - 2000; // 2秒未更新视为过期
        long minuteThreshold = now - 65000; // 65秒未更新视为过期

        ipSecondCounters.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return now - entry.getValue().windowStart >= 2000;
            }
        });

        ipMinuteCounters.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return now - entry.getValue().windowStart >= 65000;
            }
        });

        if (ipSecondCounters.size() > 0 || ipMinuteCounters.size() > 0) {
            log.debug("清理过期计数器: secondCount={}, minuteCount={}",
                    ipSecondCounters.size(), ipMinuteCounters.size());
        }
    }
}
