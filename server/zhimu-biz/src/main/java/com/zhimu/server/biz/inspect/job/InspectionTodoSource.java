package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.job.mapper.InspectionJobMapper;
import com.zhimu.server.biz.inspect.mapper.InspectReferenceMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.query.PenResponsibleRow;
import com.zhimu.server.biz.inspect.support.InspectRules;
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
import java.util.Objects;

/**
 * 模块07 的待办来源（模块03 SPI）：当天未巡的栏舍各一条 INSPECTION 待办，去重键 {@code INSPECTION:{巡检记录ID}}。
 * <ul>
 *     <li>同一栏舍同一天已有巡检待办（例如示例数据）时不再新建，避免重复；</li>
 *     <li>巡检登记后完成该栏舍当天的待办（登记接口由 {@link InspectionTodoCompleteAspect} 即时调用，生成时再兜底一次）；</li>
 *     <li>超过补录窗口仍未处理的巡检待办自动忽略。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class InspectionTodoSource implements TodoSource {

    public static final String TODO_TYPE = "INSPECTION";
    public static final String BIZ_TYPE = "INSPECTION";
    public static final String RECORD_TYPE = "INSPECTION";
    public static final int BACKFILL_DAYS = InspectRules.BACKFILL_DAYS;
    public static final String EXPIRED_REASON = "已超过补录期限";

    private final InspectionMapper inspectionMapper;
    private final InspectionJobMapper jobMapper;
    private final InspectReferenceMapper referenceMapper;
    private final TodoSourceWriteService todoWriter;

    @Override
    public String moduleName() {
        return "inspect";
    }

    @Override
    public List<Long> generate(TodoSourceContext context) {
        if (context == null || context.getFarmId() == null) {
            return List.of();
        }
        Long farmId = context.getFarmId();
        LocalDate date = context.getDate() == null ? BeijingTime.today() : context.getDate();
        return TenantSkip.call(() -> {
            List<Inspection> rows = inspectionMapper.selectDay(farmId, date);
            if (rows == null || rows.isEmpty()) {
                return List.<Long>of();
            }
            LocalTime deadline = deadline(farmId);
            Map<Long, List<InspectionTodoRow>> todosByPen = todosByPen(farmId, date);
            Map<Long, PenResponsibleRow> staff = firstResponsible(farmId);
            List<TodoDraft> drafts = new ArrayList<>();
            for (Inspection row : rows) {
                if (InspectRules.DONE.equals(row.getInspectStatus())) {
                    completeTodos(farmId, row, todosByPen.getOrDefault(row.getPenId(), List.of()));
                    continue;
                }
                if (!InspectRules.PENDING.equals(row.getInspectStatus()) && !InspectRules.MISSED.equals(row.getInspectStatus())) {
                    continue;
                }
                if (date.isBefore(BeijingTime.today().minusDays(BACKFILL_DAYS))) {
                    continue;
                }
                String key = dedupKey(row.getId());
                boolean otherTodo = todosByPen.getOrDefault(row.getPenId(), List.of()).stream()
                        .anyMatch(todo -> !key.equals(todo.getDedupKey()));
                if (otherTodo) {
                    continue;
                }
                drafts.add(draft(row, referenceMapper.selectPenName(row.getPenId()), staff.get(row.getPenId()),
                        deadline));
            }
            return drafts.isEmpty() ? List.<Long>of() : todoWriter.upsertFromSource(farmId, drafts);
        });
    }

    /** 巡检登记后完成该栏舍当天仍待处理的巡检待办；返回是否有待办被完成 */
    public boolean completeFor(Inspection inspection) {
        if (inspection == null || !InspectRules.DONE.equals(inspection.getInspectStatus())) {
            return false;
        }
        return TenantSkip.call(() -> {
            List<InspectionTodoRow> todos = new ArrayList<>();
            List<InspectionTodoRow> day = jobMapper.selectTodosOn(inspection.getFarmId(), inspection.getInspectDate());
            if (day != null) {
                for (InspectionTodoRow todo : day) {
                    if (Objects.equals(todo.getPenId(), inspection.getPenId())) {
                        todos.add(todo);
                    }
                }
            }
            return completeTodos(inspection.getFarmId(), inspection, todos);
        });
    }

    /** 截止日期已超过补录窗口仍待处理的巡检待办自动忽略；返回条数 */
    public int expireStale(Long farmId, LocalDate today) {
        return TenantSkip.call(() -> {
            int count = 0;
            List<InspectionTodoRow> stale = jobMapper.selectPendingTodosBefore(farmId, today.minusDays(BACKFILL_DAYS));
            for (InspectionTodoRow todo : stale == null ? List.<InspectionTodoRow>of() : stale) {
                if (todoWriter.ignoreFromSource(farmId, TODO_TYPE, todo.getDedupKey(), EXPIRED_REASON)) {
                    count++;
                }
            }
            return count;
        });
    }

    public static String dedupKey(Long inspectionId) {
        return BIZ_TYPE + ":" + inspectionId;
    }

    private boolean completeTodos(Long farmId, Inspection inspection, List<InspectionTodoRow> todos) {
        boolean completed = false;
        Long operator = inspection.getRegistrantId() == null ? 0L : inspection.getRegistrantId();
        for (InspectionTodoRow todo : todos) {
            if ("PENDING".equals(todo.getStatus())
                    && todoWriter.completeFromSource(farmId, TODO_TYPE, todo.getDedupKey(), RECORD_TYPE,
                    inspection.getId(), operator)) {
                completed = true;
            }
        }
        return completed;
    }

    private TodoDraft draft(Inspection row, String penName, PenResponsibleRow assignee, LocalTime deadline) {
        String name = penName == null ? "栏舍" : penName;
        return TodoDraft.builder()
                .todoType(TODO_TYPE)
                .category("WORK")
                .penId(row.getPenId())
                .batchId(row.getBatchId())
                .penName(penName)
                .bizType(BIZ_TYPE)
                .bizId(row.getId())
                .dedupKey(dedupKey(row.getId()))
                .title(name + " · 巡检")
                .contentText(InspectRules.formatTime(deadline) + " 截止")
                .receiverScope("PEN_STAFF")
                .assigneeId(assignee == null ? null : assignee.getUserId())
                .assigneeName(assignee == null ? null : assignee.getName())
                .dueDate(row.getInspectDate())
                .timeType("BEFORE_TIME")
                .dueTime(deadline)
                .overdueAt(row.getInspectDate().atTime(deadline))
                .build();
    }

    private LocalTime deadline(Long farmId) {
        LocalTime time = jobMapper.selectDeadline(farmId);
        return time == null ? InspectRules.DEFAULT_DEADLINE : time;
    }

    private Map<Long, List<InspectionTodoRow>> todosByPen(Long farmId, LocalDate date) {
        Map<Long, List<InspectionTodoRow>> map = new HashMap<>();
        List<InspectionTodoRow> todos = jobMapper.selectTodosOn(farmId, date);
        for (InspectionTodoRow todo : todos == null ? List.<InspectionTodoRow>of() : todos) {
            if (todo.getPenId() != null) {
                map.computeIfAbsent(todo.getPenId(), id -> new ArrayList<>()).add(todo);
            }
        }
        return map;
    }

    private Map<Long, PenResponsibleRow> firstResponsible(Long farmId) {
        Map<Long, PenResponsibleRow> map = new HashMap<>();
        List<PenResponsibleRow> rows = referenceMapper.selectPenResponsibles(farmId);
        for (PenResponsibleRow row : rows == null ? List.<PenResponsibleRow>of() : rows) {
            if (row.getPenId() != null) {
                map.putIfAbsent(row.getPenId(), row);
            }
        }
        return map;
    }
}
