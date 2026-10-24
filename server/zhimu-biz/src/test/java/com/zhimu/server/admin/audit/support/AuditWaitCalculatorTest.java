package com.zhimu.server.admin.audit.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuditWaitCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 30);

    @Test
    void 等待时长文案按天小时分钟分段() {
        assertThat(AuditWaitCalculator.of(NOW.minusMinutes(39), NOW).text()).isEqualTo("39 分钟");
        assertThat(AuditWaitCalculator.of(NOW.minusMinutes(110), NOW).text()).isEqualTo("1 小时 50 分");
        assertThat(AuditWaitCalculator.of(NOW.minusMinutes(2 * 24 * 60 + 18 * 60), NOW).text())
                .isEqualTo("2 天 18 小时");
    }

    @Test
    void 恰好48小时不算超时_48小时1分钟算超时() {
        assertThat(AuditWaitCalculator.of(NOW.minusMinutes(48 * 60), NOW).overdue()).isFalse();
        assertThat(AuditWaitCalculator.of(NOW.minusMinutes(48 * 60 + 1), NOW).overdue()).isTrue();
    }

    @Test
    void 未来时间按0分钟处理() {
        assertThat(AuditWaitCalculator.of(NOW.plusHours(1), NOW).minutes()).isZero();
        assertThat(AuditWaitCalculator.of(NOW.plusHours(1), NOW).text()).isEqualTo("0 分钟");
    }
}
