package com.zhimu.server.biz.herd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.service.impl.SelfBredBatchServiceImpl;
import com.zhimu.server.common.error.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 自繁批次存栏调整（产仔 / 出雏修改申请回写）的差额与 42205 口径。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelfBredBatchServiceImplTest {

    @Mock private BatchMapper batchMapper;
    @Mock private BatchPenMapper batchPenMapper;
    @Mock private PenMapper penMapper;

    @Mock private org.springframework.beans.factory.ObjectProvider<BatchService> batchService;
    @Mock private BatchService batchWriter;
    private SelfBredBatchServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Batch.class);
    }

    @BeforeEach
    void setUp() {
        service = new SelfBredBatchServiceImpl(batchMapper, batchPenMapper, penMapper, batchService);
        when(batchMapper.selectByIdIncludingDeleted(3052L)).thenReturn(batch());
        when(batchMapper.update(any(), any())).thenReturn(1);
        when(batchPenMapper.addStock(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        when(batchPenMapper.deduct(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        when(penMapper.addStock(anyLong(), anyInt(), any())).thenReturn(1);
    }

    @Test
    void createsZeroDaySelfBredBatchThroughNormalStockWriterAndAssociatesSource() {
        when(batchService.getObject()).thenReturn(batchWriter);
        var result=new com.zhimu.server.biz.herd.vo.BatchEntryResultVO();
        result.setBatchId(3501L);result.setBatchNo("ZM2610-080");
        when(batchWriter.create(any(),eq("self-bred:FARROWING:8401"))).thenReturn(result);
        var created=service.create(2002L,1101L,40,LocalDate.of(2026,10,27),false,"FARROWING",8401L);
        assertThat(created.batchId()).isEqualTo(3501L);
        var payload=org.mockito.ArgumentCaptor.forClass(com.zhimu.server.biz.herd.request.BatchCreateRequest.class);
        verify(batchWriter).create(payload.capture(),eq("self-bred:FARROWING:8401"));
        assertThat(payload.getValue().getSource()).isEqualTo("SELF_BRED");
        assertThat(payload.getValue().getEntryCount()).isEqualTo(40);
        assertThat(payload.getValue().getEntryAgeDays()).isZero();
        assertThat(payload.getValue().getEntryDate()).isEqualTo(LocalDate.of(2026,10,27));
        verify(batchMapper).update(eq(null),any());
    }

    @Test
    void positiveDeltaAddsStockToBatchPenAndPen() {
        int current = service.adjustEntry(3052L, 5, null);

        assertThat(current).isEqualTo(15);
        verify(batchPenMapper).addStock(anyLong(), anyLong(), anyInt(), any(), any());
        verify(penMapper).addStock(eq(2007L), eq(5), any());
    }

    @Test
    void negativeDeltaDeductsStock() {
        int current = service.adjustEntry(3052L, -5, null);

        assertThat(current).isEqualTo(5);
        verify(batchPenMapper).deduct(anyLong(), anyLong(), anyInt(), any(), any());
        verify(penMapper).addStock(eq(2007L), eq(-5), any());
    }

    @Test
    void dateOnlyAdjustmentDoesNotTouchStock() {
        int current = service.adjustEntry(3052L, 0, LocalDate.of(2026, 9, 20));

        assertThat(current).isEqualTo(10);
        verify(batchPenMapper, never()).addStock(anyLong(), anyLong(), anyInt(), any(), any());
        verify(penMapper, never()).addStock(anyLong(), anyInt(), any());
        verify(batchMapper).update(any(), any());
    }

    @Test
    void adjustmentBelowZeroStockReturns42205() {
        BizException error = (BizException) catchException(() -> service.adjustEntry(3052L, -11, null));

        assertThat(error.getCode()).isEqualTo(42205);
        assertThat(error.getMessage()).contains("在栏数不足");
        verify(batchPenMapper, never()).deduct(anyLong(), anyLong(), anyInt(), any(), any());
    }

    @Test
    void versionConflictReturns40902() {
        when(batchMapper.update(any(), any())).thenReturn(0);

        BizException error = (BizException) catchException(() -> service.adjustEntry(3052L, 1, null));

        assertThat(error.getCode()).isEqualTo(40902);
    }

    @Test
    void missingBatchReturns40401() {
        BizException error = (BizException) catchException(() -> service.adjustEntry(9999L, 1, null));

        assertThat(error.getCode()).isEqualTo(40401);
    }

    private static Batch batch() {
        Batch batch = new Batch();
        batch.setId(3052L);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2609-052");
        batch.setEntryPenId(2007L);
        batch.setEntryDate(LocalDate.of(2026, 9, 17));
        batch.setEntryCount(11);
        batch.setCurrentCount(10);
        batch.setDeathCount(1);
        batch.setStatus("ENDED");
        batch.setVersion(3);
        batch.setDeleted(false);
        return batch;
    }
}
