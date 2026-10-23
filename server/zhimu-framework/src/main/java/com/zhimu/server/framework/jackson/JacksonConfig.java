package com.zhimu.server.framework.jackson;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把统一 JSON 规则套用到 Spring Boot 自动配置的 JsonMapper（HTTP 请求体与响应体）。
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer zhimuJsonMapperCustomizer() {
        return ZhimuJackson::customizeHttp;
    }
}
