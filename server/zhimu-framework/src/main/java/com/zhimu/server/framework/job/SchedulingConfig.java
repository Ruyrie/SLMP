package com.zhimu.server.framework.job;

import com.zhimu.server.common.util.BeijingTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

/**
 * 定时调度线程池（@EnableScheduling 在启动类上）。任务写法：
 * <pre>
 * &#64;Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
 * &#64;JobLock("stock-snapshot")
 * public void snapshot() { ... }
 * </pre>
 * 调度使用真实北京时间（不受演示时钟 zhimu.clock.fixed-date 影响）；任务内部计算“今天”仍用 BeijingTime。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("zmt-job-");
        scheduler.setClock(Clock.system(BeijingTime.ZONE));
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(e -> log.error("定时任务执行异常", e));
        return scheduler;
    }
}
