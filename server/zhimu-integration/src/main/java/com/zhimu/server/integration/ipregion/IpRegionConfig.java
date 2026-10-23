package com.zhimu.server.integration.ipregion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(Ip2regionProperties.class)
public class IpRegionConfig {

    @Bean
    public IpRegionResolver ipRegionResolver(Ip2regionProperties properties) {
        return new Ip2regionResolver(properties);
    }
}
