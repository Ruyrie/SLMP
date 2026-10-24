package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.health.entity.DisinfectPlan;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.mapper.DisinfectPlanMapper;
import com.zhimu.server.biz.health.mapper.DisinfectTaskMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.support.HealthCodes;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 消毒周期持续生成：启动、每日调度与页面读取复用，停机跨日后也能补齐未来 90 天。 */
@Service
@RequiredArgsConstructor
public class DisinfectTaskGenerator {

    private final DisinfectPlanMapper planMapper;
    private final DisinfectTaskMapper taskMapper;
    private final HealthReferenceMapper referenceMapper;

    @Transactional
    public int ensureGenerated(Long farmId, LocalDate today) {
        if (farmId == null || today == null) {
            return 0;
        }
        return TenantSkip.call(() -> {
            int count = 0;
            for (DisinfectPlan candidate : planMapper.selectList(Wrappers.<DisinfectPlan>lambdaQuery()
                    .eq(DisinfectPlan::getFarmId, farmId)
                    .eq(DisinfectPlan::getStatus, HealthCodes.DISINFECT_PLAN_ACTIVE)
                    .ge(DisinfectPlan::getEndDate, today)
                    .le(DisinfectPlan::getStartDate, today.plusDays(89))
                    .orderByAsc(DisinfectPlan::getId))) {
                DisinfectPlan plan = planMapper.selectForUpdate(candidate.getId());
                if (plan == null || !farmId.equals(plan.getFarmId())
                        || !HealthCodes.DISINFECT_PLAN_ACTIVE.equals(plan.getStatus())
                        || plan.getEndDate().isBefore(today)) {
                    continue;
                }
                LocalDate from = plan.getStartDate().isAfter(today) ? plan.getStartDate() : today;
                LocalDate to = plan.getEndDate().isBefore(today.plusDays(89))
                        ? plan.getEndDate() : today.plusDays(89);
                count += generate(plan, referenceMapper.selectEnabledPlanPenIds(farmId, plan.getId()), from, to);
                if (plan.getGeneratedUntil() == null || plan.getGeneratedUntil().isBefore(to)) {
                    planMapper.update(null, Wrappers.<DisinfectPlan>lambdaUpdate()
                            .eq(DisinfectPlan::getId, plan.getId()).eq(DisinfectPlan::getFarmId, farmId)
                            .set(DisinfectPlan::getGeneratedUntil, to));
                }
            }
            return count;
        });
    }

    /** 唯一槽位为计划、栏舍、日期；DONE/IGNORED 槽位同样保留，不能重新生成。 */
    public int generate(DisinfectPlan plan, List<Long> penIds, LocalDate from, LocalDate to) {
        if (penIds == null || penIds.isEmpty() || from.isAfter(to)
                || plan.getCycleDays() == null || plan.getCycleDays() <= 0) {
            return 0;
        }
        Set<Slot> existing = new HashSet<>();
        for (DisinfectTask task : taskMapper.selectList(Wrappers.<DisinfectTask>lambdaQuery()
                .eq(DisinfectTask::getFarmId, plan.getFarmId())
                .eq(DisinfectTask::getPlanId, plan.getId())
                .between(DisinfectTask::getTaskDate, from, to)
                .isNotNull(DisinfectTask::getActiveFlag))) {
            existing.add(new Slot(task.getPenId(), task.getTaskDate()));
        }
        int count = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            long diff = ChronoUnit.DAYS.between(plan.getStartDate(), day);
            if (diff < 0 || diff % plan.getCycleDays() != 0) {
                continue;
            }
            for (Long penId : penIds) {
                if (!existing.add(new Slot(penId, day))) {
                    continue;
                }
                DisinfectTask task = new DisinfectTask();
                task.setFarmId(plan.getFarmId());
                task.setPlanId(plan.getId());
                task.setPenId(penId);
                task.setSource(HealthCodes.TASK_SOURCE_PLAN);
                task.setTaskDate(day);
                task.setDueTime(plan.getDueTime() == null ? LocalTime.of(17, 0) : plan.getDueTime());
                task.setCatalogId(plan.getCatalogId());
                task.setDisinfectantName(plan.getDisinfectantName());
                task.setConcentration(plan.getConcentration());
                task.setMethodCode(plan.getMethodCode());
                task.setStatus(HealthCodes.TASK_PENDING);
                task.setActiveFlag(true);
                task.setManualDone(false);
                task.setOverdueNotified(false);
                task.setVersion(0);
                task.setCreatedBy(0L);
                try {
                    taskMapper.insert(task);
                    count++;
                } catch (DuplicateKeyException ignored) {
                    // 保存方案和读取补生成并发时，由唯一索引保证不会重复。
                }
            }
        }
        return count;
    }

    private record Slot(Long penId, LocalDate date) { }
}
