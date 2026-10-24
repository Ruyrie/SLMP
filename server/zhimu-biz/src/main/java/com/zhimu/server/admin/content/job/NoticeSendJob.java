package com.zhimu.server.admin.content.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.mapper.NoticeMapper;
import com.zhimu.server.admin.content.service.NoticeDispatchService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台通知定时发送（每分钟）：status = SCHEDULED 且 scheduled_at ≤ 当前的条件更新为 SENT，
 * 然后分发；同时续发 5 分钟前仍未完成的 PENDING / RUNNING 通知。多实例由 @JobLock 保证单实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeSendJob {

    private final NoticeMapper noticeMapper;
    private final NoticeDispatchService dispatchService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    @JobLock("notice-send")
    public void run() {
        LocalDateTime now = BeijingTime.now();
        List<Notice> due = noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, "SCHEDULED")
                .le(Notice::getScheduledAt, now)
                .orderByAsc(Notice::getScheduledAt)
                .last("LIMIT 50"));
        for (Notice notice : due) {
            markSent(notice, now);
            try {
                dispatchService.dispatch(notice.getId());
            } catch (RuntimeException e) {
                log.warn("通知 {} 定时发送失败：{}", notice.getId(), e.getMessage());
            }
        }
        dispatchService.resumePending();
    }

    @Transactional
    protected void markSent(Notice notice, LocalDateTime now) {
        noticeMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getId, notice.getId())
                .eq(Notice::getStatus, "SCHEDULED")
                .eq(Notice::getVersion, notice.getVersion())
                .set(Notice::getStatus, "SENT")
                .set(Notice::getSentAt, now)
                .set(Notice::getSentBy, notice.getSentBy() == null ? notice.getCreatedBy() : notice.getSentBy())
                .set(Notice::getDispatchStatus, "PENDING")
                .set(Notice::getUpdatedAt, now)
                .setSql("version = version + 1"));
    }
}
