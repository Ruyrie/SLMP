package com.zhimu.server.biz.stock.support.change;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockOutboundMapper;
import com.zhimu.server.biz.stock.support.BatchAllocator;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 其他出库修改申请：次日起才需要申请、input_qty 表单、审核后更正结存 / 作废退回。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboundChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private StockOutboundMapper outboundMapper;
    @Mock private StockFlowMapper flowMapper;
    @Mock private StockLedger ledger;
    @Mock private StockFlowWriter flowWriter;
    @Mock private BatchAllocator allocator;
    @Mock private FeedMapper feedMapper;
    @Mock private DrugMapper drugMapper;
    @Mock private DrugLotMapper drugLotMapper;
    @Mock private AccountQueryService accountQueryService;
    @Mock private OperationLogRecorder operationLogRecorder;

    private OutboundChangeHandler handler;
    private StockOutbound outbound;
    private Feed feed;
    private StockFlow flow;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(CLOCK);
        StockChangeSupport support = new StockChangeSupport(feedMapper, drugMapper, drugLotMapper,
                accountQueryService, operationLogRecorder);
        handler = new OutboundChangeHandler(outboundMapper, flowMapper, ledger, flowWriter, allocator, support);
        outbound = new StockOutbound();
        outbound.setId(6501L);
        outbound.setFarmId(1001L);
        outbound.setOutboundNo("CK26106501");
        outbound.setMaterialType("FEED");
        outbound.setOutType("OTHER_OUT");
        outbound.setFeedId(5102L);
        outbound.setInputQty(new BigDecimal("50"));
        outbound.setInputUnit("MEASURE");
        outbound.setQty(new BigDecimal("50"));
        outbound.setAmount(new BigDecimal("155.00"));
        outbound.setMaxQty(new BigDecimal("1900"));
        outbound.setDestination("RETURN_SUPPLIER");
        outbound.setOutDate(LocalDate.of(2026, 10, 16));
        outbound.setStatus("NORMAL");
        outbound.setFlowId(7501L);
        outbound.setVersion(1);
        outbound.setCreatedBy(10001L);
        outbound.setCreatedAt(LocalDateTime.of(2026, 10, 16, 9, 0));
        when(outboundMapper.selectById(6501L)).thenReturn(outbound);
        when(outboundMapper.selectForUpdate(6501L, 1001L)).thenReturn(outbound);

        feed = new Feed();
        feed.setId(5102L);
        feed.setName("保育料");
        feed.setUnit("KG");
        feed.setStockQty(new BigDecimal("1850"));
        feed.setStockAmount(new BigDecimal("5735"));
        feed.setAvgPrice(new BigDecimal("3.1"));
        when(feedMapper.selectById(5102L)).thenReturn(feed);
        when(accountQueryService.getBrief(any())).thenAnswer(invocation ->
                new AccountBrief(invocation.getArgument(0), "王建国", "13800000000", "NORMAL", false));

        flow = new StockFlow();
        flow.setId(7501L);
        flow.setFeedId(5102L);
        flow.setQty(new BigDecimal("50"));
        flow.setAmount(new BigDecimal("155.00"));
        flow.setStockQtyAfter(new BigDecimal("1850"));
        flow.setStockAmountAfter(new BigDecimal("5735"));
        flow.setStatus("NORMAL");
        when(flowMapper.selectById(7501L)).thenReturn(flow);
        when(ledger.adjustFeed(any(Feed.class), any(), any()))
                .thenReturn(new StockLedger.Move(new BigDecimal("1840"), new BigDecimal("5704"),
                        new BigDecimal("3.1"), new BigDecimal("3.1"), new BigDecimal("-31"), 7));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsOutboundAndInventoryModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.OUTBOUND);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.INVENTORY);
    }

    @Test
    void refundedOutboundBlocksFormSubmissionAndEveryApprovalTypeBeforeInventoryMutation() {
        outbound.setFinanceRecordId(88001L);
        outbound.setRefundRegisteredAt(BeijingTime.now());
        ChangeRecord record = handler.loadRecord(BizType.OUTBOUND, 6501L);
        assertThatThrownBy(() -> handler.describeForm(record)).isInstanceOf(BizException.class)
                .hasMessageContaining("已登记退款");
        for (ChangeType type : java.util.List.of(ChangeType.UPDATE, ChangeType.DELETE, ChangeType.VOID)) {
            assertThatThrownBy(() -> handler.validateChange(record, null, type, "input_qty", "40"))
                    .isInstanceOf(BizException.class).hasMessageContaining("已登记退款");
            ChangeRequest request = new ChangeRequest();
            request.setChangeType(type.name());
            assertThatThrownBy(() -> handler.validateForApprove(request, record))
                    .isInstanceOf(BizException.class).hasMessageContaining("已登记退款");
            assertThatThrownBy(() -> handler.apply(request, record, 1L))
                    .isInstanceOf(BizException.class).hasMessageContaining("已登记退款");
        }
        org.mockito.Mockito.verifyNoInteractions(ledger, flowWriter, allocator);
        org.mockito.Mockito.verify(outboundMapper, org.mockito.Mockito.never()).updateById(any(StockOutbound.class));
    }

    @Test
    void loadRecordUsesOutboundNoAndDestination() {
        ChangeRecord record = handler.loadRecord(BizType.OUTBOUND, 6501L);

        assertThat(record.recordLocator()).isEqualTo("CK26106501 · 10月16日");
        assertThat(record.recordTitle()).isEqualTo("其他出库 · 保育料");
        assertThat(record.recordSubtitle()).isEqualTo("10月16日 · 王建国登记");
        assertThat(record.recordVersion()).isEqualTo("1");
    }

    @Test
    void requiresRequestFromNextDay() {
        ChangeRecord record = handler.loadRecord(BizType.OUTBOUND, 6501L);
        assertThat(handler.requiresRequest(record)).isTrue();

        outbound.setCreatedAt(LocalDateTime.of(2026, 10, 19, 8, 0));
        assertThat(handler.requiresRequest(record)).isFalse();
    }

    @Test
    void describeFormMatchesAppMock() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.OUTBOUND, 6501L));

        assertThat(form.fields()).hasSize(1);
        ChangeField field = form.fields().get(0);
        assertThat(field.fieldCode()).isEqualTo("input_qty");
        assertThat(field.fieldName()).isEqualTo("出库数量");
        assertThat(field.value()).isEqualTo("50");
        assertThat(field.valueUnit()).isEqualTo("千克");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo("0");
        assertThat(field.max()).isEqualByComparingTo("1900");
        assertThat(form.recordCard()).extracting(com.zhimu.server.biz.common.change.ChangeCardItem::label)
                .containsExactly("出库数量", "出库去向");
        assertThat(form.recordCard().get(0).value()).isEqualTo("50 千克");
        assertThat(form.recordCard().get(1).value()).isEqualTo("退回供应商");
    }

    @Test
    void validateChangeChecksQuantity() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.OUTBOUND, 6501L));
        ChangeRecord record = handler.loadRecord(BizType.OUTBOUND, 6501L);

        ChangeValidation validation = handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "60");
        assertThat(validation.afterValue()).isEqualTo("60");
        assertThat(validation.impacts()).containsExactly(ChangeImpact.INVENTORY);

        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "0"))
                .isInstanceOf(BizException.class).hasMessageContaining("必须大于 0");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "1901"))
                .isInstanceOf(BizException.class).hasMessageContaining("不能为负数");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "50"))
                .isInstanceOf(BizException.class).hasMessageContaining("新值与原值相同");
    }

    @Test
    void validateForApproveRejectsQuantityAboveStock() {
        ChangeRequest request = request("UPDATE", "{\"after\":{\"input_qty\":{\"value\":\"2000\"}}}");

        assertThatThrownBy(() -> handler.validateForApprove(request,
                handler.loadRecord(BizType.OUTBOUND, 6501L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过当前可用结存 1,900 千克");
    }

    @Test
    void validateForApproveRejectsExpiredDrugLot() {
        outbound.setMaterialType("DRUG");
        outbound.setFeedId(null);
        outbound.setDrugId(5201L);
        outbound.setDrugLotId(5601L);
        outbound.setQty(new BigDecimal("10"));
        outbound.setInputQty(new BigDecimal("10"));
        com.zhimu.server.biz.stock.entity.Drug drug = new com.zhimu.server.biz.stock.entity.Drug();
        drug.setId(5201L);
        drug.setName("氟苯尼考粉");
        drug.setUnit("G");
        when(drugMapper.selectById(5201L)).thenReturn(drug);
        com.zhimu.server.biz.stock.entity.DrugLot lot = new com.zhimu.server.biz.stock.entity.DrugLot();
        lot.setId(5601L);
        lot.setLotNo("FF20260901");
        lot.setExpireDate(LocalDate.of(2026, 10, 1));
        lot.setStockQty(new BigDecimal("100"));
        when(drugLotMapper.selectById(5601L)).thenReturn(lot);
        ChangeRequest request = request("UPDATE", "{\"after\":{\"input_qty\":{\"value\":\"8\"}}}");

        assertThatThrownBy(() -> handler.validateForApprove(request,
                handler.loadRecord(BizType.OUTBOUND, 6501L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    void applyUpdateAdjustsStockAndFlow() {
        ChangeRequest request = request("UPDATE", "{\"after\":{\"input_qty\":{\"value\":\"60\"}}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.OUTBOUND, 6501L), 90001L);

        assertThat(result.recalcCount()).isEqualTo(2);
        ArgumentCaptor<BigDecimal> deltaQty = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> deltaAmount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ledger).adjustFeed(any(Feed.class), deltaQty.capture(), deltaAmount.capture());
        assertThat(deltaQty.getValue()).isEqualByComparingTo("-10");
        assertThat(deltaAmount.getValue()).isEqualByComparingTo("-31");
        ArgumentCaptor<StockOutbound> saved = ArgumentCaptor.forClass(StockOutbound.class);
        verify(outboundMapper).updateById(saved.capture());
        assertThat(saved.getValue().getQty()).isEqualByComparingTo("60");
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("186");
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo(90001L);
        verify(flowMapper).updateById(any(StockFlow.class));
    }

    @Test
    void applyVoidRestoresStockAndVoidsFlow() {
        ChangeRequest request = request("VOID", "{\"after\":{}}");

        handler.apply(request, handler.loadRecord(BizType.OUTBOUND, 6501L), 90001L);

        ArgumentCaptor<BigDecimal> deltaQty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ledger).adjustFeed(any(Feed.class), deltaQty.capture(), any());
        assertThat(deltaQty.getValue()).isEqualByComparingTo("50");
        verify(flowWriter).voidFlow(any(StockFlow.class), any());
        ArgumentCaptor<StockOutbound> saved = ArgumentCaptor.forClass(StockOutbound.class);
        verify(outboundMapper).updateById(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("VOID");
        assertThat(saved.getValue().getVoidBy()).isEqualTo(90001L);
    }

    private static ChangeRequest request(String changeType, String afterJson) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9701L);
        request.setBizType("OUTBOUND");
        request.setBizId(6501L);
        request.setFarmId(1001L);
        request.setChangeType(changeType);
        request.setFieldCode("UPDATE".equals(changeType) ? "input_qty" : null);
        request.setFieldName("出库数量");
        request.setAfterJson(afterJson);
        request.setReason("出库数量登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
