package com.zhimu.server.biz.feeding.service;

import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMapper;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.request.FeedExecQuery;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.vo.FeedExecVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** G14 执行汇总口径与员工 40303。 */
@ExtendWith(MockitoExtension.class)
class FeedExecutionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private FeedTaskMapper taskMapper;
    @Mock
    private FeedRecordMapper recordMapper;
    @Mock
    private FeedPlanMapper planMapper;
    @Mock
    private FeedLookup lookup;

    private FeedExecutionService service;

    @BeforeAll
    static void initTableInfo() {
        com.zhimu.server.biz.feeding.FeedingTableInfo.init();
    }

    @BeforeEach
    void setUp() {
        com.zhimu.server.biz.feeding.FeedingTableInfo.ensure();
        service = new FeedExecutionService(taskMapper, recordMapper, planMapper, lookup);
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
    void summaryComputesExecutionRateDeviationAndAbnormal() {
        when(lookup.requireOwnedBatch(3001L)).thenReturn(batch());
        when(planMapper.selectOne(any())).thenReturn(null);
        FeedTask done1 = task(70107L, "DONE", 160);
        FeedTask done2 = task(70108L, "DONE", 160);
        FeedTask pending = task(70109L, "PENDING", 160);
        when(taskMapper.selectList(any())).thenReturn(List.of(done1, done2, pending));
        FeedRecord record1 = record(7106L, 70107L, "150.00", "UNDER", "-0.0625");
        FeedRecord record2 = record(7107L, 70108L, "158.00", "NONE", "-0.0125");
        when(recordMapper.selectList(any())).thenReturn(List.of(record1, record2));

        FeedExecQuery query = new FeedExecQuery();
        query.setBatchId(3001L);
        query.setRange("LAST_7");
        FeedExecVO.Summary summary = service.summary(query);

        assertThat(summary.getStartDate()).isEqualTo("2026-10-12");
        assertThat(summary.getEndDate()).isEqualTo("2026-10-18");
        assertThat(summary.getPlannedMeals()).isEqualTo(3);
        assertThat(summary.getDoneMeals()).isEqualTo(2);
        assertThat(summary.getExecRate()).isEqualByComparingTo("0.6667");
        assertThat(summary.getActualQty()).isEqualByComparingTo("308.00");
        assertThat(summary.getPlanQty()).isEqualByComparingTo("480.00");
        assertThat(summary.getDeviationRatio()).isEqualByComparingTo("-0.0375");
        assertThat(summary.getDeviationDirection()).isEqualTo("LESS");
        assertThat(summary.getAbnormalCount()).isEqualTo(1);
        assertThat(summary.getUnderCount()).isEqualTo(1);
        assertThat(summary.getAbnormalSameRatio()).isEqualByComparingTo("-0.0625");
        assertThat(summary.getDaily()).hasSize(7);
        assertThat(summary.getHasData()).isTrue();
        assertThat(summary.getBatchCount()).isEqualTo(1);
    }

    @Test
    void staffCallingExecutionGets40303() {
        doThrow(FeedErrors.execForbidden()).when(lookup).requireManager(anyString());
        FeedExecQuery query = new FeedExecQuery();
        query.setBatchId(3001L);
        query.setRange("LAST_7");

        assertThatThrownBy(() -> service.summary(query))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(40303);
    }

    private static BatchInfo batch() {
        return new BatchInfo(1001L, 3001L, "ZM2610-031", 1101L, 101L,
                LocalDate.of(2026, 10, 1), 200, "ACTIVE", List.of(2003L), 78, 205, 5, 0, 2003L);
    }

    private static FeedTask task(Long id, String status, int planQty) {
        FeedTask task = new FeedTask();
        task.setId(id);
        task.setFarmId(1001L);
        task.setBatchId(3001L);
        task.setPenId(2003L);
        task.setTaskDate(LocalDate.of(2026, 10, 18));
        task.setMealNo(1);
        task.setMealName("早餐");
        task.setPlanQty(new BigDecimal(planQty).setScale(2));
        task.setStatus(status);
        return task;
    }

    private static FeedRecord record(Long id, Long taskId, String actual, String abnormal, String deviation) {
        FeedRecord record = new FeedRecord();
        record.setId(id);
        record.setTaskId(taskId);
        record.setBatchId(3001L);
        record.setFeedDate(LocalDate.of(2026, 10, 18));
        record.setMealNo(1);
        record.setMealName("早餐");
        record.setActualQty(new BigDecimal(actual));
        record.setPlanQty(new BigDecimal("160.00"));
        record.setAbnormalType(abnormal);
        record.setDeviationRatio(new BigDecimal(deviation));
        record.setStatus("NORMAL");
        return record;
    }
}
