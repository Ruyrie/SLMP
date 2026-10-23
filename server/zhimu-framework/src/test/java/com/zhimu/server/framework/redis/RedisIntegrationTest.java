package com.zhimu.server.framework.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 配置与 RedisLock：连本机 Redis database 15，键统一用 fwtest: 前缀并在测试后删除。
 */
@SpringBootTest(classes = RedisIntegrationTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(locations = "classpath:fw-data-test.properties")
class RedisIntegrationTest {

    private static final String LOCK_KEY = "fwtest:lock:demo";
    private static final String VALUE_KEY = "fwtest:value:demo";

    @SpringBootConfiguration
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    @Import({RedisConfig.class, RedisLock.class})
    static class TestApp {
    }

    public record CachedFarm(Long farmId, String name, BigDecimal area, LocalDate since, LocalDateTime updatedAt,
                             List<Long> penIds) {
    }

    @Autowired
    private RedisLock redisLock;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(List.of(LOCK_KEY, VALUE_KEY));
    }

    @Test
    void tryLockIsExclusiveUntilUnlocked() {
        String token = redisLock.tryLock(LOCK_KEY, 30);
        assertThat(token).startsWith(RedisLock.INSTANCE_ID + ":");
        assertThat(stringRedisTemplate.getExpire(LOCK_KEY)).isBetween(1L, 30L);

        assertThat(redisLock.tryLock(LOCK_KEY, 30)).isNull();
        assertThat(redisLock.unlock(LOCK_KEY, "someone-else")).isFalse();
        assertThat(stringRedisTemplate.hasKey(LOCK_KEY)).isTrue();

        assertThat(redisLock.unlock(LOCK_KEY, token)).isTrue();
        assertThat(stringRedisTemplate.hasKey(LOCK_KEY)).isFalse();
        assertThat(redisLock.unlock(LOCK_KEY, token)).isFalse();

        String again = redisLock.tryLock(LOCK_KEY, 30);
        assertThat(again).isNotNull().isNotEqualTo(token);
    }

    @Test
    void redisTemplateRoundTripsJsonWithJavaTime() {
        CachedFarm farm = new CachedFarm(1001L, "东山猪场", new BigDecimal("12.50"), LocalDate.of(2026, 10, 19),
                LocalDateTime.of(2026, 10, 19, 9, 10, 0), List.of(1L, 2L));
        redisTemplate.opsForValue().set(VALUE_KEY, farm, Duration.ofMinutes(1));

        assertThat(stringRedisTemplate.opsForValue().get(VALUE_KEY))
                .contains("\"@class\"")
                .contains("\"2026-10-19T09:10:00\"");
        assertThat(redisTemplate.opsForValue().get(VALUE_KEY)).isEqualTo(farm);
    }

    @Test
    void redisTemplateStoresMapValues() {
        Map<String, Object> value = new HashMap<>();
        value.put("role", "OWNER");
        value.put("pen_ids", new ArrayList<>(List.of(1L, 2L)));
        redisTemplate.opsForValue().set(VALUE_KEY, value, Duration.ofMinutes(1));

        Object read = redisTemplate.opsForValue().get(VALUE_KEY);
        assertThat(read).isInstanceOf(HashMap.class);
        assertThat(((Map<?, ?>) read).get("role")).isEqualTo("OWNER");
    }
}
