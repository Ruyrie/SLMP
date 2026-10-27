package com.zhimu.server.biz.account.event;

import com.zhimu.server.biz.account.service.AccountCancelCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 注销事务已经提交；立即异步启动个人清理，异常由持久化重试状态和定时任务补偿。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountCancelledListener {
    private final AccountCancelCleanupService cleanupService;

    @Async
    @EventListener
    public void onCancelled(AccountCancelledEvent event) {
        log.info("收到注销个人数据清理事件 userId={}", event.userId());
        cleanupService.cleanup(event.userId());
    }
}
