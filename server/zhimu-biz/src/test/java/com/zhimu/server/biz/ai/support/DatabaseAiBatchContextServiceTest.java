package com.zhimu.server.biz.ai.support;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.biz.feeding.api.BatchGrowthQueryService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseAiBatchContextServiceTest {
    private final BatchQueryService batches = mock(BatchQueryService.class);
    private final PenQueryService pens = mock(PenQueryService.class);
    private final BaseDataQueryService categories = mock(BaseDataQueryService.class);
    private final BreedQueryService breeds = mock(BreedQueryService.class);
    private final BatchGrowthQueryService growth = mock(BatchGrowthQueryService.class);
    private final DatabaseAiBatchContextService service =
            new DatabaseAiBatchContextService(batches, pens, categories, breeds, growth);

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(LocalDate.of(2026, 10, 27).atStartOfDay(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(pens.getPens(any())).thenReturn(Map.of(
                2306L, pen(2306L, "A01", "1号育肥舍"),
                2307L, pen(2307L, "A02", "2号育肥舍")));
        when(categories.getCategory(101L)).thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD", "头", false, true));
        when(breeds.info(1001L)).thenReturn(new BreedQueryService.BreedInfo(1001L, 101L, "杜长大", null, null, null, null, true));
        when(growth.latestWeights(eq(60601L), any())).thenReturn(Map.of(3276L,
                new BatchGrowthQueryService.LatestWeight(new BigDecimal("60.0"), LocalDate.of(2026, 10, 26))));
    }

    @AfterEach
    void resetClock() { BeijingTime.setClock(null); }

    @Test
    void newFarmGetsRealBatchesLiveStockAgeAndWeight() {
        when(batches.listActive(60601L)).thenReturn(List.of(batch(60601L, 3276L, "ACTIVE", 119, List.of(2306L, 2307L))));
        var result = service.listLinkable(60601L, false, null);
        assertThat(result).hasSize(1);
        var batch = result.getFirst();
        assertThat(batch.getBatchId()).isEqualTo(3276L);
        assertThat(batch.getCurrentCount()).isEqualTo(119);
        assertThat(batch.getDayAge()).isEqualTo(95);
        assertThat(batch.getBreedName()).isEqualTo("杜长大");
        assertThat(batch.getPenName()).isEqualTo("1号育肥舍、2号育肥舍");
        assertThat(batch.getLatestAvgWeight()).isEqualByComparingTo("60.0");
        assertThat(batch.getLatestWeighDate()).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void staffCanLinkSplitBatchThroughAnyAssignedPenWithoutExposingOtherPenNames() {
        when(batches.listActive(60601L)).thenReturn(List.of(
                batch(60601L, 3276L, "ACTIVE", 119, List.of(2306L, 2307L)),
                batch(60601L, 3277L, "ACTIVE", 50, List.of(2306L))));
        var result = service.listLinkable(60601L, true, List.of(2307L));
        assertThat(result).extracting(LinkedBatchVO::getBatchId).containsExactly(3276L);
        assertThat(result.getFirst().getPenId()).isEqualTo(2307L);
        assertThat(result.getFirst().getPenName()).isEqualTo("2号育肥舍");
        verify(growth).latestWeights(60601L, List.of(3276L));
    }

    @Test
    void staffWithoutAssignedPensHasNoLinkableBatches() {
        when(batches.listActive(60601L)).thenReturn(List.of(batch(60601L, 3276L, "ACTIVE", 119, List.of(2306L))));
        assertThat(service.listLinkable(60601L, true, null)).isEmpty();
        verifyNoInteractions(pens, growth);
    }

    @Test
    void filtersOtherFarmEndedAndEmptyBatchesAndSortsNewestNumberFirst() {
        when(batches.listActive(60601L)).thenReturn(List.of(
                batch(60601L, 3276L, "ACTIVE", 119, List.of(2306L)),
                batch(60601L, 3277L, "ACTIVE", 50, List.of(2306L)),
                batch(60602L, 3279L, "ACTIVE", 300, List.of(2306L)),
                batch(60601L, 3278L, "ENDED", 0, List.of()),
                batch(60601L, 3280L, "ACTIVE", 0, List.of(2306L))));
        assertThat(service.listLinkable(60601L, false, null)).extracting(LinkedBatchVO::getBatchId)
                .containsExactly(3277L, 3276L);
    }

    @Test
    void resolveRejectsCrossFarmEndedAndUnassignedBatchBeforeLookingUpOtherData() {
        when(batches.requireBatch(3276L)).thenReturn(batch(60602L, 3276L, "ACTIVE", 119, List.of(2306L)));
        assertThat(service.resolve(60601L, 3276L, false, null)).isNull();
        when(batches.requireBatch(3276L)).thenReturn(batch(60601L, 3276L, "ENDED", 0, List.of()));
        assertThat(service.resolve(60601L, 3276L, false, null)).isNull();
        when(batches.requireBatch(3276L)).thenReturn(batch(60601L, 3276L, "ACTIVE", 119, List.of(2306L)));
        assertThat(service.resolve(60601L, 3276L, true, List.of(2307L))).isNull();
        verifyNoInteractions(pens, growth);
    }

    @Test
    void resolveUsesUpdatedCountInsteadOfStaticOrCachedBatch() {
        when(batches.requireBatch(3276L)).thenReturn(
                batch(60601L, 3276L, "ACTIVE", 119, List.of(2306L)),
                batch(60601L, 3276L, "ACTIVE", 109, List.of(2306L)));
        assertThat(service.resolve(60601L, 3276L, false, null).getCurrentCount()).isEqualTo(119);
        assertThat(service.resolve(60601L, 3276L, false, null).getCurrentCount()).isEqualTo(109);
    }

    @Test
    void missingBatchIsEmptyButDatabaseFailureIsNotSilentlyHidden() {
        when(batches.requireBatch(3276L)).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        assertThat(service.resolve(60601L, 3276L, false, null)).isNull();
        doThrow(new BizException(ErrorCode.SERVICE_UNAVAILABLE)).when(batches).requireBatch(3276L);
        assertThatThrownBy(() -> service.resolve(60601L, 3276L, false, null)).isInstanceOf(BizException.class);
    }

    @Test
    void poultryUsesConfiguredUnitAndMissingWeightStaysUnknown() {
        when(batches.requireBatch(3276L)).thenReturn(batch(60601L, 3276L, "ACTIVE", 300, List.of(2306L)));
        when(categories.getCategory(101L)).thenReturn(new CategoryInfo(101L, "肉鸡", "POULTRY", "禽", "BIRD", "只", false, true));
        when(growth.latestWeights(eq(60601L), any())).thenReturn(Map.of());
        var result = service.resolve(60601L, 3276L, false, null);
        assertThat(result.getCountUnitText()).isEqualTo("只");
        assertThat(result.getLatestAvgWeight()).isNull();
    }

    @Test
    void snapshotsRemainCompatibleWithOldSessionsAndRoundTripNewWeight() {
        var old = service.fromSnapshot(AiFixtures.session(1021L, 10001L).getBatchSnapshotJson());
        assertThat(old.getBatchNo()).isEqualTo("ZM2610-031");
        assertThat(old.getLatestAvgWeight()).isNull();
        old.setLatestAvgWeight(new BigDecimal("60.0"));
        old.setLatestWeighDate(LocalDate.of(2026, 10, 26));
        var restored = service.fromSnapshot(service.toSnapshot(old));
        assertThat(restored).isEqualTo(old);
    }

    private static BatchInfo batch(long farm, long id, String status, int count, List<Long> pens) {
        return new BatchInfo(farm, id, "ZM2610-" + id, 1001L, 101L, LocalDate.of(2026, 9, 22),
                count, status, pens, 60, 120, 1, 0, 2306L);
    }

    private static PenInfo pen(long id, String code, String name) {
        return new PenInfo(id, 60601L, code, name, "FATTENING", "育肥舍", 200, "ENABLED");
    }
}
