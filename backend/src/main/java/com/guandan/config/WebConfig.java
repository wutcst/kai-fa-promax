package com.guandan.config;

import com.guandan.interceptor.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * <p>
 * 功能说明：
 * 1. CORS跨域配置 —— 允许所有来源的前端请求
 * 2. Token拦截器注册 —— 除登录、注册、WebSocket外全部拦截
 * <p>
 * 接口字段：
 * - addCorsMappings(): 配置跨域允许的来源、方法、头信息
 * - addInterceptors(): 注册TokenInterceptor，配置排除路径
 * <p>
 * 异常场景：
 * - CORS配置不当 → 浏览器跨域请求被拒绝
 * - 排除路径遗漏 → 登录/注册接口被Token拦截导致无法访问
 * <p>
 * ── 回归验证点 ────────────────────────────────────────────
 * 1. addInterceptors 中 tokenInterceptor 已注入且不为 null
 * 2. 排除路径 /api/login、/api/register、/ws/** 存在
 * 3. OPTIONS 预检请求不被拦截
 * 4. 未登录请求返回 401 {"code":401,"message":"未登录，请先登录"}
 * 5. Token过期请求返回 401 {"code":401,"message":"登录已过期，请重新登录"}
 * 6. 拦截器order=1，在所有过滤链之前执行
 * 7. CORS allowedOriginPatterns="*" 允许跨域
 * 8. allowCredentials=true 且 allowedOriginPatterns="*" 不冲突
 * 9. CORS maxAge=3600 缓存预检结果1小时
 * 10. 响应Content-Type为application/json;charset=UTF-8
 * ─────────────────────────────────────────────────────────
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/login", "/api/register", "/ws/**")
                .order(1);
    }
}
