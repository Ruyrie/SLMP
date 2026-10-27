package com.zhimu.server.admin.system.job;

import com.zhimu.server.admin.system.service.UserActivityStatService;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityStatJob {
    private final UserActivityStatService service;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    @JobLock("user-activity-stat")
    public void run() {
        try {
            service.rebuildRecent();
        } catch (RuntimeException e) {
            log.error("平台活跃统计汇总失败，下次调度重试", e);
        }
    }
}
