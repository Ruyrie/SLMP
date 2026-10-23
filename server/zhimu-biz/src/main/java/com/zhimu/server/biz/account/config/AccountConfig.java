package com.zhimu.server.biz.account.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 模块01 账号的配置：BCrypt（强度 10）与账号参数。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountProperties.class)
public class AccountConfig {

    @Bean
    public PasswordEncoder accountPasswordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
