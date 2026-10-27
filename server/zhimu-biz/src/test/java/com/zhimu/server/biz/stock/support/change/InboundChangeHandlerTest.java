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
import com.zhimu.server.biz.stock.entity.StockInbound;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockInboundMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 入库单修改申请：已出库才需要申请、input_qty/unit_price 表单、差额调整库存与流水。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InboundChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private StockInboundMapper inboundMapper;
    @Mock private StockFlowMapper flowMapper;
    @Mock private StockLedger ledger;
    @Mock private FeedMapper feedMapper;
    @Mock private DrugMapper drugMapper;
    @Mock private DrugLotMapper drugLotMapper;
    @Mock private AccountQueryService accountQueryService;
    @Mock private OperationLogRecorder operationLogRecorder;

    private InboundChangeHandler handler;
    private StockInbound inbound;
    private Feed feed;
    private StockFlow flow;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(CLOCK);
        StockChangeSupport support = new StockChangeSupport(feedMapper, drugMapper, drugLotMapper,
                accountQueryService, operationLogRecorder);
        handler = new InboundChangeHandler(inboundMapper, flowMapper, ledger,
                org.mockito.Mockito.mock(com.zhimu.server.biz.finance.api.FinanceSourceWriteService.class), support);
        inbound = new StockInbound();
        inbound.setId(6201L);
        inbound.setFarmId(1001L);
        inbound.setInboundNo("RK26106201");
        inbound.setMaterialType("FEED");
        inbound.setFeedId(5101L);
        inbound.setInputQty(new BigDecimal("2000"));
        inbound.setInputUnit("MEASURE");
        inbound.setQty(new BigDecimal("2000"));
        inbound.setUnitPrice(new BigDecimal("3.20"));
        inbound.setMeasurePrice(new BigDecimal("3.20"));
        inbound.setAmount(new BigDecimal("6400.00"));
        inbound.setInboundDate(LocalDate.of(2026, 10, 12));
        inbound.setStatus("NORMAL");
        inbound.setFlowId(7001L);
        inbound.setVersion(1);
        inbound.setCreatedBy(10001L);
        when(inboundMapper.selectById(6201L)).thenReturn(inbound);
        when(flowMapper.countOutboundAfterFeed(7001L, 5101L)).thenReturn(12L);

        feed = new Feed();
        feed.setId(5101L);
        feed.setFarmId(1001L);
        feed.setName("育肥全价料");
        feed.setUnit("KG");
        feed.setPackUnit("袋");
        feed.setPackSize(new BigDecimal("25"));
        feed.setStockQty(new BigDecimal("3000"));
        feed.setStockAmount(new BigDecimal("9600"));
        feed.setAvgPrice(new BigDecimal("3.2000"));
        feed.setVersion(5);
        when(feedMapper.selectById(5101L)).thenReturn(feed);
        when(accountQueryService.getBrief(any())).thenAnswer(invocation ->
                new AccountBrief(invocation.getArgument(0), "王建国", "13800000000", "NORMAL", false));

        flow = new StockFlow();
        flow.setId(7001L);
        flow.setFarmId(1001L);
        flow.setBizType("INBOUND");
        flow.setBizId(6201L);
        flow.setQty(new BigDecimal("2000"));
        flow.setUnitPrice(new BigDecimal("3.20"));
        flow.setAmount(new BigDecimal("6400.00"));
        flow.setStockQtyAfter(new BigDecimal("3000"));
        flow.setStockAmountAfter(new BigDecimal("9600.00"));
        flow.setStatus("NORMAL");
        when(flowMapper.selectById(7001L)).thenReturn(flow);
        when(ledger.adjustFeed(any(Feed.class), any(), any()))
                .thenReturn(new StockLedger.Move(new BigDecimal("2500"), new BigDecimal("8000"),
                        new BigDecimal("3.2"), new BigDecimal("3.2"), new BigDecimal("-1600"), 6));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsInboundAndInventoryModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.INBOUND);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.INVENTORY);
    }

    @Test
    void loadRecordUsesInboundNoAndMaterialName() {
        ChangeRecord record = handler.loadRecord(BizType.INBOUND, 6201L);

        assertThat(record.recordLocator()).isEqualTo("RK26106201 · 10月12日");
        assertThat(record.recordTitle()).isEqualTo("饲料入库 · 育肥全价料");
        assertThat(record.recordSubtitle()).isEqualTo("10月12日 · 王建国登记");
        assertThat(record.recordVersion()).isEqualTo("1");
        assertThat(handler.loadRecord(BizType.INBOUND, 9999L)).isNull();
    }

    @Test
    void requiresRequestOnlyAfterOutbound() {
        ChangeRecord record = handler.loadRecord(BizType.INBOUND, 6201L);
        assertThat(handler.requiresRequest(record)).isTrue();

        when(flowMapper.countOutboundAfterFeed(7001L, 5101L)).thenReturn(0L);
        assertThat(handler.requiresRequest(record)).isFalse();
    }

    @Test
    void describeFormMatchesAppMock() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.INBOUND, 6201L));

        assertThat(form.fields()).hasSize(2);
        ChangeField qty = form.fields().get(0);
        assertThat(qty.fieldCode()).isEqualTo("input_qty");
        assertThat(qty.fieldName()).isEqualTo("入库数量");
        assertThat(qty.value()).isEqualTo("2000");
        assertThat(qty.valueUnit()).isEqualTo("千克");
        assertThat(qty.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(qty.min()).isEqualByComparingTo("0");
        assertThat(qty.max()).isEqualByComparingTo("99999999");
        ChangeField price = form.fields().get(1);
        assertThat(price.fieldCode()).isEqualTo("unit_price");
        assertThat(price.fieldName()).isEqualTo("单价");
        assertThat(price.value()).isEqualTo("3.20");
        assertThat(price.valueUnit()).isEqualTo("元/千克");
        assertThat(price.max()).isEqualByComparingTo("999999");
        assertThat(form.recordCard()).extracting(com.zhimu.server.biz.common.change.ChangeCardItem::label)
                .containsExactly("入库数量", "单价");
        assertThat(form.recordCard().get(0).value()).isEqualTo("2,000 千克");
        assertThat(form.recordCard().get(1).value()).isEqualTo("3.20 元/千克");
    }

    @Test
    void validateChangeChecksQuantityAndPrice() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.INBOUND, 6201L));
        ChangeRecord record = handler.loadRecord(BizType.INBOUND, 6201L);

        ChangeValidation validation = handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "1500");
        assertThat(validation.afterValue()).isEqualTo("1500");
        assertThat(validation.impacts()).containsExactly(ChangeImpact.INVENTORY, ChangeImpact.COST,
                ChangeImpact.STATS);

        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "0"))
                .isInstanceOf(BizException.class).hasMessageContaining("入库数量必须大于 0");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "2000"))
                .isInstanceOf(BizException.class).hasMessageContaining("新值与原值相同");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "unit_price", "3.20"))
                .isInstanceOf(BizException.class).hasMessageContaining("新值与原值相同");
    }

    @Test
    void applyUpdateAdjustsStockAndFlow() {
        ChangeRequest request = request("input_qty", "{\"after\":{\"input_qty\":{\"value\":\"1500\"}}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.INBOUND, 6201L), 90001L);

        assertThat(result.recalcCount()).isEqualTo(3);
        ArgumentCaptor<BigDecimal> deltaQty = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> deltaAmount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ledger).adjustFeed(eq(feed), deltaQty.capture(), deltaAmount.capture());
        assertThat(deltaQty.getValue()).isEqualByComparingTo("-500");
        assertThat(deltaAmount.getValue()).isEqualByComparingTo("-1600");
        ArgumentCaptor<StockInbound> saved = ArgumentCaptor.forClass(StockInbound.class);
        verify(inboundMapper).updateById(saved.capture());
        assertThat(saved.getValue().getQty()).isEqualByComparingTo("1500");
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("4800.00");
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo(90001L);
        ArgumentCaptor<StockFlow> flowSaved = ArgumentCaptor.forClass(StockFlow.class);
        verify(flowMapper).updateById(flowSaved.capture());
        assertThat(flowSaved.getValue().getQty()).isEqualByComparingTo("1500");
        assertThat(flowSaved.getValue().getStockQtyAfter()).isEqualByComparingTo("2500");
    }

    private static ChangeRequest request(String fieldCode, String afterJson) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9401L);
        request.setBizType("INBOUND");
        request.setBizId(6201L);
        request.setFarmId(1001L);
        request.setChangeType("UPDATE");
        request.setFieldCode(fieldCode);
        request.setFieldName("input_qty".equals(fieldCode) ? "入库数量" : "单价");
        request.setAfterJson(afterJson);
        request.setReason("入库数量登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
