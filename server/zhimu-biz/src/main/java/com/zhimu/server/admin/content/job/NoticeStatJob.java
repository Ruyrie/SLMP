package com.zhimu.server.admin.content.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.mapper.NoticeMapper;
import com.zhimu.server.admin.content.service.NoticeStatService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知阅读统计（每 5 分钟）：近 30 天发送、已分发完成的通知，从模块03 同步阅读与推送点开时间。
 * 单次最多处理 200 条，避免长事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeStatJob {

    private final NoticeMapper noticeMapper;
    private final NoticeStatService statService;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    @JobLock("notice-stat")
    public void run() {
        List<Notice> notices = noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, "SENT")
                .eq(Notice::getDispatchStatus, "DONE")
                .ge(Notice::getSentAt, BeijingTime.now().minusDays(30))
                .orderByAsc(Notice::getStatAt)
                .last("LIMIT 200"));
        for (Notice notice : notices) {
            try {
                statService.refresh(notice);
            } catch (RuntimeException e) {
                log.warn("通知 {} 阅读统计失败：{}", notice.getId(), e.getMessage());
            }
        }
    }
}
