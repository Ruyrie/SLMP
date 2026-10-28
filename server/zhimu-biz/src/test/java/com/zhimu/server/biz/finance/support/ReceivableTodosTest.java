package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.FinanceTestTables;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.TodayTableInfo;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.biz.today.api.TodoReminderService;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 待收款待办：未收款写入、收齐完成、作废忽略；收款提醒按规则每周期只发一次。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceivableTodosTest {

    @Mock private SaleOrderMapper orderMapper;
    @Mock private TodoMapper todoMapper;
    @Mock private BatchQueryService batchQueryService;
    @Mock private TodoSourceWriteService writer;
    @Mock private TodoReminderService reminder;
    @Mock private MessageService messageService;

    private ReceivableTodos todos;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        FinanceTestTables.init();
        TodayTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 28, 9, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        ObjectProvider<TodoSourceWriteService> writerProvider = mock(ObjectProvider.class);
        ObjectProvider<TodoReminderService> reminderProvider = mock(ObjectProvider.class);
        ObjectProvider<MessageService> messageProvider = mock(ObjectProvider.class);
        when(writerProvider.getObject()).thenReturn(writer);
        when(reminderProvider.getObject()).thenReturn(reminder);
        when(messageProvider.getObject()).thenReturn(messageService);
        todos = new ReceivableTodos(orderMapper, todoMapper, batchQueryService, writerProvider, reminderProvider,
                messageProvider);
        when(batchQueryService.requireBatch(3272L)).thenReturn(new BatchInfo(1002L, 3272L, "ZM2610-072", 1L, 2L,
                LocalDate.of(2026, 9, 1), 0, "ENDED", List.of(), 0, 100, 0, 100, 2201L));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void unpaidOrderUpsertsReceivableTodoWithUnpaidAmountAndDueDate() {
        todos.sync(order("PARTIAL", "23920.00", LocalDate.of(2026, 10, 22)));

        verify(writer).reopenFromSource(1002L, "RECEIVABLE", "SALE_ORDER:210201", null);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TodoSource.TodoDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(writer).upsertFromSource(eq(1002L), captor.capture());
        TodoSource.TodoDraft draft = captor.getValue().get(0);
        assertThat(draft.getTodoType()).isEqualTo("RECEIVABLE");
        assertThat(draft.getCategory()).isEqualTo("BUSINESS");
        assertThat(draft.getDedupKey()).isEqualTo("SALE_ORDER:210201");
        assertThat(draft.getBizId()).isEqualTo(210201L);
        assertThat(draft.getAmount()).isEqualByComparingTo("23920.00");
        assertThat(draft.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(draft.getOverdueAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 0, 0));
        assertThat(draft.getTitle()).isEqualTo("ZM2610-072 收款已逾期");
        assertThat(draft.getContentText()).isEqualTo("西岭雏鸡采购户 · 未收 23,920 元");
        assertThat(draft.getReceiverScope()).isEqualTo("MANAGERS");
    }

    @Test
    void paidOrderCompletesAndVoidedOrderIgnoresTodo() {
        todos.sync(order("PAID", "0", LocalDate.of(2026, 10, 22)));
        verify(writer).completeFromSource(eq(1002L), eq("RECEIVABLE"), eq("SALE_ORDER:210201"),
                eq("SALE_ORDER"), eq(210201L), anyLong());

        todos.sync(order("VOIDED", "0", LocalDate.of(2026, 10, 22)));
        verify(writer).ignoreFromSource(1002L, "RECEIVABLE", "SALE_ORDER:210201", "出栏单已作废");
        verify(writer, never()).upsertFromSource(anyLong(), any());
    }

    @Test
    void reminderStagesFollowDueDayThenEveryThreeDaysFromThirdOverdueDay() {
        LocalDate due = LocalDate.of(2026, 10, 20);
        assertThat(ReceivableTodos.stageOf(due, due.minusDays(1))).isNull();
        assertThat(ReceivableTodos.stageOf(due, due)).isEqualTo("DUE");
        assertThat(ReceivableTodos.stageOf(due, due.plusDays(1))).isNull();
        assertThat(ReceivableTodos.stageOf(due, due.plusDays(3))).isEqualTo("OVERDUE");
        assertThat(ReceivableTodos.stageOf(due, due.plusDays(4))).isNull();
        assertThat(ReceivableTodos.stageOf(due, due.plusDays(6))).isEqualTo("REPEAT");
        assertThat(ReceivableTodos.stageOf(due, due.plusDays(9))).isEqualTo("REPEAT");
    }

    @Test
    void remindSendsOneMessageToOwnerAndManagersPerClaimedCycle() {
        SaleOrder dueToday = order("UNPAID", "8000.00", LocalDate.of(2026, 10, 28));
        SaleOrder notToday = order("UNPAID", "5000.00", LocalDate.of(2026, 10, 27));
        notToday.setId(210202L);
        when(orderMapper.selectList(any())).thenReturn(List.of(dueToday, notToday));
        when(orderMapper.selectManagerUserIds(1002L)).thenReturn(List.of(10002L, 10005L));
        when(reminder.claim(eq(1002L), eq("RECEIVABLE"), eq("SALE_ORDER:210201"), eq("DUE"), eq("20261028"),
                eq("MANAGERS"), anyInt())).thenReturn(90001L, (Long) null);

        assertThat(todos.remind(LocalDate.of(2026, 10, 28))).isEqualTo(1);
        // 同一天重跑：提醒计划已登记，不再发消息
        assertThat(todos.remind(LocalDate.of(2026, 10, 28))).isZero();

        ArgumentCaptor<MessageCommand> captor = ArgumentCaptor.forClass(MessageCommand.class);
        verify(messageService).send(captor.capture());
        MessageCommand command = captor.getValue();
        assertThat(command.getMsgType()).isEqualTo(MsgType.TODO_REMIND);
        assertThat(command.getReceiverUserIds()).containsExactlyInAnyOrder(10002L, 10005L);
        assertThat(command.getTitle()).isEqualTo("ZM2610-072 今天到期收款");
        assertThat(command.getRoute()).isEqualTo("E07");
        assertThat(command.getExtras()).containsEntry("sale_order_id", 210201L).containsEntry("todo_id", 90001L);
        verify(reminder, never()).claim(anyLong(), anyString(), eq("SALE_ORDER:210202"), anyString(), anyString(),
                anyString(), anyInt());
    }

    private static SaleOrder order(String status, String unpaid, LocalDate due) {
        SaleOrder order = new SaleOrder();
        order.setId(210201L);
        order.setFarmId(1002L);
        order.setBatchId(3272L);
        order.setSaleNo("CL261019-01");
        order.setSaleDate(LocalDate.of(2026, 10, 19));
        order.setBuyerName("西岭雏鸡采购户");
        order.setSaleAmount(new BigDecimal("23920.00"));
        order.setUnpaidAmount(new BigDecimal(unpaid));
        order.setPaymentStatus(status);
        order.setDueDate(due);
        order.setEffectiveDueDate(due);
        order.setUpdatedBy(10002L);
        return order;
    }
}
