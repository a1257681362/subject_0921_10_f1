package com.evops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.evops.common.ApiResponse;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.filter.authz.RolesAuthorizationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 认证/鉴权失败返回可读 JSON，而不是重定向到空白登录页或返回空响应。
 * 这两个 Bean 以默认过滤器名 authc / roles 注册进 Shiro，覆盖 Shiro 内置过滤器；
 * 同时用 disabled 的 FilterRegistrationBean 阻止 Spring Boot 把它们再当作
 * 独立 Servlet 过滤器拦截全部请求。
 */
@Configuration
public class JsonAuthFilterConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean("authc")
    public FormAuthenticationFilter jsonAuthcFilter() {
        return new FormAuthenticationFilter() {
            @Override
            protected boolean onAccessDenied(ServletRequest request, ServletResponse response)
                    throws Exception {
                if (isLoginRequest(request, response)) {
                    return executeLogin(request, response);
                }
                writeJson((HttpServletResponse) response, HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHORIZED", "未登录或会话已过期，请先登录");
                return false;
            }
        };
    }

    @Bean("roles")
    public RolesAuthorizationFilter jsonRolesFilter() {
        return new RolesAuthorizationFilter() {
            @Override
            protected boolean onAccessDenied(ServletRequest request, ServletResponse response)
                    throws IOException {
                writeJson((HttpServletResponse) response, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "当前账户无权执行该操作");
                return false;
            }
        };
    }

    @Bean
    public FilterRegistrationBean<FormAuthenticationFilter> authcFilterRegistration(
            FormAuthenticationFilter jsonAuthcFilter) {
        FilterRegistrationBean<FormAuthenticationFilter> registration = new FilterRegistrationBean<>(jsonAuthcFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RolesAuthorizationFilter> rolesFilterRegistration(
            RolesAuthorizationFilter jsonRolesFilter) {
        FilterRegistrationBean<RolesAuthorizationFilter> registration = new FilterRegistrationBean<>(jsonRolesFilter);
        registration.setEnabled(false);
        return registration;
    }

    private void writeJson(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(code, message)));
    }
}
