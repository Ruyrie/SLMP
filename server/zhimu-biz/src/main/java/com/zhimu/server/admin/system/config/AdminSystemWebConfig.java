package com.zhimu.server.admin.system.config;

import com.zhimu.server.admin.system.interceptor.AdminClientInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端来源校验拦截器：作用于除 Swagger/Actuator 外的全部 /admin/v1 接口，
 * 排在框架的 Origin 校验（order 0）之后、会话校验（order 10）之前。
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class AdminSystemWebConfig implements WebMvcConfigurer {

    private static final String[] EXCLUDE_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**", "/error"};

    private final AdminClientInterceptor adminClientInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminClientInterceptor)
                .addPathPatterns("/admin/v1/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(5);
    }
}
