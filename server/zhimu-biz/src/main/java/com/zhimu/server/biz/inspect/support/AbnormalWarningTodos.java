package com.zhimu.server.biz.inspect.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.biz.inspect.convert.DiagnosisConvert;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportMapper;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 巡检异常上报 → 预警类待办（todo_type = ABNORMAL_REPORT，category = WARNING，所有者和场长可见）。
 * <p>
 * 上报“待诊断 / 已诊断待处理”（PENDING / DIAGNOSED）期间保持一条未完成预警；登记处理结果（HANDLED / CLOSED）后
 * 自动完成，上报被删除时自动忽略。首页 warning_count 与 T09 预警列表都读 t_todo，口径一致。
 * 提交上报、AI 诊断完成、登记处理时即时同步；{@link #reconcileAll()} 在启动与每天凌晨补齐历史数据，可重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbnormalWarningTodos {

    public static final String TODO_TYPE = "ABNORMAL_REPORT";
    private static final String BIZ_TYPE = "ABNORMAL_REPORT";
    private static final Set<String> OPEN_STATUS = Set.of("PENDING", "DIAGNOSED");

    private final AbnormalReportMapper reportMapper;
    private final TodoMapper todoMapper;
    private final PenQueryService penQueryService;
    private final BatchQueryService batchQueryService;
    private final CategoryQueryService categoryQueryService;
    // 延迟获取写入端，避免“待办服务 → 来源模块 → 待办写入”的构造依赖环
    private final ObjectProvider<TodoSourceWriteService> writerProvider;

    public static String dedupKey(Long reportId) {
        return BIZ_TYPE + ":" + reportId;
    }

    public static boolean isOpen(AbnormalReport report) {
        return report != null && !Boolean.TRUE.equals(report.getDeleted())
                && OPEN_STATUS.contains(report.getStatus() == null ? "PENDING" : report.getStatus());
    }

    /** 按上报当前状态写入 / 刷新 / 关闭预警待办 */
    public void sync(AbnormalReport report) {
        if (report == null || report.getId() == null || report.getFarmId() == null) {
            return;
        }
        TodoSourceWriteService writer = writerProvider.getObject();
        String key = dedupKey(report.getId());
        if (isOpen(report)) {
            writer.upsertFromSource(report.getFarmId(), List.of(draft(report)));
        } else if (Boolean.TRUE.equals(report.getDeleted())) {
            writer.ignoreFromSource(report.getFarmId(), TODO_TYPE, key, "异常上报已删除");
        } else {
            writer.completeFromSource(report.getFarmId(), TODO_TYPE, key, BIZ_TYPE, report.getId(),
                    report.getUpdatedBy() == null ? 0L : report.getUpdatedBy());
        }
    }

    /** 全部养殖场对账：未处理上报补写预警，已处理 / 已删除上报对应的未完成预警关闭 */
    public int reconcileAll() {
        List<AbnormalReport> open = TenantSkip.call(() -> reportMapper.selectList(
                Wrappers.<AbnormalReport>lambdaQuery().in(AbnormalReport::getStatus, OPEN_STATUS)));
        int count = 0;
        for (AbnormalReport report : open) {
            try {
                sync(report);
                count++;
            } catch (RuntimeException e) {
                log.warn("异常上报预警待办同步失败：reportId={}", report.getId(), e);
            }
        }
        List<Todo> pending = TenantSkip.call(() -> todoMapper.selectList(Wrappers.<Todo>lambdaQuery()
                .eq(Todo::getTodoType, TODO_TYPE).eq(Todo::getStatus, "PENDING")));
        for (Todo todo : pending) {
            AbnormalReport report = todo.getBizId() == null ? null
                    : TenantSkip.call(() -> reportMapper.selectById(todo.getBizId()));
            if (isOpen(report)) {
                continue;
            }
            try {
                if (report == null) {
                    writerProvider.getObject().ignoreFromSource(todo.getFarmId(), TODO_TYPE, todo.getDedupKey(),
                            "异常上报已删除");
                } else {
                    sync(report);
                }
            } catch (RuntimeException e) {
                log.warn("异常上报预警待办关闭失败：todoId={}", todo.getId(), e);
            }
        }
        return count;
    }

    private TodoSource.TodoDraft draft(AbnormalReport report) {
        Map<Long, PenInfo> pens = report.getPenId() == null ? Map.of()
                : safe(() -> penQueryService.getPens(List.of(report.getPenId())), Map.of());
        PenInfo pen = pens.get(report.getPenId());
        BatchInfo batch = report.getBatchId() == null ? null
                : safe(() -> batchQueryService.requireBatch(report.getBatchId()), null);
        String penName = pen == null ? null : pen.penName();
        String batchNo = batch == null ? null : batch.batchNo();
        boolean diagnosed = "DIAGNOSED".equals(report.getStatus());
        String title = (penName == null ? "" : penName + " · ")
                + (diagnosed ? "AI 诊断待处理" : "异常上报待诊断");
        StringBuilder content = new StringBuilder();
        if (diagnosed && report.getTopCauseName() != null && !report.getTopCauseName().isBlank()) {
            content.append("疑似").append(report.getTopCauseName()).append(" · ");
        }
        content.append("发病 ").append(report.getSickCount() == null ? 0 : report.getSickCount())
                .append(" ").append(unitText(batch));
        if (batchNo != null) {
            content.append(" · ").append(batchNo);
        }
        String urgency = DiagnosisConvert.urgencyText(report.getUrgency());
        LocalDate day = report.getReportDate() != null ? report.getReportDate()
                : report.getReportedAt() != null ? report.getReportedAt().toLocalDate() : BeijingTime.today();
        return TodoSource.TodoDraft.builder()
                .todoType(TODO_TYPE)
                .category("WARNING")
                .penId(report.getPenId())
                .batchId(report.getBatchId())
                .penName(penName)
                .batchNo(batchNo)
                .bizType(BIZ_TYPE)
                .bizId(report.getId())
                .dedupKey(dedupKey(report.getId()))
                .title(cut(title, 60))
                .contentText(cut(content.toString(), 100))
                .statusNote(diagnosed ? cut(urgency, 20) : "待诊断")
                .detailLabel("发病情况")
                .detailValue(cut(content.toString(), 100))
                .receiverScope("MANAGERS")
                .dueDate(day)
                .timeType("ALL_DAY")
                .build();
    }

    private String unitText(BatchInfo batch) {
        if (batch == null || batch.categoryId() == null) {
            return "头";
        }
        CategoryInfo category = safe(() -> categoryQueryService.info(batch.categoryId()), null);
        return category == null || category.countUnitText() == null ? "头" : category.countUnitText();
    }

    private static String cut(String value, int max) {
        if (value == null || value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max));
    }

    private static <T> T safe(java.util.function.Supplier<T> supplier, T fallback) {
        try {
            T value = supplier.get();
            return value == null ? fallback : value;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
