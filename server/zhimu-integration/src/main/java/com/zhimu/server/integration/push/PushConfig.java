package com.zhimu.server.integration.push;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    @Bean
    public PushClient pushClient(PushProperties properties) {
        return new JPushClient(properties);
    }
}
