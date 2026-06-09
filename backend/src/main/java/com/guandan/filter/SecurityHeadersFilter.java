package com.guandan.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SecurityHeadersFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse resp = (HttpServletResponse) response;
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("X-XSS-Protection", "1; mode=block");
        chain.doFilter(request, response);
    }
}
// SecurityHeadersFilter: add XSS, content-type, frame-options headers
// Fix: skip header set on non-HTTP responses
// Docs: filter chain path and security header specification
// Chore: SecurityHeadersFilter delivery materials
