package com.zhimu.server.biz.inspect.support;

import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportMapper;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 异常上报 → 预警待办：未处理写入（首页预警数与 T09 同口径），登记处理后完成。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbnormalWarningTodosTest {

    @Mock private AbnormalReportMapper reportMapper;
    @Mock private TodoMapper todoMapper;
    @Mock private PenQueryService penQueryService;
    @Mock private BatchQueryService batchQueryService;
    @Mock private CategoryQueryService categoryQueryService;
    @Mock private TodoSourceWriteService writer;

    private AbnormalWarningTodos todos;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        com.zhimu.server.biz.today.TodayTableInfo.ensure();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), AbnormalReport.class);
        ObjectProvider<TodoSourceWriteService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(writer);
        todos = new AbnormalWarningTodos(reportMapper, todoMapper, penQueryService, batchQueryService,
                categoryQueryService, provider);
        when(penQueryService.getPens(any())).thenReturn(Map.of(2003L,
                new PenInfo(2003L, 1001L, "F03", "3 号育肥舍", "FATTENING", "育肥舍", 220, "ENABLED")));
        when(batchQueryService.requireBatch(3001L)).thenReturn(new BatchInfo(1001L, 3001L, "ZM2610-031", 1L, 101L,
                LocalDate.of(2026, 7, 1), 200, "ACTIVE", List.of(2003L), 30, 200, 0, 0, 2003L));
        when(categoryQueryService.info(101L)).thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD", "头",
                false, true));
    }

    @Test
    void diagnosedReportBecomesOpenWarningTodo() {
        AbnormalReport report = report("DIAGNOSED");
        report.setTopCauseName("猪流行性腹泻");
        report.setUrgency("URGENT");

        todos.sync(report);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TodoSource.TodoDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(writer).upsertFromSource(eq(1001L), captor.capture());
        TodoSource.TodoDraft draft = captor.getValue().get(0);
        assertThat(draft.getTodoType()).isEqualTo("ABNORMAL_REPORT");
        assertThat(draft.getCategory()).isEqualTo("WARNING");
        assertThat(draft.getReceiverScope()).isEqualTo("MANAGERS");
        assertThat(draft.getDedupKey()).isEqualTo("ABNORMAL_REPORT:6001");
        assertThat(draft.getBizId()).isEqualTo(6001L);
        assertThat(draft.getTitle()).isEqualTo("3 号育肥舍 · AI 诊断待处理");
        assertThat(draft.getContentText()).isEqualTo("疑似猪流行性腹泻 · 发病 5 头 · ZM2610-031");
        assertThat(draft.getStatusNote()).isEqualTo("紧急");
        assertThat(draft.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    void handledOrClosedReportCompletesWarningTodo() {
        todos.sync(report("HANDLED"));
        todos.sync(report("CLOSED"));

        verify(writer, org.mockito.Mockito.times(2)).completeFromSource(eq(1001L), eq("ABNORMAL_REPORT"),
                eq("ABNORMAL_REPORT:6001"), eq("ABNORMAL_REPORT"), eq(6001L), anyLong());
        verify(writer, never()).upsertFromSource(anyLong(), any());
    }

    @Test
    void reconcileBackfillsOpenReportsAndClosesStaleTodos() {
        when(reportMapper.selectList(any())).thenReturn(List.of(report("PENDING")));
        com.zhimu.server.biz.today.entity.Todo stale = new com.zhimu.server.biz.today.entity.Todo();
        stale.setId(1L);
        stale.setFarmId(1001L);
        stale.setBizId(6002L);
        stale.setDedupKey("ABNORMAL_REPORT:6002");
        when(todoMapper.selectList(any())).thenReturn(List.of(stale));
        AbnormalReport handled = report("HANDLED");
        handled.setId(6002L);
        when(reportMapper.selectById(6002L)).thenReturn(handled);

        assertThat(todos.reconcileAll()).isEqualTo(1);

        verify(writer).upsertFromSource(eq(1001L), any());
        verify(writer).completeFromSource(eq(1001L), eq("ABNORMAL_REPORT"), eq("ABNORMAL_REPORT:6002"),
                eq("ABNORMAL_REPORT"), eq(6002L), anyLong());
    }

    private static AbnormalReport report(String status) {
        AbnormalReport report = new AbnormalReport();
        report.setId(6001L);
        report.setFarmId(1001L);
        report.setPenId(2003L);
        report.setBatchId(3001L);
        report.setSickCount(5);
        report.setStatus(status);
        report.setReportDate(LocalDate.of(2026, 10, 27));
        report.setReportedAt(LocalDateTime.of(2026, 10, 27, 9, 0));
        report.setUpdatedBy(10001L);
        report.setDeleted(false);
        return report;
    }
}
