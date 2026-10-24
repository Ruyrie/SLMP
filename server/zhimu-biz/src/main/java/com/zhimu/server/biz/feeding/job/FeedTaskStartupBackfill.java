package com.zhimu.server.biz.feeding.job;

import com.zhimu.server.biz.feeding.service.FeedTaskService;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 应用启动后补生成“今天”的饲喂任务：服务停机错过 00:05 时避免演示环境今日任务为空。
 * <p>
 * 幂等：只在“该养殖场今天一条任务都没有”时生成；已有任务（含已取消/已忽略）一律跳过，
 * 只插入当天任务，不修改任何历史数据。单个养殖场失败只记日志，不影响启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedTaskStartupBackfill implements ApplicationListener<ApplicationReadyEvent> {

    private final FeedTaskService taskService;
    private final FeedTaskGenerateJob generateJob;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            int generated = backfill(BeijingTime.today());
            if (generated > 0) {
                log.info("启动补生成今日饲喂任务完成：tasks={}", generated);
            }
        } catch (RuntimeException e) {
            // 补生成失败不能影响服务启动；手动补生成接口仍可使用
            log.warn("启动补生成今日饲喂任务失败，不影响启动", e);
        }
    }

    /** 指定日期执行（单元测试用固定时钟；返回新生成的任务数） */
    int backfill(LocalDate date) {
        int generated = 0;
        List<Long> farmIds = taskService.activeFarmIds();
        for (Long farmId : farmIds) {
            try {
                if (taskService.hasTasksOn(farmId, date)) {
                    continue;
                }
                generated += generateJob.generateForFarm(farmId, date).getGenerated();
            } catch (RuntimeException e) {
                log.warn("启动补生成今日饲喂任务失败：farmId={} date={}", farmId, date, e);
            }
        }
        return generated;
    }
}
