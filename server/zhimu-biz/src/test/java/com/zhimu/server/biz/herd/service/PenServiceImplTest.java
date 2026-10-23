package com.zhimu.server.biz.herd.service;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.request.PenCreateRequest;
import com.zhimu.server.biz.herd.request.PenStatusRequest;
import com.zhimu.server.biz.herd.service.impl.PenServiceImpl;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.herd.vo.PenDetailVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PenServiceImplTest {

    @Mock private HerdSupport support;
    @Mock private FarmLookup farmLookup;
    @Mock private BaseDataQueryService baseDataQueryService;
    @Mock private PenMapper penMapper;
    @Mock private OperationLogRecorder operationLogRecorder;

    private PenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PenServiceImpl(support, farmLookup, baseDataQueryService, penMapper, operationLogRecorder);
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        lenient().when(support.context()).thenReturn(FarmContext.get());
        lenient().when(support.farmUnit(anyLong())).thenReturn("HEAD");
        lenient().when(support.penTypeText(anyString())).thenReturn("育肥舍");
        lenient().when(farmLookup.activeMembers(anyLong())).thenReturn(List.of());
        lenient().when(farmLookup.pensOfFarm(anyLong())).thenReturn(List.of());
        lenient().when(support.positivePensOfBatchByPen(anyLong(), anyLong())).thenReturn(List.of());
        lenient().when(support.category(anyLong())).thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD",
                "头", false, true));
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
    }

    @Test
    void detailMatchesAppMockForInUseAndEmptyPens() {
        Pen inUse = pen(2001L, "BY-01", "1 号保育舍", 360, 320, 4);
        Pen empty = pen(2005L, "YF-05", "5 号育肥舍", 400, 0, 4);
        when(support.pensOfFarm(1001L)).thenReturn(List.of(inUse, empty));
        com.zhimu.server.biz.herd.entity.Batch batch = new com.zhimu.server.biz.herd.entity.Batch();
        batch.setId(3003L);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-045");
        when(support.activeBatches(1001L)).thenReturn(List.of(batch));
        BatchPen row = new BatchPen();
        row.setBatchId(3003L);
        row.setPenId(2001L);
        row.setStockCount(320);
        when(support.positivePensOfBatchByPen(1001L, 2001L)).thenReturn(List.of(row));
        lenient().when(support.breedTitle(any())).thenReturn("杜长大保育猪");
        lenient().when(support.dayAge(any())).thenReturn(35);

        PenDetailVO vo = service.detail(2001L);

        assertThat(vo.getCapacity()).isEqualTo(360);
        assertThat(vo.getStockCount()).isEqualTo(320);
        assertThat(vo.getRemainingCapacity()).isEqualTo(40);
        assertThat(vo.getOverCapacityCount()).isZero();
        assertThat(vo.getOccupancyRate()).isEqualByComparingTo("0.8889");
        assertThat(vo.getDisplayStatus()).isEqualTo("IN_USE");
        assertThat(vo.getDisplayStatusText()).isEqualTo("在用");
        assertThat(vo.getCanDisable()).isFalse();
        assertThat(vo.getDisableBlockReason()).isEqualTo("栏舍里还有 1 个在栏批次，全部转出或出栏后才能停用");
        assertThat(vo.getBatches()).hasSize(1);
        assertThat(vo.getBatches().get(0).getQtyInPen()).isEqualTo(320);

        PenDetailVO emptyVo = service.detail(2005L);
        assertThat(emptyVo.getDisplayStatus()).isEqualTo("EMPTY");
        assertThat(emptyVo.getDisplayStatusText()).isEqualTo("空栏");
        assertThat(emptyVo.getCanDisable()).isTrue();
        assertThat(emptyVo.getDisableBlockReason()).isNull();
    }

    @Test
    void disablePenWithStockReturns42210() {
        Pen pen = pen(2001L, "BY-01", "1 号保育舍", 360, 320, 4);
        when(support.pensOfFarm(1001L)).thenReturn(List.of(pen));
        BatchPen batchPen = new BatchPen();
        batchPen.setPenId(2001L);
        batchPen.setStockCount(320);
        when(support.positivePensOfBatchByPen(1001L, 2001L)).thenReturn(List.of(batchPen));

        assertThatThrownBy(() -> service.disable(2001L, statusRequest(4)))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getCode()).isEqualTo(42210);
                    assertThat(exception.getMessage()).contains("1 个在栏批次");
                });
    }

    @Test
    void disablePenWithWrongVersionReturns40902() {
        Pen pen = pen(2005L, "YF-05", "5 号育肥舍", 400, 0, 4);
        when(support.pensOfFarm(1001L)).thenReturn(List.of(pen));

        assertThatThrownBy(() -> service.disable(2005L, statusRequest(3)))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo(40902));
    }

    @Test
    void createPenWithDuplicateCodeReturns40001FieldError() {
        when(penMapper.countByCode(1001L, "YF-03")).thenReturn(1L);
        PenCreateRequest request = new PenCreateRequest();
        request.setPenCode("yf-03");
        request.setPenName("3 号育肥舍");
        request.setPenType("FATTENING");
        request.setCategoryId(101L);
        request.setCapacity(220);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getCode()).isEqualTo(40001);
                    assertThat(exception.getMessage()).isEqualTo("参数校验失败");
                    assertThat(exception.getData()).asString().contains("field_errors");
                });
    }

    @Test
    void createPenUppercasesCodeAndReturnsDetail() {
        when(penMapper.countByCode(1001L, "YF-06")).thenReturn(0L);
        doAnswer(invocation -> {
            Pen pen = invocation.getArgument(0);
            pen.setId(2008L);
            return 1;
        }).when(penMapper).insert(any(Pen.class));
        when(support.pensOfFarm(1001L)).thenReturn(List.of(pen(2008L, "YF-06", "6 号育肥舍", 220, 0, 0)));
        PenCreateRequest request = new PenCreateRequest();
        request.setPenCode(" yf-06 ");
        request.setPenName("6 号育肥舍");
        request.setPenType("FATTENING");
        request.setCategoryId(101L);
        request.setCapacity(220);

        PenDetailVO vo = service.create(request);

        assertThat(vo.getPenId()).isEqualTo(2008L);
        assertThat(vo.getPenCode()).isEqualTo("YF-06");
        assertThat(vo.getStatus()).isEqualTo("ENABLED");
    }

    private static PenStatusRequest statusRequest(int version) {
        PenStatusRequest request = new PenStatusRequest();
        request.setVersion(version);
        return request;
    }

    private static Pen pen(Long id, String code, String name, int capacity, int stock, int version) {
        Pen pen = new Pen();
        pen.setId(id);
        pen.setFarmId(1001L);
        pen.setPenCode(code);
        pen.setPenName(name);
        pen.setPenType("FATTENING");
        pen.setCategoryId(101L);
        pen.setCapacity(capacity);
        pen.setStockCount(stock);
        pen.setStatus("ENABLED");
        pen.setVersion(version);
        return pen;
    }
}
