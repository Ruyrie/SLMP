package com.zhimu.server.integration.amap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AmapProperties.class)
public class AmapConfig {

    @Bean
    public AmapClient amapClient(AmapProperties properties) {
        return new AmapClientImpl(properties);
    }
}
