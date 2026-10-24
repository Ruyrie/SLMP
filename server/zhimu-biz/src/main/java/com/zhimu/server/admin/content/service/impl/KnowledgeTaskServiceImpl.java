package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.content.entity.KnowledgeTask;
import com.zhimu.server.admin.content.mapper.KnowledgeTaskMapper;
import com.zhimu.server.admin.content.query.KnowledgeTaskQuery;
import com.zhimu.server.admin.content.request.KnowledgeTaskCreateRequest;
import com.zhimu.server.admin.content.request.TaskCloseRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.service.KnowledgeTaskService;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.vo.KnowledgeTaskVO;
import com.zhimu.server.admin.system.api.AdminMessageCommand;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.api.AdminPermissionService;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeMapper;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 知识补充任务（ADM-C54–C56）。 */
@Service
@RequiredArgsConstructor
public class KnowledgeTaskServiceImpl implements KnowledgeTaskService {

    private static final Set<String> SOURCE_TYPES = Set.of("AI_QA", "CORRECTION", "MANUAL");
    private static final Set<String> MODES = Set.of("NEW_ENTRY", "SUPPLEMENT");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");

    private final KnowledgeTaskMapper taskMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final AiMessageMapper messageMapper;
    private final AdminMapper adminMapper;
    private final AdminPermissionService permissionService;
    private final AdminMessageService messageService;
    private final ContentCategoryAdminService categoryService;
    private final ContentLogs logs;

    @Override
    public PageVO<KnowledgeTaskVO> page(KnowledgeTaskQuery query) {
        int pageNo = query.pageOrDefault();
        int size = query.sizeOrDefault();
        var wrapper = Wrappers.<KnowledgeTask>lambdaQuery();
        if (query.getStatus() != null && !query.getStatus().isBlank()
                && !"ALL".equalsIgnoreCase(query.getStatus())) {
            wrapper.eq(KnowledgeTask::getStatus, query.getStatus().toUpperCase());
        }
        if (query.getAssigneeId() != null) {
            wrapper.eq(KnowledgeTask::getAssigneeId, query.getAssigneeId());
        }
        wrapper.last("ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, due_date ASC, id DESC");
        Page<KnowledgeTask> page = taskMapper.selectPage(new Page<>(pageNo, size), wrapper);
        List<KnowledgeTaskVO> rows = new ArrayList<>();
        for (KnowledgeTask task : page.getRecords()) {
            rows.add(toVO(task));
        }
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", Math.toIntExact(taskMapper.selectCount(Wrappers.<KnowledgeTask>lambdaQuery())));
        tabs.put("PENDING", Math.toIntExact(taskMapper.selectCount(Wrappers.<KnowledgeTask>lambdaQuery()
                .eq(KnowledgeTask::getStatus, "PENDING"))));
        tabs.put("DONE", Math.toIntExact(taskMapper.selectCount(Wrappers.<KnowledgeTask>lambdaQuery()
                .eq(KnowledgeTask::getStatus, "DONE"))));
        tabs.put("CLOSED", Math.toIntExact(taskMapper.selectCount(Wrappers.<KnowledgeTask>lambdaQuery()
                .eq(KnowledgeTask::getStatus, "CLOSED"))));
        return PageVO.of(rows, page.getTotal(), pageNo, size).withTabCounts(tabs);
    }

    @Override
    @Transactional
    public KnowledgeTaskVO create(KnowledgeTaskCreateRequest request) {
        String sourceType = request.getSourceType().toUpperCase();
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw ContentErrors.field("source_type", "来源不正确");
        }
        String mode = request.getMode().toUpperCase();
        if (!MODES.contains(mode)) {
            throw ContentErrors.field("mode", "补充方式不正确");
        }
        var category = categoryService.requireEnabled(request.getCategoryId());
        AiMessage source = null;
        if ("AI_QA".equals(sourceType) || "CORRECTION".equals(sourceType)) {
            if (request.getSourceMessageId() == null) {
                throw ContentErrors.field("source_message_id", "请选择来源问答");
            }
            source = messageMapper.selectById(request.getSourceMessageId());
            if (source == null) {
                throw ContentErrors.field("source_message_id", "来源问答不存在");
            }
        }
        Long targetId = null;
        if ("SUPPLEMENT".equals(mode)) {
            if (request.getTargetKnowledgeId() == null) {
                throw ContentErrors.field("target_knowledge_id", "请选择目标条目");
            }
            AiKnowledge target = knowledgeMapper.selectById(request.getTargetKnowledgeId());
            if (target == null) {
                throw ContentErrors.field("target_knowledge_id", "目标条目不存在");
            }
            targetId = target.getId();
        }
        Admin assignee = adminMapper.selectById(request.getAssigneeId());
        if (assignee == null || !"NORMAL".equals(assignee.getStatus())) {
            throw ContentErrors.field("assignee_id", "被指派的管理员不存在或已停用");
        }
        if (!permissionService.permissionsOf(assignee.getId()).contains("WA-C04:HANDLE")) {
            throw ContentErrors.field("assignee_id", "被指派人没有知识库处理权限");
        }
        if (request.getDueDate().isBefore(BeijingTime.today())) {
            throw ContentErrors.field("due_date", "期望完成日期不能早于今天");
        }
        if (request.getSourceMessageId() != null
                && taskMapper.selectPendingBySource(request.getSourceMessageId()) != null) {
            throw ContentErrors.stateChanged("这条问答已有待处理的补充任务");
        }
        KnowledgeTask task = new KnowledgeTask();
        task.setTitle(request.getTitle().trim());
        task.setSourceType(sourceType);
        task.setSourceMessageId(request.getSourceMessageId());
        task.setMode(mode);
        task.setTargetKnowledgeId(targetId);
        task.setCategoryId(category.getId());
        task.setAssigneeId(assignee.getId());
        task.setDueDate(request.getDueDate());
        task.setPoints(request.getPoints().trim());
        task.setSimilarCount(request.getSimilarCount() == null ? 0 : request.getSimilarCount());
        task.setStatus("PENDING");
        task.setVersion(0);
        task.setCreatedBy(AdminContext.requireAdminId());
        taskMapper.insert(task);

        if (source != null) {
            messageMapper.update(null, Wrappers.<AiMessage>lambdaUpdate()
                    .eq(AiMessage::getId, source.getId())
                    .set(AiMessage::getHandleStatus, "TASKED")
                    .and(w -> w.isNull(AiMessage::getHandleStatus)
                            .or().ne(AiMessage::getHandleStatus, "CORRECTED")));
        }
        notifyAssignee(task, assignee);
        writeLog("TASK_CREATE", task.getId(), task.getTitle(), null, null,
                Map.of("assignee_id", assignee.getId(), "status", "PENDING"));
        return toVO(task);
    }

    @Override
    @Transactional
    public KnowledgeTaskVO close(Long id, TaskCloseRequest request) {
        KnowledgeTask task = taskMapper.selectById(id);
        if (task == null) {
            throw ContentErrors.notFound();
        }
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), task.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw ContentErrors.stateChanged("任务已完成或已关闭");
        }
        task.setStatus("CLOSED");
        task.setCloseReason(request.getCloseReason());
        task.setClosedBy(AdminContext.requireAdminId());
        task.setClosedAt(BeijingTime.now());
        if (taskMapper.updateById(task) == 0) {
            throw ContentErrors.versionConflict();
        }
        writeLog("TASK_CLOSE", id, task.getTitle(), request.getCloseReason(),
                Map.of("status", "PENDING"), Map.of("status", "CLOSED"));
        return toVO(task);
    }

    @Override
    @Transactional
    public void completeForKnowledge(Long knowledgeId, Integer versionNo) {
        for (KnowledgeTask task : taskMapper.selectPendingByTarget(knowledgeId)) {
            task.setStatus("DONE");
            task.setResultKnowledgeId(knowledgeId);
            task.setResultVersionNo(versionNo);
            task.setCompletedAt(BeijingTime.now());
            taskMapper.updateById(task);
        }
    }

    private void notifyAssignee(KnowledgeTask task, Admin assignee) {
        if (Objects.equals(assignee.getId(), AdminContext.adminId())) {
            return;
        }
        AdminMessageCommand command = AdminMessageCommand.builder()
                .adminId(assignee.getId())
                .msgType("KNOWLEDGE_TASK_ASSIGNED")
                .level("NORMAL")
                .title("你有一个知识补充任务")
                .summary("你有一个知识补充任务：" + task.getTitle() + "，期望 "
                        + task.getDueDate().format(MONTH_DAY) + "完成")
                .bizType("KNOWLEDGE_TASK")
                .bizId(task.getId())
                .routePage("WA-C04")
                .routeParams(Map.of("tab", "tasks", "task_id", task.getId()))
                .mergeKey("knowledge-task:" + task.getId())
                .build();
        messageService.send(command);
    }

    private KnowledgeTaskVO toVO(KnowledgeTask task) {
        KnowledgeTaskVO vo = new KnowledgeTaskVO();
        vo.setId(task.getId());
        vo.setVersion(task.getVersion() == null ? 0 : task.getVersion());
        vo.setTitle(task.getTitle());
        vo.setSourceType(task.getSourceType());
        vo.setSourceTypeText(ContentTexts.taskSource(task.getSourceType()));
        vo.setSourceMessageId(task.getSourceMessageId());
        if (task.getSourceMessageId() != null) {
            AiMessage message = messageMapper.selectById(task.getSourceMessageId());
            vo.setSourceQuestion(message == null ? null : message.getQuestionText());
        }
        vo.setMode(task.getMode());
        vo.setModeText(ContentTexts.taskMode(task.getMode()));
        vo.setTargetKnowledgeId(task.getTargetKnowledgeId());
        if (task.getTargetKnowledgeId() != null) {
            AiKnowledge target = knowledgeMapper.selectById(task.getTargetKnowledgeId());
            vo.setTargetTitle(target == null ? null : target.getTitle());
        }
        vo.setCategoryId(task.getCategoryId());
        vo.setCategoryName(categoryService.nameOf(task.getCategoryId()));
        vo.setSimilarCount(task.getSimilarCount());
        vo.setAssigneeId(task.getAssigneeId());
        Admin assignee = task.getAssigneeId() == null ? null : adminMapper.selectById(task.getAssigneeId());
        vo.setAssigneeName(assignee == null ? null : assignee.getName());
        vo.setDueDate(task.getDueDate());
        vo.setIsOverdue("PENDING".equals(task.getStatus()) && task.getDueDate() != null
                && task.getDueDate().isBefore(BeijingTime.today()));
        vo.setPoints(task.getPoints());
        vo.setStatus(task.getStatus());
        vo.setStatusText(ContentTexts.taskStatus(task.getStatus()));
        vo.setResultKnowledgeId(task.getResultKnowledgeId());
        vo.setResultVersionNo(task.getResultVersionNo());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setCloseReason(task.getCloseReason());
        vo.setCreatedAt(task.getCreatedAt());
        if (task.getCreatedBy() != null) {
            Admin creator = adminMapper.selectById(task.getCreatedBy());
            vo.setCreatedByName(creator == null ? null : creator.getName());
        }
        return vo;
    }

    private void writeLog(String action, Long id, String title, String reason, Object before, Object after) {
        logs.record(OperationLogEntry.builder()
                .opType("KNOWLEDGE")
                .opAction(action)
                .targetName(title)
                .bizId(id)
                .reason(reason)
                .before(before)
                .after(after)
                .build());
    }
}
