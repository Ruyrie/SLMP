package com.zhimu.server.integration.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

    @Bean
    public SmsClient smsClient(SmsProperties properties) {
        return properties.getMode() == SmsMode.ALIYUN ? new AliyunSmsClient(properties) : new MockSmsClient(properties);
    }
}
