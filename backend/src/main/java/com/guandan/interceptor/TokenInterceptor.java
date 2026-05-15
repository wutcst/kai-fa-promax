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

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Resource
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            log.debug("非HandlerMethod放行: {}", request.getRequestURI());
            return true;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            log.debug("OPTIONS预检放行: {}", request.getRequestURI());
            return true;
        }

        if (request.getRequestURI() == null) {
            log.warn("请求URI为空");
            return false;
        }

        String token = extractToken(request);

        if (token == null || token.isEmpty()) {
            log.warn("Token缺失: {}", request.getRequestURI());
            writeUnauthorizedResponse(response, "未登录，请先登录");
            return false;
        }

        String tokenValidation = checkTokenFormat(token);
        if (tokenValidation != null) {
            writeUnauthorizedResponse(response, tokenValidation);
            return false;
        }

        try {
            Long userId = authService.validateToken(token);
            if (userId == null) {
                log.warn("Token验证结果为空: {}", request.getRequestURI());
                writeUnauthorizedResponse(response, "登录已过期，请重新登录");
                return false;
            }
            if (userId <= 0) {
                log.warn("Token解析出非法userId: {}", userId);
                writeUnauthorizedResponse(response, "用户信息异常，请重新登录");
                return false;
            }
            UserContext.setUserId(userId);
            log.debug("Token验证通过: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.error("Token验证异常: {}", e.getMessage());
            response.setStatus(500);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":500,\"message\":\"认证服务异常\"}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTH_HEADER);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            String token = bearerToken.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    private String checkTokenFormat(String token) {
        if (token == null || token.isEmpty()) {
            return "Token不能为空";
        }
        if (token.length() < 10) {
            return "Token格式不正确";
        }
        return null;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
