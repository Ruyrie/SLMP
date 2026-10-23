package com.zhimu.server.biz.today.service.impl;

import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.today.convert.TodoConvert;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.TodoHandleLogMapper;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.request.TodoCompleteRequest;
import com.zhimu.server.biz.today.request.TodoIgnoreRequest;
import com.zhimu.server.biz.today.support.TodoScope;
import com.zhimu.server.biz.today.vo.TodoDetailVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** API-T08/T09 的权限、状态与版本校验：40303、42207、41001、40902、40004。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoServiceImplTest {

    @Mock private TodoMapper todoMapper;
    @Mock private TodoHandleLogMapper handleLogMapper;
    @Mock private TodoScope todoScope;
    @Mock private TodoConvert todoConvert;
    @Mock private FarmQueryService farmQueryService;

    private TodoServiceImpl service;
    private FarmContext.FarmMember owner;
    private FarmContext.FarmMember staff;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZoneId.of("Asia/Shanghai")));
        service = new TodoServiceImpl(todoMapper, handleLogMapper, todoScope, todoConvert, farmQueryService);
        owner = new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER, Set.of(),
                FarmStatus.NORMAL);
        staff = new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2003L, 2004L), FarmStatus.NORMAL);
        FarmContext.set(owner);
        when(farmQueryService.listFarmsOfUser(anyLong())).thenReturn(List.of(
                FarmBriefVO.builder().farmId(1001L).name("东山猪场").build()));
        when(todoConvert.toDetail(any(), any(), anyBoolean(), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(new TodoDetailVO());
        when(todoScope.memberFor(eq(1001L), anyLong())).thenReturn(owner);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        FarmContext.clear();
    }

    @Test
    void completeDoneByStaffReturns40303() {
        Todo todo = todo(90112L, "WORK", "PENDING", 3);
        when(todoMapper.selectIncludingDeleted(90112L)).thenReturn(todo);
        FarmContext.set(staff);
        when(todoScope.memberFor(1001L, 10003L)).thenReturn(staff);

        assertThatThrownBy(() -> service.complete(90112L, completeRequest(3), 10003L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.ROLE_FORBIDDEN.getCode());
    }

    @Test
    void completeBusinessTodoReturns42207() {
        Todo todo = todo(90201L, "BUSINESS", "PENDING", 2);
        when(todoMapper.selectIncludingDeleted(90201L)).thenReturn(todo);

        assertThatThrownBy(() -> service.complete(90201L, completeRequest(2), 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.STATE_NOT_ALLOWED.getCode());
    }

    @Test
    void completeDeletedTodoReturns41001() {
        Todo todo = todo(90112L, "WORK", "PENDING", 3);
        todo.setDeleted(true);
        when(todoMapper.selectIncludingDeleted(90112L)).thenReturn(todo);

        assertThatThrownBy(() -> service.complete(90112L, completeRequest(3), 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.RECORD_DELETED.getCode());
    }

    @Test
    void completeWithStaleVersionReturns40902() {
        Todo todo = todo(90112L, "WORK", "PENDING", 3);
        when(todoMapper.selectIncludingDeleted(90112L)).thenReturn(todo);

        assertThatThrownBy(() -> service.complete(90112L, completeRequest(2), 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.VERSION_CONFLICT.getCode());
    }

    @Test
    void completeMissingTodoReturns40401() {
        when(todoMapper.selectIncludingDeleted(99999L)).thenReturn(null);

        assertThatThrownBy(() -> service.complete(99999L, completeRequest(1), 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void ignoreWithoutReasonReturns40004() {
        Todo todo = todo(90201L, "BUSINESS", "PENDING", 2);
        when(todoMapper.selectIncludingDeleted(90201L)).thenReturn(todo);

        assertThatThrownBy(() -> service.ignore(90201L, ignoreRequest("   ", 2), 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.REASON_EMPTY.getCode());
    }

    @Test
    void ignoreTooLongReasonReturns40001() {
        Todo todo = todo(90201L, "BUSINESS", "PENDING", 2);
        when(todoMapper.selectIncludingDeleted(90201L)).thenReturn(todo);

        assertThatThrownBy(() -> service.ignore(90201L, ignoreRequest("原".repeat(101), 2), 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    void completeSuccessWritesHandleLog() {
        Todo pending = todo(90112L, "WORK", "PENDING", 3);
        Todo done = todo(90112L, "WORK", "DONE", 4);
        when(todoMapper.selectIncludingDeleted(90112L)).thenReturn(pending, done);
        when(todoMapper.update(any(Todo.class), any())).thenReturn(1);

        TodoDetailVO result = service.complete(90112L, completeRequest(3), 10001L);

        assertThat(result).isNotNull();
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        org.mockito.Mockito.verify(handleLogMapper).insert(any(com.zhimu.server.biz.today.entity.TodoHandleLog.class));
        org.mockito.Mockito.verify(todoMapper).update(any(Todo.class), any());
    }

    private static TodoCompleteRequest completeRequest(int version) {
        TodoCompleteRequest request = new TodoCompleteRequest();
        request.setVersion(version);
        return request;
    }

    private static TodoIgnoreRequest ignoreRequest(String reason, int version) {
        TodoIgnoreRequest request = new TodoIgnoreRequest();
        request.setReason(reason);
        request.setVersion(version);
        return request;
    }

    private static Todo todo(long id, String category, String status, int version) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setFarmId(1001L);
        todo.setTodoType("DISINFECTION");
        todo.setCategory(category);
        todo.setTitle("2 号保育舍 · 消毒");
        todo.setStatus(status);
        todo.setPenId(2002L);
        todo.setPenName("2 号保育舍");
        todo.setReceiverScope("PEN_STAFF");
        todo.setDueDate(LocalDate.of(2026, 10, 18));
        todo.setTimeType("ALL_DAY");
        todo.setDueTime(LocalTime.of(17, 0));
        todo.setOverdueAt(LocalDateTime.of(2026, 10, 18, 17, 0));
        todo.setSortTime(LocalDateTime.of(2026, 10, 18, 0, 0));
        todo.setVersion(version);
        todo.setDeleted(false);
        todo.setRemindCount(3);
        return todo;
    }
}
