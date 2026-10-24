package com.zhimu.server.biz.health.job;

import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.service.impl.DisinfectTaskGenerator;
import com.zhimu.server.biz.health.service.impl.HealthTodoSource;
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

/** 服务启动及每天凌晨补齐健康任务，不依赖用户手动修改或重新启用计划。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthTaskGenerateJob {

    private final HealthReferenceMapper referenceMapper;
    private final DisinfectTaskGenerator generator;
    private final HealthTodoSource todoSource;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        generate();
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Shanghai")
    @JobLock("health-task-generate")
    public void generate() {
        LocalDate today = BeijingTime.today();
        for (Long farmId : TenantSkip.call(referenceMapper::selectActiveFarmIds)) {
            try {
                generator.ensureGenerated(farmId, today);
                todoSource.generate(TodoSource.TodoSourceContext.builder().farmId(farmId).date(today).build());
            } catch (RuntimeException e) {
                log.warn("健康任务补生成失败：farmId={} date={}，页面读取时可重试", farmId, today, e);
            }
        }
    }
}
