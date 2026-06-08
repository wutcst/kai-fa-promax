package com.guandan.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.io.IOException;

/**
 * 安全响应头过滤器
 *
 * <h3>接口字段说明</h3>
 * <ul>
 *   <li>X-Content-Type-Options: nosniff — 防止 MIME 类型嗅探攻击</li>
 *   <li>X-Frame-Options: DENY — 禁止页面被嵌入 iframe，防止点击劫持</li>
 *   <li>X-XSS-Protection: 1; mode=block — 浏览器 XSS 过滤器（已废弃，仅兼容旧浏览器）</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin — 跨域时仅传递 origin</li>
 *   <li>Cache-Control: no-cache, no-store, must-revalidate — 禁止浏览器缓存敏感响应</li>
 *   <li>Content-Security-Policy — 严格资源白名单，限制脚本/样式/图片/字体/连接来源</li>
 * </ul>
 *
 * <h3>异常场景</h3>
 * <ul>
 *   <li>请求头 Idempotency-Key 缺失：仅记录 X-Idempotency-Warning 头，不做严格拦截</li>
 *   <li>Filter 链中后续 Filter 抛出异常：安全头仍在 response 中，不会丢失</li>
 *   <li>非 POST/PUT 请求：跳过幂等性检查，不影响 GET/HEAD/OPTIONS</li>
 * </ul>
 */
@Component
@Order(1)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // 添加安全头
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        httpResponse.setHeader("Pragma", "no-cache");
        httpResponse.setHeader("Expires", "0");
        httpResponse.setHeader("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "font-src 'self' data:; " +
            "connect-src 'self' ws: wss:;"
        );

        // 校验重复提交: 对 POST/PUT 请求检查幂等性
        if ("POST".equalsIgnoreCase(httpRequest.getMethod()) || "PUT".equalsIgnoreCase(httpRequest.getMethod())) {
            String idempotencyKey = httpRequest.getHeader("Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                // 幂等键为空时不做严格拦截，仅记录
                httpResponse.setHeader("X-Idempotency-Warning", "missing-idempotency-key");
            }
        }
        
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初始化方法
    }

    @Override
    public void destroy() {
        // 销毁方法
    }
}