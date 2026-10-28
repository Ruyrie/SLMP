package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.biz.inspect.support.AbnormalWarningTodos;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 启动及每天凌晨对账异常上报预警待办：补齐历史未处理上报、关闭已处理上报的预警（可重复执行）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbnormalWarningTodoJob {

    private final AbnormalWarningTodos warningTodos;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reconcile();
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Shanghai")
    @JobLock("abnormal-warning-todo")
    public void reconcile() {
        try {
            int count = warningTodos.reconcileAll();
            log.info("异常上报预警待办对账完成：未处理上报 {} 条", count);
        } catch (RuntimeException e) {
            log.warn("异常上报预警待办对账失败，下次启动或凌晨重试", e);
        }
    }
}
