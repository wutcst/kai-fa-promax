package com.guandan.interceptor;

import com.guandan.service.AuthService;
import com.guandan.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token拦截器
 * 除登录注册和WebSocket外，所有请求需携带有效Token
 */
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Resource
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非Controller方法直接放行（静态资源、swagger等）
        if (!(handler instanceof HandlerMethod)) {
            log.debug("非HandlerMethod放行: {}", request.getRequestURI());
            return true;
        }

        // OPTIONS预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            log.debug("OPTIONS预检放行: {}", request.getRequestURI());
            return true;
        }

        // 空请求处理
        if (request.getRequestURI() == null) {
            log.warn("请求URI为空");
            return false;
        }

        // 从请求头提取Token
        String token = extractToken(request);

        // Token缺失处理
        if (token == null || token.isEmpty()) {
            log.warn("Token缺失: {}", request.getRequestURI());
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录，请先登录\"}");
            return false;
        }

        // 验证Token有效性
        try {
            Long userId = authService.validateToken(token);
            if (userId == null) {
                log.warn("Token验证结果为空: {}", request.getRequestURI());
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\"}");
                return false;
            }

            // 检查userId合法性
            if (userId <= 0) {
                log.warn("Token解析出非法userId: {}", userId);
                response.setStatus(401);
                response.getWriter().write("{\"code\":401,\"message\":\"用户信息异常，请重新登录\"}");
                return false;
            }

            // 存入上下文
            UserContext.setUserId(userId);
            log.debug("Token验证通过: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.error("Token验证异常: {}", e.getMessage());
            response.setStatus(500);
            response.getWriter().write("{\"code\":500,\"message\":\"认证服务异常\"}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 从Authorization头中提取Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            String token = bearerToken.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }
}
