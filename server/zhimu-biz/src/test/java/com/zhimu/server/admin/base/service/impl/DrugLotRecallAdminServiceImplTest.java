package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.DrugLotRecall;
import com.zhimu.server.admin.base.entity.DrugLotRecallFarm;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.DrugLotRecallFarmMapper;
import com.zhimu.server.admin.base.mapper.DrugLotRecallMapper;
import com.zhimu.server.admin.base.query.IdNameRow;
import com.zhimu.server.admin.base.request.LotRecallRequest;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.DrugAdminSupport;
import com.zhimu.server.admin.base.vo.DrugLotRecallVO;
import com.zhimu.server.admin.base.vo.RecallPreviewVO;
import com.zhimu.server.biz.stock.api.DrugLotRecallPort;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D35–D38 关键分支：预览汇总、重复停用 40903、停用写通知表并调用模块09 端口。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrugLotRecallAdminServiceImplTest {

    @Mock private DrugLotRecallMapper recallMapper;
    @Mock private DrugLotRecallFarmMapper recallFarmMapper;
    @Mock private BaseAdminReadMapper readMapper;
    @Mock private DrugAdminSupport drugSupport;
    @Mock private DrugLotRecallPort recallPort;
    @Mock private DrugLotRecallNotifier notifier;
    @Mock private BaseLogs logs;

    private DrugLotRecallAdminServiceImpl service;
    private DrugCatalog catalog;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.base.AdminBaseTableInfo.ensure();
        service = new DrugLotRecallAdminServiceImpl(recallMapper, recallFarmMapper, readMapper, drugSupport,
                recallPort, notifier, logs);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        catalog = new DrugCatalog();
        catalog.setId(7003L);
        catalog.setName("口蹄疫灭活疫苗");
        catalog.setDrugCategory("VACCINE");
        catalog.setMeasureUnit("DOSE");
        catalog.setVersion(1);
        when(drugSupport.requireCatalog(7003L)).thenReturn(catalog);
        when(recallMapper.selectOne(any())).thenReturn(null);
        when(recallMapper.insert(any(DrugLotRecall.class))).thenAnswer(invocation -> {
            DrugLotRecall recall = invocation.getArgument(0);
            recall.setId(301L);
            return 1;
        });
        when(recallMapper.updateById(any(DrugLotRecall.class))).thenReturn(1);
        when(recallFarmMapper.insert(any(DrugLotRecallFarm.class))).thenReturn(1);
        IdNameRow farm = new IdNameRow();
        farm.setId(1001L);
        farm.setName("东山猪场");
        when(readMapper.farmNames(any())).thenReturn(List.of(farm));
        when(recallPort.holds(7003L, "2025047")).thenReturn(new DrugLotRecallPort.RecallApplyResult(
                LocalDate.of(2027, 4, 20),
                List.of(new DrugLotRecallPort.RecallLot(91L, 1001L, new BigDecimal("300.00"),
                                LocalDate.of(2027, 4, 20)),
                        new DrugLotRecallPort.RecallLot(92L, 1002L, BigDecimal.ZERO,
                                LocalDate.of(2027, 4, 20)))));
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void previewAggregatesFarmsWithStock() {
        RecallPreviewVO vo = service.preview(7003L, " 2025047 ");

        assertThat(vo.getHasStockRecord()).isTrue();
        assertThat(vo.getFarmCount()).isEqualTo(1);
        assertThat(vo.getTotalQty()).isEqualByComparingTo("300.00");
        assertThat(vo.getSampleFarmName()).isEqualTo("东山猪场");
        assertThat(vo.getSampleFarmQty()).isEqualByComparingTo("300.00");
        assertThat(vo.getExpireDate()).isEqualTo(LocalDate.of(2027, 4, 20));
        assertThat(vo.getQtyUnitText()).isEqualTo("头份");
    }

    @Test
    void recallWritesNoticeRowsAndMarksLots() {
        when(recallPort.markRecalled(anyLong(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(new DrugLotRecallPort.RecallApplyResult(LocalDate.of(2027, 4, 20),
                        List.of(new DrugLotRecallPort.RecallLot(91L, 1001L, new BigDecimal("300.00"),
                                        LocalDate.of(2027, 4, 20)),
                                new DrugLotRecallPort.RecallLot(92L, 1002L, BigDecimal.ZERO,
                                        LocalDate.of(2027, 4, 20)))));
        LotRecallRequest request = request();

        DrugLotRecallVO vo = service.recall(request);

        assertThat(vo.getId()).isEqualTo(301L);
        assertThat(vo.getFarmCount()).isEqualTo(1);
        assertThat(vo.getTotalQty()).isEqualByComparingTo("300.00");
        assertThat(vo.getNotifiedFarmCount()).isEqualTo(1);
        verify(recallFarmMapper).insert(any(DrugLotRecallFarm.class));
        verify(recallPort).markRecalled(anyLong(), anyString(), anyLong(), anyString(), any());
        verify(notifier).notifyRecalled(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void duplicateRecallReturns40903() {
        DrugLotRecall existing = new DrugLotRecall();
        existing.setId(301L);
        when(recallMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.recall(request()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40903))
                .hasMessage("批号 2025047 已停用，不能重复停用");
        verify(recallPort, never()).markRecalled(anyLong(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void recallWithoutReasonReturns40004() {
        LotRecallRequest request = request();
        request.setReason(" ");
        assertThatThrownBy(() -> service.recall(request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40004))
                .hasMessage("请填写停用原因");
    }

    private static LotRecallRequest request() {
        LotRecallRequest request = new LotRecallRequest();
        request.setCatalogId(7003L);
        request.setLotNo("2025047");
        request.setReason("生产企业召回，抽检效价不合格");
        return request;
    }
}
