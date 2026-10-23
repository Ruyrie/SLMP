package com.zhimu.server.biz.herd.service;

import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.service.impl.HerdHomeMetricProvider;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.today.api.HomeMetricProvider;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** 模块04 向首页提供的指标：870 头 / 4 个在养批次 / 占用率 0.5438，员工栏舍批次与容量。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HerdHomeMetricProviderTest {

    @Mock private HerdSupport support;

    private HerdHomeMetricProvider provider;

    @BeforeEach
    void setUp() {
        provider = new HerdHomeMetricProvider(support);
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2003L, 2004L), FarmStatus.NORMAL));
        lenient().when(support.activeBatches(1001L)).thenReturn(List.of(
                batch(3001L, 200), batch(3002L, 150), batch(3003L, 320), batch(3004L, 200)));
        lenient().when(support.pensOfFarm(1001L)).thenReturn(List.of(
                pen(2001L, 360, 320, 101L), pen(2002L, 400, 200, 101L), pen(2003L, 220, 200, 101L),
                pen(2004L, 220, 150, 101L), pen(2005L, 400, 0, 101L), pen(2006L, 60, 0, 102L)));
        lenient().when(support.category(101L)).thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD",
                "头", false, true));
        lenient().when(support.category(102L)).thenReturn(new CategoryInfo(102L, "种猪", "PIG", "猪", "HEAD",
                "头", true, true));
        lenient().when(support.farmUnit(1001L)).thenReturn("HEAD");
        lenient().when(support.unitText("HEAD")).thenReturn("头");
        lenient().when(support.pensVisibleTo(any())).thenReturn(List.of(
                pen(2003L, 220, 200, 101L), pen(2004L, 220, 150, 101L)));
        lenient().when(support.positivePensOfBatch(3001L)).thenReturn(List.of(batchPen(3001L, 2003L, 200)));
        lenient().when(support.positivePensOfBatch(3002L)).thenReturn(List.of(batchPen(3002L, 2004L, 150)));
        lenient().when(support.positivePensOfBatch(3003L)).thenReturn(List.of(batchPen(3003L, 2001L, 320)));
        lenient().when(support.positivePensOfBatch(3004L)).thenReturn(List.of(batchPen(3004L, 2002L, 200)));
        lenient().when(support.breedTitle(any())).thenReturn("杜长大育肥猪");
        lenient().when(support.dayAge(any())).thenReturn(96);
        lenient().when(support.stageName(any())).thenReturn("育肥期");
        lenient().when(support.penTypeText("FATTENING")).thenReturn("育肥舍");
        lenient().when(support.pensByIds(any())).thenReturn(List.of(pen(2003L, 220, 200, 101L)));
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
    }

    @Test
    void farmMetricsMatchAppHomeMock() {
        assertThat(provider.stockCount(1001L)).isEqualTo(870);
        assertThat(provider.batchCount(1001L)).isEqualTo(4);
        assertThat(provider.penOccupancyRate(1001L)).isEqualByComparingTo(new BigDecimal("0.5438"));
        assertThat(provider.countUnit(1001L)).isEqualTo("HEAD");
        assertThat(provider.countUnitText(1001L)).isEqualTo("头");
    }

    @Test
    void staffPensCarryCapacityAndInPenBatches() {
        List<HomeMetricProvider.PenMetric> pens = provider.pens(FarmContext.get());

        assertThat(pens).hasSize(2);
        assertThat(pens.get(0).penId()).isEqualTo(2003L);
        assertThat(pens.get(0).penName()).isEqualTo("3 号育肥舍");
        assertThat(pens.get(0).capacity()).isEqualTo(220);
        assertThat(pens.get(0).batches()).hasSize(1);
        assertThat(pens.get(0).batches().get(0).batchId()).isEqualTo(3001L);
        assertThat(pens.get(0).batches().get(0).headCount()).isEqualTo(200);
        assertThat(pens.get(0).batches().get(0).breedText()).isEqualTo("杜长大育肥猪");
        assertThat(pens.get(1).batches()).hasSize(1);
        assertThat(pens.get(1).batches().get(0).batchId()).isEqualTo(3002L);
    }

    private static Pen pen(Long id, int capacity, int stock, Long categoryId) {
        Pen pen = new Pen();
        pen.setId(id);
        pen.setFarmId(1001L);
        pen.setPenCode("P" + id);
        pen.setPenName(id == 2003L ? "3 号育肥舍" : "P" + id);
        pen.setPenType("FATTENING");
        pen.setCategoryId(categoryId);
        pen.setCapacity(capacity);
        pen.setStockCount(stock);
        pen.setStatus("ENABLED");
        pen.setVersion(4);
        return pen;
    }

    private static Batch batch(Long id, int current) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-0" + id);
        batch.setCategoryId(101L);
        batch.setBreedId(1101L);
        batch.setPurpose("COMMERCIAL");
        batch.setSource("PURCHASED");
        batch.setEntryDate(java.time.LocalDate.of(2026, 10, 1));
        batch.setEntryAgeDays(78);
        batch.setEntryCount(current + 5);
        batch.setCurrentCount(current);
        batch.setDeathCount(5);
        batch.setSoldCount(0);
        batch.setStatus("ACTIVE");
        batch.setVersion(1);
        return batch;
    }

    private static BatchPen batchPen(Long batchId, Long penId, int stock) {
        BatchPen row = new BatchPen();
        row.setBatchId(batchId);
        row.setPenId(penId);
        row.setStockCount(stock);
        return row;
    }
}
