package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.mapper.DisinfectTaskMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.ImmunePlanMapper;
import com.zhimu.server.biz.health.query.PenResponsibleRow;
import com.zhimu.server.biz.health.support.HealthCodes;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 健康任务与今日待办使用同一个业务 ID；登记完成后关闭对应待办。 */
@Service
@RequiredArgsConstructor
public class HealthTodoSource implements TodoSource {

    public static final String IMMUNIZATION = "IMMUNIZATION";
    public static final String DISINFECTION = "DISINFECTION";
    private static final String PAUSED_REASON = "消毒任务已暂停";

    private final ImmunePlanMapper planMapper;
    private final DisinfectTaskMapper taskMapper;
    private final HealthReferenceMapper referenceMapper;
    private final BatchQueryService batchQueryService;
    private final TodoSourceWriteService todoWriter;

    @Override
    public String moduleName() {
        return "health";
    }

    @Override
    public List<Long> generate(TodoSourceContext context) {
        if (context == null || context.getFarmId() == null) {
            return List.of();
        }
        Long farmId = context.getFarmId();
        LocalDate date = context.getDate() == null ? BeijingTime.today() : context.getDate();
        return TenantSkip.call(() -> {
            List<Long> result = new ArrayList<>();
            Map<Long, BatchInfo> batches = new HashMap<>();
            for (BatchInfo batch : batchQueryService.listActive(farmId)) {
                batches.put(batch.batchId(), batch);
            }
            for (ImmunePlan plan : planMapper.selectList(Wrappers.<ImmunePlan>lambdaQuery()
                    .eq(ImmunePlan::getFarmId, farmId).eq(ImmunePlan::getPreEntry, false)
                    .le(ImmunePlan::getPlanDate, date.plusDays(1)))) {
                BatchInfo batch = batches.get(plan.getBatchId());
                if (HealthCodes.PLAN_CANCELLED.equals(plan.getStatus())) {
                    todoWriter.ignoreFromSource(farmId, IMMUNIZATION, immuneKey(plan.getId()), "免疫计划已取消");
                } else if (plan.getBatchId() == null || batch != null) {
                    result.addAll(todoWriter.upsertFromSource(farmId, List.of(immuneDraft(plan, batch))));
                    if (HealthCodes.PLAN_DONE.equals(plan.getStatus())) {
                        todoWriter.completeFromSource(farmId, IMMUNIZATION, immuneKey(plan.getId()),
                                plan.getRecordId() == null ? null : "IMMUNE_RECORD", plan.getRecordId(), 0L);
                    }
                }
            }
            for (DisinfectTask task : taskMapper.selectList(Wrappers.<DisinfectTask>lambdaQuery()
                    .eq(DisinfectTask::getFarmId, farmId).le(DisinfectTask::getTaskDate, date.plusDays(1)))) {
                if (HealthCodes.TASK_CANCELLED.equals(task.getStatus()) || HealthCodes.TASK_PAUSED.equals(task.getStatus())) {
                    todoWriter.ignoreFromSource(farmId, DISINFECTION, disinfectKey(task.getId()),
                            HealthCodes.TASK_PAUSED.equals(task.getStatus()) ? PAUSED_REASON : "消毒任务已取消");
                    continue;
                }
                todoWriter.reopenAutoIgnoredFromSource(farmId, DISINFECTION, disinfectKey(task.getId()), PAUSED_REASON);
                result.addAll(todoWriter.upsertFromSource(farmId, List.of(disinfectDraft(task))));
                if (HealthCodes.TASK_DONE.equals(task.getStatus())) {
                    todoWriter.completeFromSource(farmId, DISINFECTION, disinfectKey(task.getId()),
                            task.getRecordId() == null ? null : "DISINFECT_RECORD", task.getRecordId(), 0L);
                }
            }
            return result;
        });
    }

    public void completeImmune(ImmunePlan plan, Long recordId, Long executorId) {
        BatchInfo batch = plan.getBatchId() == null ? null : batchQueryService.requireBatch(plan.getBatchId());
        todoWriter.upsertFromSource(plan.getFarmId(), List.of(immuneDraft(plan, batch)));
        todoWriter.completeFromSource(plan.getFarmId(), IMMUNIZATION, immuneKey(plan.getId()),
                "IMMUNE_RECORD", recordId, executorId);
    }

    public void completeDisinfect(DisinfectTask task, Long recordId, Long executorId) {
        todoWriter.reopenAutoIgnoredFromSource(task.getFarmId(), DISINFECTION, disinfectKey(task.getId()), PAUSED_REASON);
        todoWriter.upsertFromSource(task.getFarmId(), List.of(disinfectDraft(task)));
        todoWriter.completeFromSource(task.getFarmId(), DISINFECTION, disinfectKey(task.getId()),
                "DISINFECT_RECORD", recordId, executorId);
    }

    private TodoDraft immuneDraft(ImmunePlan plan, BatchInfo batch) {
        Long penId = plan.getPenId();
        if (penId == null && batch != null) {
            penId = batch.penIds() == null || batch.penIds().isEmpty() ? batch.entryPenId() : batch.penIds().get(0);
        }
        String penName = penId == null ? "种畜" : referenceMapper.selectPenName(penId);
        LocalTime due = plan.getDueTime() == null ? LocalTime.of(18, 0) : plan.getDueTime();
        return TodoDraft.builder().todoType(IMMUNIZATION).category("WORK")
                .penId(penId).penName(penName).batchId(plan.getBatchId()).batchNo(batch == null ? null : batch.batchNo())
                .bizType("IMMUNE_PLAN").bizId(plan.getId()).dedupKey(immuneKey(plan.getId()))
                .title((penName == null ? "栏舍" : penName) + " · 免疫接种")
                .contentText(plan.getVaccineName()).detailLabel("免疫项目").detailValue(plan.getVaccineName())
                .statusNote(HealthCodes.PLAN_MISSED.equals(plan.getStatus()) ? "漏免" : null)
                .receiverScope("PEN_ALL").dueDate(plan.getPlanDate()).timeType("BEFORE_TIME").dueTime(due)
                .overdueAt(plan.getPlanDate().atTime(due)).build();
    }

    private TodoDraft disinfectDraft(DisinfectTask task) {
        String penName = referenceMapper.selectPenName(task.getPenId());
        PenResponsibleRow assignee = referenceMapper.selectPenResponsibles(task.getFarmId()).stream()
                .filter(row -> java.util.Objects.equals(row.getPenId(), task.getPenId()) && row.getUserId() != null)
                .findFirst().orElse(null);
        LocalTime due = task.getDueTime() == null ? LocalTime.of(17, 0) : task.getDueTime();
        return TodoDraft.builder().todoType(DISINFECTION).category("WORK")
                .penId(task.getPenId()).penName(penName).bizType("DISINFECT_TASK").bizId(task.getId())
                .dedupKey(disinfectKey(task.getId())).title((penName == null ? "栏舍" : penName) + " · 消毒")
                .contentText(task.getDisinfectantName()).detailLabel("消毒要求")
                .detailValue(task.getDisinfectantName() + " " + task.getConcentration())
                .assigneeId(assignee == null ? null : assignee.getUserId())
                .assigneeName(assignee == null ? null : assignee.getName())
                .receiverScope("PEN_ALL").dueDate(task.getTaskDate()).timeType("BEFORE_TIME").dueTime(due)
                .overdueAt(task.getTaskDate().atTime(due)).build();
    }

    public static String immuneKey(Long planId) {
        return "IMMUNE_PLAN:" + planId;
    }

    public static String disinfectKey(Long taskId) {
        return "DISINFECT_TASK:" + taskId;
    }
}
