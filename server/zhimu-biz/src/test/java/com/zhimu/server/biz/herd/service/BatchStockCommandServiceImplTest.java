package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.herd.HerdTableInfo;
import com.zhimu.server.biz.herd.api.BatchStockCommandService;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.BatchTimelineMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.service.impl.BatchStockCommandServiceImpl;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.BizType;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出栏存栏端口（API-L21 的库存联动）：三处存栏同步扣减、批次售罄结束、42205/42211、
 * 同一出栏单幂等、作废回滚恢复。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchStockCommandServiceImplTest {

    @Mock private BatchMapper batchMapper;
    @Mock private BatchEventMapper eventMapper;
    @Mock private BatchPenMapper batchPenMapper;
    @Mock private PenMapper penMapper;
    @Mock private BatchTimelineMapper timelineMapper;
    @Mock private HerdSupport support;

    private BatchStockCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        HerdTableInfo.ensure();
        service = new BatchStockCommandServiceImpl(batchMapper, eventMapper, batchPenMapper, penMapper,
                timelineMapper, support);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        lenient().when(batchMapper.selectByIdIncludingDeleted(3001L)).thenReturn(batch(200, 0, "ACTIVE"));
        lenient().when(batchMapper.update(any(), any())).thenReturn(1);
        lenient().when(batchPenMapper.deduct(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        lenient().when(batchPenMapper.addStock(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        lenient().when(penMapper.addStock(anyLong(), anyInt(), any())).thenReturn(1);
        lenient().when(eventMapper.insert(any(BatchEvent.class))).thenAnswer(invocation -> {
            BatchEvent event = invocation.getArgument(0);
            event.setId(9900L);
            return 1;
        });
        lenient().when(eventMapper.selectList(any())).thenReturn(List.of());
        lenient().when(batchPenMapper.selectList(any())).thenReturn(List.of());
        lenient().when(timelineMapper.upsertSaleTimeline(any(com.zhimu.server.biz.herd.entity.BatchTimeline.class))).thenReturn(1);
        lenient().when(support.positivePensOfBatch(3001L)).thenReturn(List.of(batchPen(2003L, 200)));
        lenient().when(support.pensByIds(any())).thenReturn(List.of(pen(2003L, 200, "3 号育肥舍")));
        lenient().when(support.userName(10001L)).thenReturn("王建国");
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
        BeijingTime.setClock(null);
    }

    @Test
    void saleDeductsBatchPenAndPenStock() {
        BatchStockCommandService.BatchSaleResult result = service.sale(command(50, item(2003L, 50)));

        assertThat(result.quantity()).isEqualTo(50);
        assertThat(result.currentCount()).isEqualTo(150);
        assertThat(result.soldCount()).isEqualTo(50);
        assertThat(result.batchEnded()).isFalse();
        assertThat(result.pens()).hasSize(1);
        assertThat(result.pens().get(0).penId()).isEqualTo(2003L);
        assertThat(result.pens().get(0).stockCount()).isEqualTo(150);
        verify(batchPenMapper).deduct(eq(3001L), eq(2003L), eq(50), any(LocalDate.class), any(LocalDateTime.class));
        verify(penMapper).addStock(eq(2003L), eq(-50), any(LocalDateTime.class));
        verify(eventMapper).insert(any(BatchEvent.class));
        verify(timelineMapper).upsertSaleTimeline(any(com.zhimu.server.biz.herd.entity.BatchTimeline.class));
    }

    @Test
    void saleWithoutItemsAllocatesByPen() {
        BatchStockCommandService.BatchSaleResult result = service.sale(
                new BatchStockCommandService.SaleStockCommand(3001L, BizType.SALE_ORDER, 9101L,
                        LocalDate.of(2026, 10, 19), 100, null, 10001L));

        assertThat(result.quantity()).isEqualTo(100);
        assertThat(result.currentCount()).isEqualTo(100);
        verify(batchPenMapper).deduct(eq(3001L), eq(2003L), eq(100), any(), any());
    }

    @Test
    void saleEndsBatchWhenSoldOut() {
        when(batchMapper.selectByIdIncludingDeleted(3001L)).thenReturn(batch(50, 0, "ACTIVE"));
        when(support.positivePensOfBatch(3001L)).thenReturn(List.of(batchPen(2003L, 50)));

        BatchStockCommandService.BatchSaleResult result = service.sale(command(50, item(2003L, 50)));

        assertThat(result.batchEnded()).isTrue();
        assertThat(result.batchStatus()).isEqualTo("ENDED");
        assertThat(result.currentCount()).isZero();
    }

    @Test
    void saleOverStockReturns42205() {
        assertThatThrownBy(() -> service.sale(command(300, item(2003L, 300))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(42205);
    }

    @Test
    void saleEndedBatchReturns42211() {
        when(batchMapper.selectByIdIncludingDeleted(3001L)).thenReturn(batch(0, 200, "ENDED"));

        assertThatThrownBy(() -> service.sale(command(10, item(2003L, 10))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(42211);
        verify(batchPenMapper, never()).deduct(anyLong(), anyLong(), anyInt(), any(), any());
    }

    @Test
    void saleIsIdempotentForSameOrderAndBatch() {
        BatchEvent existing = event(9901L, 3001L, 2003L, 50, "EFFECTIVE");
        when(eventMapper.selectList(any())).thenReturn(List.of(existing));

        BatchStockCommandService.BatchSaleResult result = service.sale(command(50, item(2003L, 50)));

        assertThat(result.quantity()).isEqualTo(50);
        verify(batchPenMapper, never()).deduct(anyLong(), anyLong(), anyInt(), any(), any());
        verify(eventMapper, never()).insert(any(BatchEvent.class));
    }

    @Test
    void restoreSaleAddsBackStockAndReactivatesBatch() {
        BatchEvent event = event(9901L, 3001L, 2003L, 200, "EFFECTIVE");
        when(eventMapper.selectList(any())).thenReturn(List.of(event));
        when(batchMapper.selectByIdIncludingDeleted(3001L)).thenReturn(batch(0, 200, "ENDED"));
        when(batchPenMapper.addStock(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);

        service.restoreSale(BizType.SALE_ORDER, 9101L);

        verify(batchPenMapper).addStock(eq(3001L), eq(2003L), eq(200), any(), any());
        verify(penMapper).addStock(eq(2003L), eq(200), any());
        verify(timelineMapper).delete(any());
        verify(eventMapper).update(any(), any());
    }

    @Test
    void restoreIsIdempotentWhenNoEffectiveEvents() {
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.restoreSale(9101L);

        verify(batchPenMapper, never()).addStock(anyLong(), anyLong(), anyInt(), any(), any());
        verify(eventMapper, never()).update(any(), any());
    }

    private static BatchStockCommandService.SaleStockCommand command(int quantity,
                                                                    BatchStockCommandService.SaleStockItem item) {
        return new BatchStockCommandService.SaleStockCommand(3001L, BizType.SALE_ORDER, 9101L,
                LocalDate.of(2026, 10, 19), quantity, List.of(item), 10001L);
    }

    private static BatchStockCommandService.SaleStockItem item(Long penId, int quantity) {
        return new BatchStockCommandService.SaleStockItem(penId, quantity);
    }

    private static Batch batch(int current, int sold, String status) {
        Batch batch = new Batch();
        batch.setId(3001L);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-031");
        batch.setEntryDate(LocalDate.of(2026, 10, 1));
        batch.setEntryCount(205);
        batch.setCurrentCount(current);
        batch.setSoldCount(sold);
        batch.setStatus(status);
        batch.setVersion(7);
        batch.setDeleted(false);
        return batch;
    }

    private static BatchPen batchPen(Long penId, int stock) {
        BatchPen row = new BatchPen();
        row.setBatchId(3001L);
        row.setPenId(penId);
        row.setStockCount(stock);
        return row;
    }

    private static Pen pen(Long id, int stock, String name) {
        Pen pen = new Pen();
        pen.setId(id);
        pen.setFarmId(1001L);
        pen.setPenName(name);
        pen.setStockCount(stock);
        return pen;
    }

    private static BatchEvent event(Long id, Long batchId, Long penId, int quantity, String status) {
        BatchEvent event = new BatchEvent();
        event.setId(id);
        event.setFarmId(1001L);
        event.setBatchId(batchId);
        event.setPenId(penId);
        event.setQuantity(quantity);
        event.setEventType("SALE");
        event.setEventDate(LocalDate.of(2026, 10, 19));
        event.setSourceBizType("SALE_ORDER");
        event.setSourceBizId(9101L);
        event.setStatus(status);
        event.setVersion(0);
        event.setDeleted(false);
        return event;
    }
}
