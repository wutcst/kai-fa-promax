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
 * 验证请求的Token有效性，将用户信息注入上下文
 */
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String TOKEN_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    @Resource
    private AuthService authenticatorService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!isControllerMethod(handler)) {
            return true;
        }

        if (isPreflightRequest(request)) {
            log.debug("预检请求放行: {}", request.getRequestURI());
            return true;
        }

        if (isEmptyRequest(request)) {
            return false;
        }

        String token = resolveToken(request);
        if (hasNoToken(token)) {
            log.warn("Token缺失: {}", request.getRequestURI());
            rejectUnauthorized(response, "未登录，请先登录");
            return false;
        }

        String validationError = validateTokenFormat(token);
        if (validationError != null) {
            rejectUnauthorized(response, validationError);
            return false;
        }

        return authenticateAndSetContext(request, response, token);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean isControllerMethod(Object handler) {
        return handler instanceof HandlerMethod;
    }

    private boolean isPreflightRequest(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private boolean isEmptyRequest(HttpServletRequest request) {
        if (request.getRequestURI() == null) {
            log.warn("请求URI为空");
            return true;
        }
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(TOKEN_HEADER);
        if (bearerToken != null && bearerToken.startsWith(TOKEN_PREFIX)) {
            String token = bearerToken.substring(TOKEN_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    private boolean hasNoToken(String token) {
        return token == null || token.isEmpty();
    }

    private String validateTokenFormat(String token) {
        if (token.length() < 10) {
            return "Token格式不正确";
        }
        return null;
    }

    private boolean authenticateAndSetContext(HttpServletRequest request, HttpServletResponse response, String token) throws Exception {
        try {
            Long userId = authenticatorService.validateToken(token);
            if (userId == null) {
                log.warn("Token验证结果为空: {}", request.getRequestURI());
                rejectUnauthorized(response, "登录已过期，请重新登录");
                return false;
            }
            if (userId <= 0) {
                log.warn("Token解析出非法userId: {}", userId);
                rejectUnauthorized(response, "用户信息异常，请重新登录");
                return false;
            }
            UserContext.setUserId(userId);
            log.debug("Token验证通过: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.error("Token验证异常: {}", e.getMessage());
            rejectInternalError(response);
            return false;
        }
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }

    private void rejectInternalError(HttpServletResponse response) throws Exception {
        response.setStatus(500);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":500,\"message\":\"认证服务异常\"}");
    }
}
