package com.zhimu.server.framework.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Redis 配置：{@code RedisTemplate<String, Object>} 的键为字符串，值为 JSON（带 @class 类型信息，读回原类型）。
 * 值序列化使用独立的 JsonMapper（与接口 JSON 的 snake_case、字段裁剪等配置无关），Jackson 3 自带 java.time 支持。
 * <p>
 * 计数器、令牌、简单字符串请用 Spring Boot 自动配置的 StringRedisTemplate（数字以 Object 读回时可能变成 Integer）。
 * 顶层值用实体 / record / ArrayList / HashMap，不要直接存 List.of()、Map.of() 这类 JDK 不可变集合（没有类型信息，读不回来）。
 * 键名按 04 号文档第十二节。
 */
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        GenericJacksonJsonRedisSerializer valueSerializer = jsonValueSerializer();
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /** 只允许反序列化本项目与 JDK 常用类型，防止任意类型反序列化 */
    public static GenericJacksonJsonRedisSerializer jsonValueSerializer() {
        PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.zhimu.server.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.lang.")
                .build();
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(validator)
                .build();
    }
}
