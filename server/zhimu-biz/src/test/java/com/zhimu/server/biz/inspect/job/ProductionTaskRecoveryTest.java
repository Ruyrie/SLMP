package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.biz.feeding.service.FeedTaskService;
import com.zhimu.server.biz.health.service.impl.DisinfectTaskGenerator;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.framework.lock.JobLockAspect;
import com.zhimu.server.framework.redis.RedisLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionTaskRecoveryTest {
    private final LocalDate today = LocalDate.of(2026, 10, 25);
    private final InspectionTaskGenerator inspection = mock(InspectionTaskGenerator.class);
    private final InspectionTodoSource inspectionTodos = mock(InspectionTodoSource.class);
    private final FeedTaskService feeding = mock(FeedTaskService.class);
    private final DisinfectTaskGenerator disinfect = mock(DisinfectTaskGenerator.class);
    private final TodoSource healthTodos = mock(TodoSource.class);
    private final TodoSource withdrawalTodos = mock(TodoSource.class);
    private final ProductionTaskRecovery recovery = new ProductionTaskRecovery(inspection, inspectionTodos,
            feeding, disinfect, List.of(inspectionTodos, healthTodos, withdrawalTodos));

    @BeforeEach
    void setup() {
        when(inspection.activeFarmIds()).thenReturn(List.of(1001L, 1002L));
        when(healthTodos.moduleName()).thenReturn("health");
        when(withdrawalTodos.moduleName()).thenReturn("withdrawal");
    }

    @Test
    void 一个步骤短暂失败不阻断其他模块和养殖场且下轮自动恢复() {
        when(feeding.generateDailyForFarm(1001L, today)).thenThrow(new DataAccessResourceFailureException("offline"))
                .thenReturn(null);

        var first = recovery.recoverFor(today);

        assertThat(first.checkedFarms()).isEqualTo(2);
        assertThat(first.failedSteps()).isEqualTo(1);
        verify(disinfect).ensureGenerated(1001L, today);
        verify(feeding).generateDailyForFarm(1002L, today);
        verify(healthTodos, times(2)).generate(any());

        var retry = recovery.recoverFor(today);

        assertThat(retry.failedSteps()).isZero();
        verify(feeding, times(2)).generateDailyForFarm(1001L, today);
    }

    @Test
    void 一种待办来源失败仍处理其他来源和日期() {
        when(inspectionTodos.generate(argThat(context -> context.getFarmId().equals(1001L)
                && context.getDate().equals(today.minusDays(1)))))
                .thenThrow(new IllegalStateException("source unavailable"));
        when(healthTodos.generate(argThat(context -> context.getFarmId().equals(1001L))))
                .thenThrow(new IllegalStateException("health unavailable"));

        var result = recovery.recoverFor(today);

        assertThat(result.failedSteps()).isEqualTo(2);
        verify(inspectionTodos).generate(argThat(context -> context.getFarmId().equals(1001L)
                && context.getDate().equals(today)));
        verify(inspectionTodos).expireStale(1001L, today);
        verify(withdrawalTodos, times(2)).generate(any());
    }

    @Test
    void 当天补偿使用当天日期并仅按既有补录窗口检查巡检待办() {
        recovery.recoverFor(today);

        verify(feeding).generateDailyForFarm(1001L, today);
        verify(feeding).generateDailyForFarm(1002L, today);
        verify(disinfect).ensureGenerated(1001L, today);
        verify(inspectionTodos, times(2 * (InspectionTodoSource.BACKFILL_DAYS + 1))).generate(any());
        verify(healthTodos).generate(argThat(context -> context.getFarmId().equals(1001L)
                && context.getDate().equals(today)));
    }

    @Test
    void 启动和周期入口均经过同一把锁并在锁被占用时跳过() {
        RedisLock locks = mock(RedisLock.class);
        when(locks.tryLock("lock:job:daily-production-tasks", 600)).thenReturn("owner-token", null);
        AspectJProxyFactory factory = new AspectJProxyFactory(recovery);
        factory.addAspect(new JobLockAspect(locks));
        DailyProductionTaskJob job = new DailyProductionTaskJob(factory.getProxy());

        job.onReady();
        job.daily();

        verify(inspection, times(1)).activeFarmIds();
        verify(locks, times(2)).tryLock("lock:job:daily-production-tasks", 600);
        verify(locks).unlock("lock:job:daily-production-tasks", "owner-token");
    }
}
