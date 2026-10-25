package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 启动及运行期间定期补齐任务；所有入口共享补偿服务的锁，不移动历史日期。 */
@Component
@RequiredArgsConstructor
public class DailyProductionTaskJob {
    private final ProductionTaskRecovery recovery;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        recovery.recoverFor(BeijingTime.today());
    }

    // 每五分钟重试遗漏的槽位；错开饲喂（00:05）和健康（00:10）的每日生成时刻。
    @Scheduled(cron = "0 2/5 * * * *", zone = "Asia/Shanghai")
    public void daily() {
        recovery.recoverFor(BeijingTime.today());
    }
}
