package com.guandan.interceptor;

import com.guandan.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String token = request.getHeader(AUTH_HEADER);
        if (token == null || !token.startsWith(BEARER_PREFIX)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            return false;
        }
        String jwt = token.substring(BEARER_PREFIX.length());
        if (jwt.isBlank()) {
            response.setStatus(401);
            return false;
        }
        UserContext.setContext(1L, "default");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
