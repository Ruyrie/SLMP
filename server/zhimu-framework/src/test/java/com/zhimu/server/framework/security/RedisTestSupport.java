package com.zhimu.server.framework.security;

import org.springframework.data.redis.connection.RedisConnectionCommands;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用本机 Redis（127.0.0.1:6379，database 15）。只删除测试自己写入的键。
 */
public final class RedisTestSupport {

    private static StringRedisTemplate template;
    private static Boolean available;

    private RedisTestSupport() {
    }

    public static synchronized StringRedisTemplate template() {
        if (template == null) {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 6379);
            config.setDatabase(15);
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            factory.start();
            template = new StringRedisTemplate(factory);
        }
        return template;
    }

    public static synchronized boolean available() {
        if (available == null) {
            try {
                template().execute((RedisCallback<String>) RedisConnectionCommands::ping);
                available = true;
            } catch (RuntimeException e) {
                available = false;
            }
        }
        return available;
    }

    public static void deleteByPattern(String pattern) {
        StringRedisTemplate redis = template();
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
