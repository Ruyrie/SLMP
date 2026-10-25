package com.zhimu.server.biz.today.service.impl;

import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.today.TodayTableInfo;
import com.zhimu.server.biz.today.convert.TodoConvert;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.TodoHandleLogMapper;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.query.TodoCalendarQuery;
import com.zhimu.server.biz.today.query.TodayTodoQuery;
import com.zhimu.server.biz.today.query.TodoWeeklyQuery;
import com.zhimu.server.biz.today.support.TodoScope;
import com.zhimu.server.biz.today.vo.TodoCalendarResult;
import com.zhimu.server.biz.today.vo.TodoItemVO;
import com.zhimu.server.biz.today.vo.TodoPageVO;
import com.zhimu.server.biz.today.vo.TodoWeeklyResult;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * API-T02–T06 查询口径（不连库）：今日列表与 tab_counts、日历状态计数、周视图固定 7 天与每天前 N 项。
 * 固定“今天”= 2026-10-19 周一 09:30。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoQueryServiceImplTest {

    @Mock private TodoMapper todoMapper;
    @Mock private TodoHandleLogMapper handleLogMapper;
    @Mock private TodoScope todoScope;
    @Mock private TodoConvert todoConvert;
    @Mock private FarmQueryService farmQueryService;

    private TodoServiceImpl service;
    private FarmContext.FarmMember owner;

    @BeforeEach
    void setUp() {
        TodayTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZoneId.of("Asia/Shanghai")));
        service = new TodoServiceImpl(todoMapper, handleLogMapper, todoScope, todoConvert, farmQueryService);
        owner = new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER, Set.of(),
                FarmStatus.NORMAL);
        FarmContext.set(owner);
        lenient().when(farmQueryService.listFarmsOfUser(anyLong())).thenReturn(List.of(
                FarmBriefVO.builder().farmId(1001L).name("东山猪场").build()));
        lenient().when(todoScope.canView(any(), any())).thenReturn(true);
        lenient().when(todoScope.resolve(anyLong(), any())).thenReturn(
                new TodoScope.Scope(1001L, false, List.of(1001L), Map.of(1001L, owner)));
        lenient().when(todoConvert.toItem(any(), any(), anyBoolean(), any(LocalDate.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    Todo todo = invocation.getArgument(0);
                    TodoItemVO item = new TodoItemVO();
                    item.setTodoId(todo.getId());
                    item.setDisplayStatus(todo.getStatus());
                    item.setTitle(todo.getTitle());
                    return item;
                });
        lenient().when(todoMapper.selectList(any())).thenReturn(List.of(
                todo(90101L, "FEEDING", "WORK", "PENDING", LocalDate.of(2026, 10, 18),
                        LocalTime.of(7, 0), LocalDateTime.of(2026, 10, 18, 17, 0), "已逾期饲喂"),
                todo(90105L, "FEED_LOW_STOCK", "WARNING", "PENDING", LocalDate.of(2026, 10, 19),
                        null, null, "库存不足"),
                todo(90108L, "FEEDING", "WORK", "PENDING", LocalDate.of(2026, 10, 19),
                        LocalTime.of(11, 30), LocalDateTime.of(2026, 10, 19, 13, 30), "午餐饲喂"),
                todo(90501L, "RECEIVABLE", "BUSINESS", "PENDING", LocalDate.of(2026, 10, 20),
                        null, LocalDateTime.of(2026, 10, 21, 0, 0), "明日待收款")));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        FarmContext.clear();
    }

    @Test
    void todayReturnsTabCountsAndOrder() {
        TodayTodoQuery query = new TodayTodoQuery();
        query.setFilter("ALL");
        query.setPage(1);
        query.setSize(20);

        TodoPageVO result = service.today(query, 10001L);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getTabCounts())
                .containsEntry("ALL", 2)
                .containsEntry("OVERDUE", 1)
                .containsEntry("WORK", 1)
                .containsEntry("BIZ_WARN", 1);
        assertThat(result.getList()).extracting(TodoItemVO::getTodoId)
                .containsExactly(90105L, 90108L);
    }

    @Test
    void staffFilterWorkReturns40001() {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2003L, 2004L), FarmStatus.NORMAL));
        TodayTodoQuery query = new TodayTodoQuery();
        query.setFilter("WORK");

        assertThatThrownBy(() -> service.today(query, 10003L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(40001);
    }

    @Test
    void calendarCountsStatusesByBusinessDay() {
        Todo calendarDone = todo(90107L, "FEEDING", "WORK", "DONE", LocalDate.of(2026, 10, 19),
                LocalTime.of(7, 0), LocalDateTime.of(2026, 10, 19, 9, 0), "早餐饲喂");
        calendarDone.setCompletedAt(LocalDateTime.of(2026, 10, 19, 7, 12));
        when(todoMapper.selectList(any())).thenReturn(List.of(
                todo(90105L, "FEED_LOW_STOCK", "WARNING", "PENDING", LocalDate.of(2026, 10, 19),
                        null, null, "库存不足"),
                calendarDone,
                todo(90108L, "FEEDING", "WORK", "PENDING", LocalDate.of(2026, 10, 20),
                        LocalTime.of(11, 30), LocalDateTime.of(2026, 10, 20, 13, 30), "午餐饲喂")));
        TodoCalendarQuery query = new TodoCalendarQuery();
        query.setStartDate(LocalDate.of(2026, 10, 19));
        query.setEndDate(LocalDate.of(2026, 10, 21));

        TodoCalendarResult result = service.calendar(query, 10001L);

        assertThat(result.getDays()).hasSize(3);
        assertThat(result.getDays().get(0).getDate()).isEqualTo("2026-10-19");
        assertThat(result.getDays().get(0).getIsToday()).isTrue();
        assertThat(result.getDays().get(0).getTotal()).isEqualTo(2);
        assertThat(result.getDays().get(0).getPendingCount()).isEqualTo(1);
        assertThat(result.getDays().get(0).getClosedCount()).isEqualTo(1);
        assertThat(result.getDays().get(1).getDate()).isEqualTo("2026-10-20");
        assertThat(result.getDays().get(1).getTotal()).isEqualTo(1);
        assertThat(result.getDays().get(2).getTotal()).isZero();
    }

    @Test
    void weeklyReturnsSevenDaysIncludingEmptyOnes() {
        TodoWeeklyQuery query = new TodoWeeklyQuery();
        query.setWeekStart(LocalDate.of(2026, 10, 19));
        query.setPerDay(1);

        TodoWeeklyResult result = service.weekly(query, 10001L);

        assertThat(result.getDays()).hasSize(7);
        assertThat(result.getDays().get(0)).satisfies(day -> {
            assertThat(day.getDate()).isEqualTo("2026-10-19");
            assertThat(day.getIsToday()).isTrue();
            assertThat(day.getTotal()).isEqualTo(2);
            assertThat(day.getItems()).hasSize(1);
        });
        assertThat(result.getDays().get(5)).satisfies(day -> {
            assertThat(day.getDate()).isEqualTo("2026-10-24");
            assertThat(day.getTotal()).isZero();
            assertThat(day.getItems()).isEmpty();
        });
    }

    private static Todo todo(long id, String type, String category, String status, LocalDate dueDate,
                             LocalTime dueTime, LocalDateTime overdueAt, String title) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setFarmId(1001L);
        todo.setTodoType(type);
        todo.setCategory(category);
        todo.setTitle(title);
        todo.setStatus(status);
        todo.setDueDate(dueDate);
        todo.setTimeType(dueTime == null ? "ALL_DAY" : "AT_TIME");
        todo.setDueTime(dueTime);
        todo.setOverdueAt(overdueAt);
        todo.setSortTime(overdueAt == null ? dueDate.atStartOfDay() : overdueAt);
        todo.setPenId(2003L);
        todo.setPenName("3 号育肥舍");
        todo.setVersion(1);
        todo.setDeleted(false);
        return todo;
    }

    @Test
    void penTasksUseRealSourceIdsAndIncludeOnlyVisibleWork() {
        Todo feed = todo(1L, "FEEDING", "WORK", "PENDING", LocalDate.of(2026, 10, 19),
                LocalTime.of(17, 0), LocalDateTime.of(2026, 10, 19, 19, 0), "3 号育肥舍 · 晚餐饲喂");
        feed.setBizId(70109L);
        feed.setBatchId(3001L);
        Todo warning = todo(2L, "FEED_LOW_STOCK", "WARNING", "PENDING", LocalDate.of(2026, 10, 19), null, null, "库存不足");
        Todo done = todo(3L, "FEEDING", "WORK", "DONE", LocalDate.of(2026, 10, 19), LocalTime.of(7, 0), null, "早餐");
        when(todoMapper.selectList(any())).thenReturn(List.of(feed, warning), List.of(done));

        var tasks = service.penTasksToday().get(2003L);

        assertThat(tasks.done()).isEqualTo(1);
        assertThat(tasks.total()).isEqualTo(2);
        assertThat(tasks.pending()).hasSize(1);
        assertThat(tasks.pending().get(0).taskId()).isEqualTo(70109L);
        assertThat(tasks.pending().get(0).title()).isEqualTo("晚餐饲喂");
        assertThat(tasks.pending().get(0).route()).isEqualTo("G02");
    }
}
