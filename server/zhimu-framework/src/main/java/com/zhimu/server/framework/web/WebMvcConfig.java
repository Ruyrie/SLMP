package com.zhimu.server.framework.web;

import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.interceptor.AdminAuthInterceptor;
import com.zhimu.server.framework.security.interceptor.AuthInterceptor;
import com.zhimu.server.framework.security.interceptor.FarmContextInterceptor;
import com.zhimu.server.framework.security.interceptor.OriginCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 配置：拦截器（来源校验 → 登录 → 养殖场上下文）、snake_case 查询参数绑定、查询参数的日期格式。
 * 服务端不开放跨域，因此不配置 CORS。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SecurityProperties.class, AppVersionProperties.class})
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String[] EXCLUDE_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**", "/error"};

    private final OriginCheckInterceptor originCheckInterceptor;
    private final AuthInterceptor authInterceptor;
    private final FarmContextInterceptor farmContextInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(originCheckInterceptor)
                .addPathPatterns("/api/v1/auth/**", "/admin/v1/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(0);
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(10);
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/v1/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(10);
        registry.addInterceptor(farmContextInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(20);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new SnakeCaseModelAttributeMethodProcessor());
    }

    /** 查询参数中的日期时间：与 JSON 相同的格式（datetime 与时刻同时接受省略秒） */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateFormatter(BeijingTime.DATE);
        registrar.setDateTimeFormatter(ZhimuJackson.DATETIME_INPUT);
        registrar.setTimeFormatter(ZhimuJackson.TIME_INPUT);
        registrar.registerFormatters(registry);
    }
}
