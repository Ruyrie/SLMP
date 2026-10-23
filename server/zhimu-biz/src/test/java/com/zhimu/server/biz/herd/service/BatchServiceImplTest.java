package com.zhimu.server.biz.herd.service;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.herd.api.BatchTimelineProvider;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.BatchTimelineMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.request.BatchCreateRequest;
import com.zhimu.server.biz.herd.request.DeathCreateRequest;
import com.zhimu.server.biz.herd.request.EntryUpdateRequest;
import com.zhimu.server.biz.herd.request.TransferCreateRequest;
import com.zhimu.server.biz.herd.service.impl.BatchServiceImpl;
import com.zhimu.server.biz.herd.support.BatchAccess;
import com.zhimu.server.biz.herd.support.BatchNoGenerator;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.herd.vo.BatchEntryResultVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchServiceImplTest {

    @Mock private HerdSupport support;
    @Mock private BatchAccess access;
    @Mock private BatchMapper batchMapper;
    @Mock private BatchEventMapper eventMapper;
    @Mock private BatchTimelineMapper timelineMapper;
    @Mock private BatchPenMapper batchPenMapper;
    @Mock private PenMapper penMapper;
    @Mock private BatchNoGenerator batchNoGenerator;
    @Mock private FarmLookup farmLookup;
    @Mock private FarmMemberCache memberCache;
    @Mock private BizMediaService bizMediaService;
    @Mock private OperationLogRecorder operationLogRecorder;
    @Mock private org.springframework.beans.factory.ObjectProvider<BatchTimelineProvider> timelineProviders;

    private BatchServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(timelineProviders.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        service = new BatchServiceImpl(support, access, batchMapper, eventMapper, timelineMapper, batchPenMapper,
                penMapper, batchNoGenerator, farmLookup, memberCache, bizMediaService, operationLogRecorder,
                timelineProviders);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        lenient().when(support.context()).thenReturn(FarmContext.get());
        lenient().when(support.farmUnit(anyLong())).thenReturn("HEAD");
        lenient().when(support.unitText(any())).thenReturn("头");
        lenient().when(support.userName(anyLong())).thenReturn("王建国");
        lenient().when(support.activeBatches(anyLong())).thenReturn(List.of());
        lenient().when(support.category(anyLong())).thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD",
                "头", false, true));
        lenient().when(support.breed(1101L)).thenReturn(new BreedQueryService.BreedInfo(1101L, 101L, "杜长大",
                null, null, null, null, true));
        lenient().when(support.breedName(1101L)).thenReturn("杜长大");
        lenient().when(timelineMapper.insert((com.zhimu.server.biz.herd.entity.BatchTimeline) any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
        BeijingTime.setClock(null);
    }

    @Test
    void createBatchOverCapacityWithoutConfirmReturns42206() {
        Pen pen = pen(2004L, "4 号育肥舍", 220, 150);
        when(support.pensOfFarm(1001L)).thenReturn(List.of(pen));

        BizException error = (BizException) catchException(() -> service.create(createRequest(90, null), "req-1"));

        assertThat(error.getCode()).isEqualTo(42206);
        assertThat(error.getData()).asString().contains("need_confirm");
        assertThat(error.getMessage()).contains("4 号育肥舍容量 220 头", "现有 150 头", "超出 20 头");
    }

    @Test
    void createBatchReturnsKeyFields() {
        Pen pen = pen(2004L, "4 号育肥舍", 220, 150);
        when(support.pensOfFarm(1001L)).thenReturn(List.of(pen));
        when(batchNoGenerator.next()).thenReturn(new BatchNoGenerator.Generated("2610", 58, "ZM2610-058"));
        doAnswer(invocation -> {
            Batch batch = invocation.getArgument(0);
            batch.setId(3058L);
            return 1;
        }).when(batchMapper).insert((Batch) any());
        doAnswer(invocation -> {
            BatchEvent event = invocation.getArgument(0);
            event.setId(958L);
            return 1;
        }).when(eventMapper).insert((BatchEvent) any());
        when(penMapper.addStock(anyLong(), anyInt(), any())).thenReturn(1);

        BatchEntryResultVO vo = service.create(createRequest(90, true), "req-1");

        assertThat(vo.getBatchId()).isEqualTo(3058L);
        assertThat(vo.getPenId()).isEqualTo(2004L);
        assertThat(vo.getBatchNo()).isEqualTo("ZM2610-058");
        assertThat(vo.getEntryCount()).isEqualTo(90);
        assertThat(vo.getPenStockAfter()).isEqualTo(240);
        assertThat(vo.getCapacity()).isEqualTo(220);
        assertThat(vo.getOverCapacityCount()).isEqualTo(20);
        assertThat(vo.getUnitText()).isEqualTo("头");
    }

    @Test
    void updateEntryWithFollowUpRecordsReturns40307() {
        Batch batch = batch(3001L, 2003L, "ACTIVE", 7, 200, 205);
        when(access.require(3001L)).thenReturn(batch);
        when(batchMapper.countFollowUpEvents(3001L)).thenReturn(1L);
        EntryUpdateRequest request = new EntryUpdateRequest();
        request.setVersion(7);

        BizException error = (BizException) catchException(() -> service.updateEntry(3001L, request));

        assertThat(error.getCode()).isEqualTo(40307);
        assertThat(error.getMessage()).isEqualTo("这条入栏记录已有后续记录，需要提交修改申请");
    }

    @Test
    void transferMoreThanPenStockReturns42205WithLatestStock() {
        Batch batch = batch(3001L, 2003L, "ACTIVE", 7, 200, 205);
        when(access.require(3001L)).thenReturn(batch);
        when(support.pensOfFarm(1001L)).thenReturn(List.of(pen(2003L, "3 号育肥舍", 220, 200),
                pen(2004L, "4 号育肥舍", 220, 150)));
        BatchPen row = batchPen(3001L, 2003L, 200, LocalDate.of(2026, 10, 1));
        when(batchPenMapper.selectList(any())).thenReturn(List.of(row));
        TransferCreateRequest request = new TransferCreateRequest();
        request.setBatchId(3001L);
        request.setFromPenId(2003L);
        request.setToPenId(2004L);
        request.setQuantity(250);
        request.setEventDate(LocalDate.of(2026, 10, 19));
        request.setSeenStockCount(190);

        BizException error = (BizException) catchException(() -> service.createTransfer(request, "tr-1"));

        assertThat(error.getCode()).isEqualTo(42205);
        assertThat(error.getData()).asString().contains("pen_id", "2003", "stock_count", "200");
        assertThat(error.getMessage()).contains("3 号育肥舍的在栏数已变为 200 头");
    }

    @Test
    void deathOnEndedBatchReturns42211() {
        Batch batch = batch(3001L, 2003L, "ENDED", 7, 0, 205);
        when(access.require(3001L)).thenReturn(batch);
        DeathCreateRequest request = new DeathCreateRequest();
        request.setBatchId(3001L);
        request.setQuantity(1);

        BizException error = (BizException) catchException(() -> service.createDeath(request, "death-1"));

        assertThat(error.getCode()).isEqualTo(42211);
    }

    @Test
    void deathMoreThanStockReturns42205() {
        Batch batch = batch(3001L, 2003L, "ACTIVE", 7, 200, 205);
        when(access.require(3001L)).thenReturn(batch);
        when(support.positivePensOfBatch(3001L)).thenReturn(List.of(batchPen(3001L, 2003L, 195,
                LocalDate.of(2026, 10, 1))));
        when(batchPenMapper.selectList(any())).thenReturn(List.of(batchPen(3001L, 2003L, 195,
                LocalDate.of(2026, 10, 1))));
        when(support.pensByIds(any())).thenReturn(List.of(pen(2003L, "3 号育肥舍", 220, 195)));
        DeathCreateRequest request = new DeathCreateRequest();
        request.setBatchId(3001L);
        request.setPenId(2003L);
        request.setQuantity(200);
        request.setSeenStockCount(190);

        BizException error = (BizException) catchException(() -> service.createDeath(request, "death-1"));

        assertThat(error.getCode()).isEqualTo(42205);
        assertThat(error.getData()).asString().contains("pen_id", "2003", "stock_count", "195");
        assertThat(error.getMessage()).contains("3 号育肥舍的在栏数已变为 195 头");
    }

    private static Object catchException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected BizException");
        } catch (BizException exception) {
            return exception;
        }
    }

    private static BatchCreateRequest createRequest(int count, Boolean capacityConfirmed) {
        BatchCreateRequest request = new BatchCreateRequest();
        request.setPenId(2004L);
        request.setBreedId(1101L);
        request.setSource("PURCHASED");
        request.setEntryDate(LocalDate.of(2026, 10, 19));
        request.setEntryAgeDays(70);
        request.setEntryCount(count);
        request.setEntryAvgWeight(new BigDecimal("28.5"));
        request.setIntroUnitPrice(new BigDecimal("520"));
        request.setCapacityConfirmed(capacityConfirmed);
        return request;
    }

    private static Pen pen(Long id, String name, int capacity, int stock) {
        Pen pen = new Pen();
        pen.setId(id);
        pen.setFarmId(1001L);
        pen.setPenCode(id == 2003L ? "YF-03" : "YF-04");
        pen.setPenName(name);
        pen.setPenType("FATTENING");
        pen.setCategoryId(101L);
        pen.setCapacity(capacity);
        pen.setStockCount(stock);
        pen.setStatus("ENABLED");
        pen.setVersion(4);
        return pen;
    }

    private static Batch batch(Long id, Long entryPenId, String status, int version, int current, int entryCount) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-031");
        batch.setQrToken("k7Rm2xQ9pT");
        batch.setCategoryId(101L);
        batch.setBreedId(1101L);
        batch.setPurpose("COMMERCIAL");
        batch.setSource("PURCHASED");
        batch.setEntryPenId(entryPenId);
        batch.setEntryDate(LocalDate.of(2026, 10, 1));
        batch.setEntryAgeDays(78);
        batch.setEntryCount(entryCount);
        batch.setCurrentCount(current);
        batch.setDeathCount(5);
        batch.setSoldCount(0);
        batch.setStatus(status);
        batch.setVersion(version);
        return batch;
    }

    private static BatchPen batchPen(Long batchId, Long penId, int stock, LocalDate firstInDate) {
        BatchPen row = new BatchPen();
        row.setId(4001L);
        row.setFarmId(1001L);
        row.setBatchId(batchId);
        row.setPenId(penId);
        row.setStockCount(stock);
        row.setFirstInDate(firstInDate);
        return row;
    }
}
