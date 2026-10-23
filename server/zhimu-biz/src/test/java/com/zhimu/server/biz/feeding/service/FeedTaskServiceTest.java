package com.zhimu.server.biz.feeding.service;

import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMapper;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMealMapper;
import com.zhimu.server.biz.feeding.mapper.FeedPlanStageMapper;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.vo.FeedTaskVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** G01 今日指标与员工栏舍范围（40304）。 */
@ExtendWith(MockitoExtension.class)
class FeedTaskServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private FeedTaskMapper taskMapper;
    @Mock
    private FeedPlanMapper planMapper;
    @Mock
    private FeedPlanStageMapper stageMapper;
    @Mock
    private FeedPlanMealMapper mealMapper;
    @Mock
    private FeedRecordMapper recordMapper;
    @Mock
    private FeedLookup lookup;

    private FeedTaskService service;

    @BeforeAll
    static void initTableInfo() {
        com.zhimu.server.biz.feeding.FeedingTableInfo.init();
    }

    @BeforeEach
    void setUp() {
        service = new FeedTaskService(taskMapper, planMapper, stageMapper, mealMapper, recordMapper, lookup);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZONE));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
        BeijingTime.setClock(null);
    }

    @Test
    void todaySummaryCountsDoneOverdueAndActualQty() {
        when(lookup.context()).thenReturn(FarmContext.get());
        when(lookup.penName(anyLong())).thenReturn("3 号育肥舍");
        when(lookup.feed(4001L)).thenReturn(new FeedStockPort.FeedBrief(4001L, "育肥全价料", "KG",
                new BigDecimal("500.0"), 1, true, true, null));
        when(lookup.feed(4002L)).thenReturn(new FeedStockPort.FeedBrief(4002L, "保育料", "KG",
                new BigDecimal("1200.0"), 5, false, true, null));
        FeedTask done = task(70107L, 3001L, 2003L, 1, "早餐", "07:00", 160, "DONE", 4001L);
        FeedTask overdue = task(70101L, 3003L, 2001L, 1, "早餐", "07:00", 80, "PENDING", 4002L);
        FeedTask pending = task(70108L, 3001L, 2003L, 2, "午餐", "11:30", 160, "PENDING", 4001L);
        when(taskMapper.selectList(any())).thenReturn(List.of(done, overdue, pending));
        FeedRecord record = new FeedRecord();
        record.setId(7106L);
        record.setTaskId(70107L);
        record.setActualQty(new BigDecimal("158.00"));
        record.setStatus("NORMAL");
        when(recordMapper.selectList(any())).thenReturn(List.of(record));

        FeedTaskVO.TodaySummary summary = service.todaySummary();

        assertThat(summary.getTaskTotal()).isEqualTo(3);
        assertThat(summary.getDoneCount()).isEqualTo(1);
        assertThat(summary.getOverdueCount()).isEqualTo(1);
        assertThat(summary.getStatusCounts().getPending()).isEqualTo(1);
        assertThat(summary.getPlanTotalQty()).isEqualByComparingTo("400.00");
        assertThat(summary.getActualTotalQty()).isEqualByComparingTo("158.00");
        assertThat(summary.getFirstOverdue().getMealName()).isEqualTo("早餐");
        assertThat(summary.getFirstOverdue().getOverdueMinutes()).isEqualTo(150);
        assertThat(summary.getPenOptions()).hasSize(2);
        assertThat(summary.getCanManage()).isTrue();
        assertThat(summary.getEmptyReason()).isNull();
    }

    @Test
    void staffOutsidePenGets40304() {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2003L), FarmStatus.NORMAL));
        when(lookup.context()).thenReturn(FarmContext.get());
        FeedTask task = task(70101L, 3003L, 2001L, 1, "早餐", "07:00", 80, "PENDING", 4002L);
        when(taskMapper.selectById(70101L)).thenReturn(task);
        doThrow(FeedErrors.penForbidden()).when(lookup).checkPenScope(2001L);

        assertThatThrownBy(() -> service.form(70101L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(40304);
    }

    private static FeedTask task(Long id, Long batchId, Long penId, int mealNo, String mealName, String time,
                                 int planQty, String status, Long feedId) {
        FeedTask task = new FeedTask();
        task.setId(id);
        task.setFarmId(1001L);
        task.setBatchId(batchId);
        task.setPenId(penId);
        task.setMealNo(mealNo);
        task.setMealCode("BREAKFAST");
        task.setMealName(mealName);
        LocalTime feedTime = LocalTime.parse(time);
        LocalDateTime feedAt = LocalDateTime.of(2026, 10, 19, feedTime);
        task.setFeedTime(feedTime);
        task.setTaskDate(LocalDate.of(2026, 10, 19));
        task.setFeedAt(feedAt);
        task.setDeadlineAt(feedAt.plusHours(2));
        task.setBackfillEndAt(LocalDateTime.of(2026, 10, 21, 0, 0));
        task.setPlanQty(new BigDecimal(planQty).setScale(2));
        task.setHeadCount(200);
        task.setDailyQtyPerHead(new BigDecimal("2.400"));
        task.setMealsPerDay(3);
        task.setStatus(status);
        task.setFeedId(feedId);
        task.setVersion(0);
        return task;
    }
}
