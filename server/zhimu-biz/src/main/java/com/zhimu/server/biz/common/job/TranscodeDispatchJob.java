package com.zhimu.server.biz.common.job;

import com.zhimu.server.biz.common.service.TranscodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 转码补偿任务（管理端-04 2.8 TranscodeDispatchJob）：每分钟领取重启后遗留的 WAITING 任务、
 * 按 1/5/15 分钟重试未成功的派生版本，并重置超过 10 分钟无进度的 PROCESSING。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodeDispatchJob {

    private final TranscodeService transcodeService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void dispatch() {
        try {
            transcodeService.dispatch();
        } catch (RuntimeException e) {
            log.warn("转码补偿任务执行失败", e);
        }
    }
}
