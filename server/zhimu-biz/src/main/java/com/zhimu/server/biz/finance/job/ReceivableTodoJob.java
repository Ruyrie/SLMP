package com.zhimu.server.biz.finance.job;

import com.zhimu.server.biz.finance.support.ReceivableTodos;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 待收款待办与收款提醒：
 * <ul>
 *     <li>启动时与每天 00:20 对账：历史出栏单补齐待收款待办、刷新逾期标题，已收齐 / 已作废的关闭（可重复执行）</li>
 *     <li>每天 09:00 按规则提醒所有者和场长（约定收款日当天、逾期第 3 天起每 3 天）；
 *     启动时若已过 09:00 补发当天的提醒，提醒计划去重保证同一天不重复</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceivableTodoJob {

    private static final LocalTime REMIND_AT = LocalTime.of(9, 0);

    private final ReceivableTodos receivableTodos;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reconcile();
        if (!BeijingTime.now().toLocalTime().isBefore(REMIND_AT)) {
            remind();
        }
    }

    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Shanghai")
    @JobLock("receivable-todo-reconcile")
    public void reconcile() {
        try {
            int count = receivableTodos.reconcileAll();
            log.info("待收款待办对账完成：未收款出栏单 {} 张", count);
        } catch (RuntimeException e) {
            log.warn("待收款待办对账失败，下次启动或凌晨重试", e);
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Shanghai")
    @JobLock("receivable-remind")
    public void remind() {
        try {
            int sent = receivableTodos.remind(BeijingTime.today());
            log.info("收款提醒发送完成：{} 张出栏单", sent);
        } catch (RuntimeException e) {
            log.warn("收款提醒发送失败", e);
        }
    }
}
