package com.zhimu.server.biz.today.api;

import java.util.List;
import java.util.Map;

/** 待办查询（模块03 实现，第 3 批）；在此之前返回 0 */
public interface TodoQueryService {

    /** 成员在该养殖场待处理的待办数（移出成员影响预览用） */
    int countPendingForMember(Long farmId, Long userId);

    /**
     * API-L01 生产概况用：当前养殖场（FarmContext）的今日待办数与未解除的预警类待办。
     * 今日待办口径与 API-T02 一致（已逾期、今天到期、预警类与休药期到期）。
     */
    ProductionTodoSummary productionSummary();

    /** 今日待办数 + 预警项；预警项按标题倒序（越早越靠前） */
    record ProductionTodoSummary(int todayTodoCount, List<WarningTodo> warnings) {

        public ProductionTodoSummary {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    /** 预警类待办（生产概况的提醒项） */
    record WarningTodo(Long todoId, String todoType, String title, String subtitle, String tagText,
                       Long batchId, String route) {
    }

    /**
     * API-L02 我的栏舍：当前成员（FarmContext）今天各栏舍的作业类待办。
     * 未完成项口径与今日待办一致（今天到期或已逾期）；今日已完成按 due_date = 今天的 DONE 统计。
     * 默认实现返回空（未接入时栏舍卡显示 0/0）。
     */
    default Map<Long, PenTasks> penTasksToday() {
        return Map.of();
    }

    /** 某栏舍今天的作业：已完成数、总数（含已完成）、未完成列表 */
    record PenTasks(int done, int total, List<PenTask> pending) {

        public PenTasks {
            pending = pending == null ? List.of() : List.copyOf(pending);
        }
    }

    /** 栏舍卡的作业行（与 App TodoBriefVO 对齐） */
    record PenTask(Long todoId, String todoType, String title, String timeText, String status, String statusText,
                   String route, Long taskId, Long batchId, Long penId) {
    }
}
