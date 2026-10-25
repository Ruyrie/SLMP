package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.biz.feeding.service.FeedTaskService;
import com.zhimu.server.biz.health.service.impl.DisinfectTaskGenerator;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.framework.lock.JobLock;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 复用各模块的幂等生成逻辑，只补缺少的任务和待办，不移动日期或编造实际业务记录。
 * 单个养殖场/步骤失败不会阻止其他步骤；下一次周期重新检查，已存在的槽位原样保留。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionTaskRecovery {
    private final InspectionTaskGenerator inspectionGenerator;
    private final InspectionTodoSource inspectionTodos;
    private final FeedTaskService feeding;
    private final DisinfectTaskGenerator disinfect;
    private final List<TodoSource> sources;

    public record Result(int checkedFarms, int failedSteps) { }

    // 锁放在独立 Bean 上，启动事件和调度入口均经过代理，避免同类自调用绕过锁。
    @JobLock("daily-production-tasks")
    public Result recoverFor(LocalDate today) {
        List<Long> farms = inspectionGenerator.activeFarmIds();
        int failed = 0;
        for (Long farmId : farms) {
            failed += TenantSkip.call(() -> recoverFarm(farmId, today));
        }
        if (failed > 0) {
            log.warn("生产任务补偿未全部完成：date={} farms={} failedSteps={}，下个周期自动重试",
                    today, farms.size(), failed);
        }
        return new Result(farms.size(), failed);
    }

    private int recoverFarm(Long farmId, LocalDate today) {
        int failed = attempt(farmId, today, "巡检漏巡状态", () -> inspectionGenerator.markMissed(farmId, today));
        failed += attempt(farmId, today, "巡检任务", () -> inspectionGenerator.backfill(farmId, today));
        failed += attempt(farmId, today, "饲喂任务", () -> feeding.generateDailyForFarm(farmId, today));
        failed += attempt(farmId, today, "消毒任务", () -> disinfect.ensureGenerated(farmId, today));
        for (LocalDate date = today.minusDays(InspectionTodoSource.BACKFILL_DAYS);
             !date.isAfter(today); date = date.plusDays(1)) {
            LocalDate day = date;
            failed += attempt(farmId, day, "巡检待办", () -> inspectionTodos.generate(context(farmId, day)));
        }
        failed += attempt(farmId, today, "过期巡检待办", () -> inspectionTodos.expireStale(farmId, today));
        for (TodoSource source : sources) {
            if (source != inspectionTodos) {
                failed += attempt(farmId, today, source.moduleName() + "待办", () -> source.generate(context(farmId, today)));
            }
        }
        return failed;
    }

    private int attempt(Long farmId, LocalDate date, String step, Runnable action) {
        try {
            action.run();
            return 0;
        } catch (RuntimeException error) {
            log.warn("生产任务步骤失败：farmId={} date={} step={}，下个周期自动重试", farmId, date, step, error);
            return 1;
        }
    }

    private TodoSource.TodoSourceContext context(Long farmId, LocalDate date) {
        return TodoSource.TodoSourceContext.builder().farmId(farmId).date(date).build();
    }
}
