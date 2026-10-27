package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyProductionTaskJobTest {
    @AfterEach
    void resetClock() {
        BeijingTime.setClock(null);
    }

    @Test
    void 不重启应用也能在业务跨日后补齐新的当天任务() {
        var recovery = mock(ProductionTaskRecovery.class);
        var job = new DailyProductionTaskJob(recovery);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-25T15:59:00Z"), BeijingTime.ZONE));
        job.onReady();

        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-25T16:07:00Z"), BeijingTime.ZONE));
        job.daily();

        verify(recovery).recoverFor(LocalDate.of(2026, 10, 25));
        verify(recovery).recoverFor(LocalDate.of(2026, 10, 26));
    }

    @Test
    void 跨日和白天都有连续的五分钟补偿机会() throws Exception {
        Scheduled schedule = DailyProductionTaskJob.class.getMethod("daily").getAnnotation(Scheduled.class);
        CronExpression cron = CronExpression.parse(schedule.cron());
        LocalDateTime first = cron.next(LocalDateTime.of(2026, 10, 25, 23, 59));
        assertThat(first).isEqualTo(LocalDateTime.of(2026, 10, 26, 0, 2));
        assertThat(cron.next(first)).isEqualTo(first.plusMinutes(5));
        assertThat(cron.next(LocalDateTime.of(2026, 10, 26, 15, 3)))
                .isEqualTo(LocalDateTime.of(2026, 10, 26, 15, 7));
        assertThat(schedule.zone()).isEqualTo(BeijingTime.ZONE.getId());
    }
}
