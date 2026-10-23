package com.zhimu.server.biz.feeding.service;

import com.zhimu.server.biz.feeding.FeedingTableInfo;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.support.FeedBatchRow;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 模块06 待办来源：只映射当天未完成任务，字段与幂等键正确，补登窗口结束后不再生成。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedTodoSourceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 19);

    @Mock private FeedTaskMapper taskMapper;
    @Mock private FeedLookup lookup;
    @Mock private FeedStockPort feedStockPort;
    @Mock private TodoSourceWriteService todoWriter;

    private FeedTodoSource source;

    @BeforeAll
    static void initTableInfo() {
        FeedingTableInfo.ensure();
    }

    @BeforeEach
    void setUp() {
        source = new FeedTodoSource(taskMapper, lookup, feedStockPort, todoWriter);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-18T16:05:00Z"), ZONE));
        when(lookup.pens(anyCollection())).thenReturn(Map.of(2003L,
                new PenInfo(2003L, 1001L, "P3", "3 号育肥舍", "FATTENING", "育肥舍", 300, "ENABLED")));
        UserBriefVO assignee = new UserBriefVO();
        assignee.setUserId(10003L);
        assignee.setName("李明");
        when(lookup.memberBriefs(eq(1001L), anyCollection())).thenReturn(Map.of(10003L, assignee));
        FeedBatchRow batch = new FeedBatchRow();
        batch.setBatchId(3001L);
        batch.setBatchNo("ZM2610-031");
        when(lookup.batchBasics(3001L)).thenReturn(batch);
        when(feedStockPort.getFeed(1001L, 5101L)).thenReturn(new FeedStockPort.FeedBrief(
                5101L, "育肥全价料", "KG", new BigDecimal("500.0"), 3, false, true, null));
        when(todoWriter.upsertFromSource(eq(1001L), any())).thenReturn(List.of(95001L));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void mapsOnlyUnfinishedTasksOfTheDay() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(70107L, "PENDING"), task(70119L, "DONE")));

        List<Long> ids = source.generate(context());

        assertThat(ids).containsExactly(95001L);
        ArgumentCaptor<List<TodoSource.TodoDraft>> captor = draftsCaptor();
        verify(todoWriter).upsertFromSource(eq(1001L), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        TodoSource.TodoDraft draft = captor.getValue().get(0);
        assertThat(draft.getTodoType()).isEqualTo("FEEDING");
        assertThat(draft.getCategory()).isEqualTo("WORK");
        assertThat(draft.getPenId()).isEqualTo(2003L);
        assertThat(draft.getPenName()).isEqualTo("3 号育肥舍");
        assertThat(draft.getBatchId()).isEqualTo(3001L);
        assertThat(draft.getBatchNo()).isEqualTo("ZM2610-031");
        assertThat(draft.getBizType()).isEqualTo("FEED_TASK");
        assertThat(draft.getBizId()).isEqualTo(70107L);
        assertThat(draft.getDedupKey()).isEqualTo("FEED_TASK:70107");
        assertThat(draft.getTitle()).isEqualTo("3 号育肥舍 · 早餐饲喂");
        assertThat(draft.getContentText()).isEqualTo("育肥全价料 · 计划 160 千克");
        assertThat(draft.getReceiverScope()).isEqualTo("PEN_STAFF");
        assertThat(draft.getAssigneeId()).isEqualTo(10003L);
        assertThat(draft.getAssigneeName()).isEqualTo("李明");
        assertThat(draft.getDueDate()).isEqualTo(DATE);
        assertThat(draft.getTimeType()).isEqualTo("AT_TIME");
        assertThat(draft.getDueTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(draft.getOverdueAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 9, 0));
    }

    @Test
    void stopsGeneratingAfterBackfillWindow() {
        FeedTask task = task(70107L, "PENDING");
        task.setBackfillEndAt(LocalDateTime.of(2026, 10, 21, 0, 0));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-20T16:10:00Z"), ZONE));

        List<Long> ids = source.generate(context());

        assertThat(ids).isEmpty();
        verify(todoWriter, never()).upsertFromSource(anyLong(), any());
    }

    @Test
    void returnsEmptyWithoutTasks() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        assertThat(source.generate(context())).isEmpty();
        verify(todoWriter, never()).upsertFromSource(anyLong(), any());
    }

    private static TodoSource.TodoSourceContext context() {
        return TodoSource.TodoSourceContext.builder().farmId(1001L).date(DATE).build();
    }

    private static FeedTask task(Long id, String status) {
        FeedTask task = new FeedTask();
        task.setId(id);
        task.setFarmId(1001L);
        task.setBatchId(3001L);
        task.setPenId(2003L);
        task.setTaskDate(DATE);
        task.setMealNo(1);
        task.setMealCode("BREAKFAST");
        task.setMealName("早餐");
        task.setFeedTime(LocalTime.of(7, 0));
        task.setFeedAt(LocalDateTime.of(2026, 10, 19, 7, 0));
        task.setDeadlineAt(LocalDateTime.of(2026, 10, 19, 9, 0));
        task.setBackfillEndAt(LocalDateTime.of(2026, 10, 21, 0, 0));
        task.setFeedId(5101L);
        task.setHeadCount(200);
        task.setDailyQtyPerHead(new BigDecimal("2.400"));
        task.setMealsPerDay(3);
        task.setPlanQty(new BigDecimal("160.00"));
        task.setAssigneeId(10003L);
        task.setStatus(status);
        return task;
    }

    private static ArgumentCaptor<List<TodoSource.TodoDraft>> draftsCaptor() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TodoSource.TodoDraft>> captor = ArgumentCaptor.forClass(List.class);
        return captor;
    }
}
