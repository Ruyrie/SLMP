package com.zhimu.server.biz.feeding.job;

import com.zhimu.server.biz.feeding.service.FeedTaskService;
import com.zhimu.server.biz.feeding.service.FeedTodoSource;
import com.zhimu.server.biz.feeding.vo.FeedTaskVO;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 模块06 每日饲喂任务生成（技术准备文档 2.6.5、2.7.3）：
 * 每天 00:05（Asia/Shanghai）为有饲喂方案的在养批次按方案餐次生成当天任务，并同步生成模块03 待办。
 * <p>
 * 幂等：同批次 + 栏舍 + 日期 + 餐次已存在时跳过；多实例由 {@link JobLock} 保证只执行一次。
 * 生成逻辑复用 {@link FeedTaskService#generateDailyForFarm(Long, LocalDate)}；本类只负责调度与跨养殖场遍历。
 * 同一次生成也供“启动后补生成”调用，避免复制计算逻辑；手动补生成接口复用同一个 service 方法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedTaskGenerateJob {

    private final FeedTaskService taskService;
    private final FeedTodoSource todoSource;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    @JobLock("feed-task-generate")
    public void generate() {
        generateFor(BeijingTime.today());
    }

    /** 指定养殖场补生成指定日期的任务并同步待办；已存在的任务跳过 */
    public FeedTaskVO.GenerateToday generateForFarm(Long farmId, LocalDate date) {
        return generateForFarmInternal(farmId, date).vo();
    }

    /** 指定日期执行（单元测试用固定时钟；返回新生成的饲喂任务数） */
    int generateFor(LocalDate date) {
        int created = 0;
        int todos = 0;
        List<Long> farmIds = taskService.activeFarmIds();
        for (Long farmId : farmIds) {
            try {
                FarmResult result = generateForFarmInternal(farmId, date);
                created += result.vo().getGenerated();
                todos += result.todos();
            } catch (RuntimeException e) {
                // 单个养殖场失败不影响其他养殖场；下一个调度周期重试，幂等不会重复生成
                log.error("饲喂任务生成失败：farmId={} date={}", farmId, date, e);
            }
        }
        if (created > 0 || todos > 0) {
            log.info("每日饲喂任务生成完成：date={} farms={} tasks={} todos={}",
                    date, farmIds.size(), created, todos);
        }
        return created;
    }

    private FarmResult generateForFarmInternal(Long farmId, LocalDate date) {
        FeedTaskService.DailyGenerateResult result = taskService.generateDailyForFarm(farmId, date);
        int todos = todoSource.generate(TodoSource.TodoSourceContext.builder()
                .farmId(farmId)
                .date(date)
                .build()).size();
        FeedTaskVO.GenerateToday vo = new FeedTaskVO.GenerateToday();
        vo.setGenerated(result.generated());
        vo.setSkipped(result.skipped());
        return new FarmResult(vo, todos);
    }

    private record FarmResult(FeedTaskVO.GenerateToday vo, int todos) {
    }
}
