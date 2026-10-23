package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.service.impl.ChangeRequestNoGenerator;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 修改申请编号：Redis seq:cr:yyyyMMdd + 4 位序号，跨日由日期键自然重置。
 */
class ChangeRequestNoGeneratorTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private ChangeRequestNoGenerator generator;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), BeijingTime.ZONE));
        when(redis.opsForValue()).thenReturn(ops);
        generator = new ChangeRequestNoGenerator(redis);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void generatesCrNumberFromDailySequence() {
        when(ops.increment("seq:cr:20261019")).thenReturn(8L);

        assertThat(generator.next()).isEqualTo("CR202610190008");
    }

    @Test
    void firstSequenceOfDaySetsTwoDayTtl() {
        when(ops.increment("seq:cr:20261019")).thenReturn(1L);

        assertThat(generator.next()).isEqualTo("CR202610190001");
        verify(redis).expire("seq:cr:20261019", Duration.ofDays(2));
    }

    @Test
    void laterSequenceDoesNotResetTtl() {
        when(ops.increment(anyString())).thenReturn(2L);

        generator.next();

        verify(redis, never()).expire(eq("seq:cr:20261019"), eq(Duration.ofDays(2)));
    }

    @Test
    void sequenceIsPaddedToFourDigitsAndWraps() {
        when(ops.increment(anyString())).thenReturn(12345L);

        assertThat(generator.next()).isEqualTo("CR202610192345");
    }
}
