package com.zhimu.server.biz.feeding.service;

import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.admin.base.vo.BreedStandardsVO;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordItemMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.feeding.request.WeighCycleRequest;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.vo.WeighVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/** 称重周期修改口径（G23）与员工 40303。 */
@ExtendWith(MockitoExtension.class)
class WeighServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private WeighRecordMapper recordMapper;
    @Mock
    private WeighRecordItemMapper itemMapper;
    @Mock
    private BatchGrowthMapper growthMapper;
    @Mock
    private FeedLookup lookup;
    @Mock
    private StdQueryService stdQueryService;

    private WeighService service;

    @BeforeEach
    void setUp() {
        service = new WeighService(recordMapper, itemMapper, growthMapper, lookup, stdQueryService);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZONE));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
        BeijingTime.setClock(null);
    }

    @Test
    void updateCycleRecalculatesIntervalAndNextDate() {
        when(lookup.requireActiveBatch(3001L)).thenReturn(batch());
        when(growthMapper.selectOne(any())).thenReturn(growth());
        when(growthMapper.updateById(any(BatchGrowth.class))).thenReturn(1);
        when(stdQueryService.standards(1101L, null)).thenReturn(null);
        WeighCycleRequest request = new WeighCycleRequest();
        request.setPerMonth(3);

        WeighVO.Cycle cycle = service.updateCycle(3001L, request);

        assertThat(cycle.getPerMonth()).isEqualTo(3);
        assertThat(cycle.getIntervalDays()).isEqualTo(9);
        assertThat(cycle.getCustomized()).isTrue();
        assertThat(cycle.getSuggestedPerMonth()).isEqualTo(2);
        assertThat(cycle.getNextWeighDate()).isEqualTo("2026-10-28");
    }

    @Test
    void compareOptionsSkipsBatchesWithoutWeighAndSortsByLatest() {
        when(lookup.requireOwnedBatch(3001L)).thenReturn(batch());
        when(lookup.farmBatches(true)).thenReturn(List.of(
                row(3002L, "ZM2610-019", "ACTIVE"),
                row(3003L, "ZM2610-045", "ACTIVE"),
                row(3004L, "ZM2609-038", "ACTIVE"),
                row(3009L, "ZM2609-012", "ENDED")));
        when(lookup.batchPenIds(anyLong())).thenReturn(List.of(2004L));
        when(lookup.staffCanSeePens(any())).thenReturn(true);
        when(lookup.penNames(any())).thenReturn("4 号育肥舍");
        // 3002 最近 10-12、3004 最近 10-10；3003/3009 没有称重记录
        when(recordMapper.selectList(any())).thenReturn(
                List.of(weigh(3310L, 3002L, "2026-10-12", 75, "44.90")),
                List.of(),
                List.of(weigh(3304L, 3004L, "2026-10-10", 54, "23.00")),
                List.of());

        List<WeighVO.CompareOption> options = service.compareOptions(3001L);

        assertThat(options).extracting(WeighVO.CompareOption::getBatchId)
                .containsExactly(3002L, 3004L);
        assertThat(options.get(0).getLatestAvgWeight()).isEqualByComparingTo("44.90");
        assertThat(options.get(1).getLatestDayAge()).isEqualTo(54);
    }

    @Test
    void staffChangingCycleGets40303() {
        doThrow(FeedErrors.roleForbidden("你没有修改称重周期的权限")).when(lookup).requireManager(anyString());
        WeighCycleRequest request = new WeighCycleRequest();
        request.setPerMonth(3);

        assertThatThrownBy(() -> service.updateCycle(3001L, request))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(40303);
    }

    @Test
    void curveComparesMissingSnapshotAtWeighingAgeWithoutChangingHistory() {
        WeighRecord record = weigh(3330L, 3001L, "2026-10-19", 96, "60.00");
        stubCurve(record, currentStandards(90, "62.0", 100, "73.0"));

        WeighVO.Curve curve = service.curve(3001L, null);

        assertThat(curve.getMetrics().getStdWeight()).isEqualByComparingTo("68.6");
        assertThat(curve.getMetrics().getDeviationRatio()).isEqualByComparingTo("-0.1254");
        assertThat(curve.getMetrics().getAllowedDeviation()).isEqualByComparingTo("0.10");
        assertThat(curve.getMetrics().getComparisonSource()).isEqualTo("CURRENT_STANDARD");
        assertThat(curve.getActualPoints().get(0).getStdWeight()).isEqualByComparingTo("68.6");
        assertThat(record.getStdWeight()).isNull();
        assertThat(record.getDeviationRatio()).isNull();
        verify(recordMapper, never()).updateById(any(WeighRecord.class));
        verify(growthMapper, never()).updateById(any(BatchGrowth.class));
    }

    @Test
    void curveKeepsExistingSnapshotWhenCurrentStandardDiffers() {
        WeighRecord record = weigh(3330L, 3001L, "2026-10-19", 96, "60.00");
        record.setStdWeight(new BigDecimal("65.00"));
        record.setDeviationRatio(new BigDecimal("-0.0769"));
        record.setAllowedDeviation(new BigDecimal("0.20"));
        stubCurve(record, currentStandards(90, "62.0", 100, "73.0"));

        WeighVO.Metrics metrics = service.curve(3001L, null).getMetrics();

        assertThat(metrics.getStdWeight()).isEqualByComparingTo("65.00");
        assertThat(metrics.getDeviationRatio()).isEqualByComparingTo("-0.0769");
        assertThat(metrics.getAllowedDeviation()).isEqualByComparingTo("0.20");
        assertThat(metrics.getComparisonSource()).isEqualTo("RECORDED");
    }

    @Test
    void missingSnapshotWithoutCurrentStandardStaysUnavailable() {
        stubCurve(weigh(3330L, 3001L, "2026-10-19", 96, "60.00"), null);

        WeighVO.Curve curve = service.curve(3001L, null);

        assertThat(curve.getStdAvailable()).isFalse();
        assertThat(curve.getMetrics().getStdWeight()).isNull();
        assertThat(curve.getMetrics().getDeviationRatio()).isNull();
        assertThat(curve.getMetrics().getComparisonSource()).isNull();
    }

    @Test
    void missingSnapshotOutsideCurrentStandardAgeRangeIsNotExtrapolated() {
        stubCurve(weigh(3330L, 3001L, "2026-10-19", 96, "60.00"), currentStandards(100, "73.0", 110, "84.0"));

        WeighVO.Metrics metrics = service.curve(3001L, null).getMetrics();

        assertThat(metrics.getStdWeight()).isNull();
        assertThat(metrics.getDeviationRatio()).isNull();
        assertThat(metrics.getComparisonSource()).isNull();
    }

    @Test
    void recordedSnapshotRemainsAvailableWithoutCurrentStandard() {
        WeighRecord record = weigh(3330L, 3001L, "2026-10-19", 96, "60.00");
        record.setStdWeight(new BigDecimal("65.00"));
        record.setDeviationRatio(new BigDecimal("-0.0769"));
        stubCurve(record, null);

        WeighVO.Metrics metrics = service.curve(3001L, null).getMetrics();

        assertThat(metrics.getStdWeight()).isEqualByComparingTo("65.00");
        assertThat(metrics.getDeviationRatio()).isEqualByComparingTo("-0.0769");
        assertThat(metrics.getComparisonSource()).isEqualTo("RECORDED");
    }

    private void stubCurve(WeighRecord record, BreedStandardsVO standards) {
        when(lookup.requireOwnedBatch(3001L)).thenReturn(batch());
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        when(growthMapper.selectOne(any())).thenReturn(growth());
        when(stdQueryService.standards(1101L, null)).thenReturn(standards);
    }

    private static BreedStandardsVO currentStandards(int firstDay, String firstWeight, int lastDay, String lastWeight) {
        BreedStandardsVO result = new BreedStandardsVO();
        BreedStandardsVO.WeightStandardVO weight = new BreedStandardsVO.WeightStandardVO();
        weight.setToleranceRate(new BigDecimal("0.10"));
        BreedStandardsVO.WeightPointVO first = new BreedStandardsVO.WeightPointVO();
        first.setDayAge(firstDay);
        first.setStdWeight(new BigDecimal(firstWeight));
        BreedStandardsVO.WeightPointVO last = new BreedStandardsVO.WeightPointVO();
        last.setDayAge(lastDay);
        last.setStdWeight(new BigDecimal(lastWeight));
        weight.setPoints(List.of(first, last));
        result.setWeight(weight);
        return result;
    }

    private static BatchInfo batch() {
        return new BatchInfo(1001L, 3001L, "ZM2610-031", 1101L, 101L,
                LocalDate.of(2026, 10, 1), 200, "ACTIVE", List.of(2003L), 78, 205, 5, 0, 2003L);
    }

    private static com.zhimu.server.biz.feeding.support.FeedBatchRow row(Long batchId, String batchNo,
                                                                          String status) {
        var row = new com.zhimu.server.biz.feeding.support.FeedBatchRow();
        row.setBatchId(batchId);
        row.setBatchNo(batchNo);
        row.setBreedId(1101L);
        row.setCategoryId(101L);
        row.setEntryDate(LocalDate.of(2026, 10, 1));
        row.setEntryAgeDays(68);
        row.setCurrentCount(150);
        row.setStatus(status);
        return row;
    }

    private static com.zhimu.server.biz.feeding.entity.WeighRecord weigh(Long id, Long batchId, String date,
                                                                         int dayAge, String avgWeight) {
        var record = new com.zhimu.server.biz.feeding.entity.WeighRecord();
        record.setId(id);
        record.setFarmId(1001L);
        record.setBatchId(batchId);
        record.setWeighDate(LocalDate.parse(date));
        record.setDayAge(dayAge);
        record.setAvgWeight(new java.math.BigDecimal(avgWeight));
        return record;
    }

    private static BatchGrowth growth() {
        BatchGrowth growth = new BatchGrowth();
        growth.setId(601L);
        growth.setFarmId(1001L);
        growth.setBatchId(3001L);
        growth.setWeighCyclePerMonth(2);
        growth.setCycleCustomized(false);
        growth.setLastWeighDate(LocalDate.of(2026, 10, 19));
        growth.setLastAvgWeight(new java.math.BigDecimal("60.00"));
        growth.setGrowthStatus("SLOW");
        growth.setNextWeighDate(LocalDate.of(2026, 11, 2));
        growth.setVersion(0);
        return growth;
    }
}
