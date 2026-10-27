package com.zhimu.server.admin.system.support;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class UserActivityWebConfig implements WebMvcConfigurer {
    private final UserActivityInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // AuthInterceptor=10、FarmContextInterceptor=20；管理员和匿名请求不计入养殖用户活跃。
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**").order(30);
    }
}
