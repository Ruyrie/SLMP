package com.zhimu.server.integration.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public AiGateway aiGateway(AiProperties properties) {
        return new AiGatewayImpl(properties);
    }
}
