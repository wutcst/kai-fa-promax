package com.guandan.interceptor;

import com.guandan.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token 鉴权拦截器：从 Authorization 头提取 Bearer Token，
 * 验证非空后写入 UserContext，未登录返回 401。
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            return false;
        }
        UserContext.setContext(1L, "default");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
/**
 * 鉴权流程：
 * 1. OPTIONS 预检放行
 * 2. 提取 Authorization: Bearer <token>
 * 3. Token 为空或空白 → 401
 * 4. 写入 UserContext 供后续请求使用
 * 5. afterCompletion 清理 ThreadLocal
 */
