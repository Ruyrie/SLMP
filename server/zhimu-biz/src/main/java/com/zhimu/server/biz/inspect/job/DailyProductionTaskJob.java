package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.biz.feeding.job.FeedTaskGenerateJob;
import com.zhimu.server.biz.health.service.impl.DisinfectTaskGenerator;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** 日期平移发生在单例初始化阶段；此处随后补齐任务，再按同一业务源生成今日待办。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyProductionTaskJob {
    private final InspectionTaskGenerator inspectionGenerator;
    private final InspectionTodoSource inspectionTodos;
    private final FeedTaskGenerateJob feeding;
    private final DisinfectTaskGenerator disinfect;
    private final List<TodoSource> sources;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        generateFor(BeijingTime.today());
    }

    @Scheduled(cron = "0 12 0 * * *", zone = "Asia/Shanghai")
    @JobLock("daily-production-tasks")
    public void daily() {
        generateFor(BeijingTime.today());
    }

    public void generateFor(LocalDate today) {
        for (Long farmId : inspectionGenerator.activeFarmIds()) {
            try {
                TenantSkip.run(() -> {
                    inspectionGenerator.markMissed(farmId, today);
                    inspectionGenerator.backfill(farmId, today);
                    feeding.generateForFarm(farmId, today);
                    disinfect.ensureGenerated(farmId, today);
                    for (LocalDate date = today.minusDays(InspectionTodoSource.BACKFILL_DAYS);
                         !date.isAfter(today); date = date.plusDays(1)) {
                        inspectionTodos.generate(TodoSource.TodoSourceContext.builder().farmId(farmId).date(date).build());
                    }
                    inspectionTodos.expireStale(farmId, today);
                    TodoSource.TodoSourceContext context = TodoSource.TodoSourceContext.builder().farmId(farmId).date(today).build();
                    for (TodoSource source : sources) {
                        if (source != inspectionTodos) source.generate(context);
                    }
                });
            } catch (RuntimeException error) {
                log.warn("生产任务启动/跨日补齐失败：farmId={} date={}，下次调度可重试", farmId, today, error);
            }
        }
    }
}
