package com.zhimu.server.biz.account.job;

import com.zhimu.server.biz.account.mapper.AccountCancelMapper;
import com.zhimu.server.biz.account.service.AccountCancelCleanupService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 补偿遗漏事件、历史待清理记录与到期阶段；每批 100 个账号，由数据库行锁保证逐账号幂等。 */
@Component
@RequiredArgsConstructor
public class AccountCancelCleanupJob {
    private final AccountCancelMapper cancelMapper;
    private final AccountCancelCleanupService cleanupService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Shanghai")
    @JobLock("account-cancel-cleanup")
    public void cleanupDue() {
        for (Long userId : cancelMapper.selectDueUserIds(BeijingTime.now())) {
            cleanupService.cleanup(userId);
        }
    }
}
