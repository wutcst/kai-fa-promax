package com.guandan.interceptor;

import com.guandan.annotation.IgnoreAuth;
import com.guandan.common.ResponseCode;
import com.guandan.exception.BusinessException;
import com.guandan.service.AgentTokenService;
import com.guandan.service.TokenCache;
import com.guandan.util.JwtUtil;
import com.guandan.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenCache tokenCache;

    @Autowired
    private AgentTokenService agentTokenService;

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) return true;

        HandlerMethod hm = (HandlerMethod) handler;
        if (hm.getMethodAnnotation(IgnoreAuth.class) != null ||
                hm.getBeanType().getAnnotation(IgnoreAuth.class) != null) {
            return true;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED.getCode(), "未登录，请先登录");
        }

        // Agent 永久 Token
        if (agentTokenService.isAgentToken(token)) {
            UserContext.setContext(agentTokenService.getAgentUserId(), agentTokenService.getAgentUsername(), token);
            return true;
        }

        // JWT 验证
        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        if (!tokenCache.validateToken(userId, token)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED.getCode(), "登录已在其他设备失效");
        }

        UserContext.setContext(userId, username, token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
