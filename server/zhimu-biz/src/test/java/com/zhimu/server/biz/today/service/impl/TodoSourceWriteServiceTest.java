package com.zhimu.server.biz.today.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.today.TodoTableInfo;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.convert.TodoConvert;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.entity.TodoHandleLog;
import com.zhimu.server.biz.today.mapper.TodoHandleLogMapper;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.support.TodoScope;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 来源模块待办写入：幂等、已关闭行不复活、登记完成与自动忽略。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoSourceWriteServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock private TodoMapper todoMapper;
    @Mock private TodoHandleLogMapper handleLogMapper;
    @Mock private TodoScope todoScope;
    @Mock private TodoConvert todoConvert;
    @Mock private FarmQueryService farmQueryService;

    private TodoServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TodoTableInfo.ensure();
    }

    @BeforeEach
    void setUp() {
        service = new TodoServiceImpl(todoMapper, org.mockito.Mockito.mock(com.zhimu.server.biz.stock.support.StockWarningReconciler.class), handleLogMapper, todoScope, todoConvert, farmQueryService, org.mockito.Mockito.mock(com.zhimu.server.biz.feeding.api.FeedTaskReconciliationService.class));
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZONE));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void upsertInsertsPendingTodoWithDedupKeyAndSortTime() {
        when(todoMapper.selectByDedupIncludingDeleted(1001L, "FEEDING", "FEED_TASK:70107")).thenReturn(null);
        doAnswer(invocation -> {
            Todo todo = invocation.getArgument(0);
            todo.setId(95001L);
            return 1;
        }).when(todoMapper).insert(any(Todo.class));

        List<Long> ids = service.upsertFromSource(1001L, List.of(feedingDraft("3 号育肥舍 · 早餐饲喂")));

        assertThat(ids).containsExactly(95001L);
        ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
        verify(todoMapper).insert(captor.capture());
        Todo todo = captor.getValue();
        assertThat(todo.getFarmId()).isEqualTo(1001L);
        assertThat(todo.getTodoType()).isEqualTo("FEEDING");
        assertThat(todo.getCategory()).isEqualTo("WORK");
        assertThat(todo.getDedupKey()).isEqualTo("FEED_TASK:70107");
        assertThat(todo.getBizType()).isEqualTo("FEED_TASK");
        assertThat(todo.getBizId()).isEqualTo(70107L);
        assertThat(todo.getStatus()).isEqualTo("PENDING");
        assertThat(todo.getTimeType()).isEqualTo("AT_TIME");
        assertThat(todo.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(todo.getDueTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(todo.getSortTime()).isEqualTo(LocalDateTime.of(2026, 10, 19, 7, 0));
        assertThat(todo.getOverdueAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 9, 0));
        assertThat(todo.getVersion()).isZero();
        assertThat(todo.getDeleted()).isFalse();
    }

    @Test
    void upsertSecondRunUpdatesDisplayFieldsWithoutInsert() {
        Todo existing = existingTodo(95001L, "PENDING", null);
        when(todoMapper.selectByDedupIncludingDeleted(1001L, "FEEDING", "FEED_TASK:70107")).thenReturn(existing);
        when(todoMapper.update(any(Todo.class), any())).thenReturn(1);

        List<Long> ids = service.upsertFromSource(1001L, List.of(feedingDraft("3 号育肥舍 · 早餐饲喂（新）")));

        assertThat(ids).containsExactly(95001L);
        verify(todoMapper, never()).insert(any(Todo.class));
        ArgumentCaptor<LambdaUpdateWrapper<Todo>> captor = wrapperCaptor();
        verify(todoMapper).update(any(Todo.class), captor.capture());
        assertThat(captor.getValue().getSqlSet()).doesNotContain("status=");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains("3 号育肥舍 · 早餐饲喂（新）", "PEN_STAFF", "AT_TIME");
    }

    @Test
    void upsertDoesNotReopenDoneTodo() {
        Todo existing = existingTodo(95001L, "DONE", "REGISTER");
        when(todoMapper.selectByDedupIncludingDeleted(1001L, "FEEDING", "FEED_TASK:70107")).thenReturn(existing);
        when(todoMapper.update(any(Todo.class), any())).thenReturn(1);

        service.upsertFromSource(1001L, List.of(feedingDraft("3 号育肥舍 · 早餐饲喂")));

        ArgumentCaptor<LambdaUpdateWrapper<Todo>> captor = wrapperCaptor();
        verify(todoMapper).update(any(Todo.class), captor.capture());
        assertThat(captor.getValue().getSqlSet()).doesNotContain("status=");
        assertThat(existing.getStatus()).isEqualTo("DONE");
    }

    @Test
    void completeFromSourceOnlyTransitionsPendingAndWritesLog() {
        Todo pending = existingTodo(95001L, "PENDING", null);
        when(todoMapper.selectByDedupIncludingDeleted(1001L, "FEEDING", "FEED_TASK:70107")).thenReturn(pending);
        when(todoMapper.update(any(Todo.class), any())).thenReturn(1);

        boolean done = service.completeFromSource(1001L, "FEEDING", "FEED_TASK:70107",
                "FEED_RECORD", 7106L, 10003L);

        assertThat(done).isTrue();
        ArgumentCaptor<LambdaUpdateWrapper<Todo>> captor = wrapperCaptor();
        verify(todoMapper).update(any(Todo.class), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("status=", "complete_way=");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains("DONE", "REGISTER", "FEED_RECORD", 7106L, 10003L);
        ArgumentCaptor<TodoHandleLog> log = ArgumentCaptor.forClass(TodoHandleLog.class);
        verify(handleLogMapper).insert(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("COMPLETE");
        assertThat(log.getValue().getWay()).isEqualTo("REGISTER");
    }

    @Test
    void completeFromSourceIgnoresTodoAlreadyClosedByUser() {
        Todo ignored = existingTodo(95001L, "IGNORED", null);

        boolean done = service.completeFromSource(1001L, "FEEDING", "FEED_TASK:70107",
                "FEED_RECORD", 7106L, 10003L);

        assertThat(done).isFalse();
        verify(todoMapper, never()).update(any(Todo.class), any());
        verify(handleLogMapper, never()).insert(any(TodoHandleLog.class));
        assertThat(ignored.getStatus()).isEqualTo("IGNORED");
    }

    @Test
    void reopenFromSourceRestoresOnlyTodoCompletedByThatRecord() {
        Todo done = existingTodo(95001L, "DONE", "REGISTER");
        done.setCompleteRecordId(7106L);
        when(todoMapper.selectByDedupIncludingDeleted(1001L, "FEEDING", "FEED_TASK:70107")).thenReturn(done);
        when(todoMapper.update(any(Todo.class), any())).thenReturn(1);

        boolean reopened = service.reopenFromSource(1001L, "FEEDING", "FEED_TASK:70107", 7106L);

        assertThat(reopened).isTrue();
        ArgumentCaptor<LambdaUpdateWrapper<Todo>> captor = wrapperCaptor();
        verify(todoMapper).update(any(Todo.class), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("status=");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains("PENDING");
    }

    @Test
    void reopenFromSourceSkipsTodoCompletedByAnotherRecord() {
        Todo done = existingTodo(95001L, "DONE", "REGISTER");
        done.setCompleteRecordId(9999L);
        when(todoMapper.selectByDedupIncludingDeleted(1001L, "FEEDING", "FEED_TASK:70107")).thenReturn(done);

        boolean reopened = service.reopenFromSource(1001L, "FEEDING", "FEED_TASK:70107", 7106L);

        assertThat(reopened).isFalse();
        verify(todoMapper, never()).update(any(Todo.class), any());
    }

    private static ArgumentCaptor<LambdaUpdateWrapper<Todo>> wrapperCaptor() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Todo>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        return captor;
    }

    @Test
    void autoPausedTaskCanResumeButManualIgnoreRemainsClosed() {
        Todo ignored = existingTodo(95001L, "IGNORED", null);
        ignored.setIgnoreWay("AUTO");
        ignored.setIgnoreReason("消毒任务已暂停");
        when(todoMapper.selectByDedupIncludingDeleted(1001L, "DISINFECTION", "DISINFECT_TASK:9404"))
                .thenReturn(ignored);
        when(todoMapper.update(any(Todo.class), any())).thenReturn(1);

        assertThat(service.reopenAutoIgnoredFromSource(1001L, "DISINFECTION", "DISINFECT_TASK:9404", "消毒任务已暂停"))
                .isTrue();
        ignored.setIgnoreWay("MANUAL");
        assertThat(service.reopenAutoIgnoredFromSource(1001L, "DISINFECTION", "DISINFECT_TASK:9404", "消毒任务已暂停"))
                .isFalse();
        ignored.setIgnoreWay("AUTO");
        ignored.setIgnoreReason("消毒任务已取消");
        assertThat(service.reopenAutoIgnoredFromSource(1001L, "DISINFECTION", "DISINFECT_TASK:9404", "消毒任务已暂停"))
                .isFalse();
    }

    private static Todo existingTodo(Long id, String status, String completeWay) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setFarmId(1001L);
        todo.setTodoType("FEEDING");
        todo.setCategory("WORK");
        todo.setDedupKey("FEED_TASK:70107");
        todo.setStatus(status);
        todo.setCompleteWay(completeWay);
        todo.setVersion(1);
        todo.setDeleted(false);
        return todo;
    }

    private static TodoSource.TodoDraft feedingDraft(String title) {
        return TodoSource.TodoDraft.builder()
                .todoType("FEEDING")
                .category("WORK")
                .penId(2003L)
                .batchId(3001L)
                .penName("3 号育肥舍")
                .batchNo("ZM2610-031")
                .bizType("FEED_TASK")
                .bizId(70107L)
                .dedupKey("FEED_TASK:70107")
                .title(title)
                .contentText("育肥全价料 · 计划 160 千克")
                .receiverScope("PEN_STAFF")
                .assigneeId(10003L)
                .assigneeName("李明")
                .dueDate(LocalDate.of(2026, 10, 19))
                .timeType("AT_TIME")
                .dueTime(LocalTime.of(7, 0))
                .overdueAt(LocalDateTime.of(2026, 10, 19, 9, 0))
                .amount(new BigDecimal("160.00"))
                .build();
    }
}
