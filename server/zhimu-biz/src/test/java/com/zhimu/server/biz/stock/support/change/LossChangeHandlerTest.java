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
import com.zhimu.server.biz.stock.entity.StockLoss;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockLossMapper;
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

/** 报损修改申请：input_qty 表单、数量校验、审核后更正流水 / 作废退回库存。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LossChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private StockLossMapper lossMapper;
    @Mock private StockFlowMapper flowMapper;
    @Mock private StockLedger ledger;
    @Mock private StockFlowWriter flowWriter;
    @Mock private FeedMapper feedMapper;
    @Mock private DrugMapper drugMapper;
    @Mock private DrugLotMapper drugLotMapper;
    @Mock private AccountQueryService accountQueryService;
    @Mock private OperationLogRecorder operationLogRecorder;

    private LossChangeHandler handler;
    private StockLoss loss;
    private Feed feed;
    private StockFlow flow;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(CLOCK);
        StockChangeSupport support = new StockChangeSupport(feedMapper, drugMapper, drugLotMapper,
                accountQueryService, operationLogRecorder);
        handler = new LossChangeHandler(lossMapper, flowMapper, ledger, flowWriter, support);
        loss = new StockLoss();
        loss.setId(6401L);
        loss.setFarmId(1001L);
        loss.setLossType("FEED_LOSS");
        loss.setFeedId(5101L);
        loss.setInputQty(new BigDecimal("10"));
        loss.setInputUnit("MEASURE");
        loss.setQty(new BigDecimal("10"));
        loss.setReason("MILDEW");
        loss.setLossDate(LocalDate.of(2026, 10, 15));
        loss.setUnitPrice(new BigDecimal("3.20"));
        loss.setAmount(new BigDecimal("32.00"));
        loss.setFlowId(7401L);
        loss.setStatus("NORMAL");
        loss.setVersion(1);
        loss.setCreatedBy(10001L);
        when(lossMapper.selectById(6401L)).thenReturn(loss);

        feed = new Feed();
        feed.setId(5101L);
        feed.setName("育肥全价料");
        feed.setUnit("KG");
        feed.setStockQty(new BigDecimal("490"));
        feed.setStockAmount(new BigDecimal("1568"));
        feed.setAvgPrice(new BigDecimal("3.2"));
        when(feedMapper.selectById(5101L)).thenReturn(feed);
        when(accountQueryService.getBrief(any())).thenAnswer(invocation ->
                new AccountBrief(invocation.getArgument(0), "王建国", "13800000000", "NORMAL", false));

        flow = new StockFlow();
        flow.setId(7401L);
        flow.setFeedId(5101L);
        flow.setQty(new BigDecimal("10"));
        flow.setAmount(new BigDecimal("32.00"));
        flow.setStockQtyAfter(new BigDecimal("490"));
        flow.setStockAmountAfter(new BigDecimal("1568"));
        flow.setStatus("NORMAL");
        when(flowMapper.selectById(7401L)).thenReturn(flow);
        when(ledger.adjustFeed(any(Feed.class), any(), any()))
                .thenReturn(new StockLedger.Move(new BigDecimal("495"), new BigDecimal("1584"),
                        new BigDecimal("3.2"), new BigDecimal("3.2"), new BigDecimal("16"), 9));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsLossAndInventoryModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.LOSS);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.INVENTORY);
    }

    @Test
    void loadRecordUsesLossDateAndMaterial() {
        ChangeRecord record = handler.loadRecord(BizType.LOSS, 6401L);

        assertThat(record.recordLocator()).isEqualTo("报损 · 10月15日");
        assertThat(record.recordTitle()).isEqualTo("报损 · 育肥全价料");
        assertThat(record.recordSubtitle()).isEqualTo("10月15日 · 王建国登记");
        assertThat(record.recordVersion()).isEqualTo("1");
        assertThat(handler.requiresRequest(record)).isTrue();
    }

    @Test
    void describeFormMatchesAppMock() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.LOSS, 6401L));

        assertThat(form.fields()).hasSize(1);
        ChangeField field = form.fields().get(0);
        assertThat(field.fieldCode()).isEqualTo("input_qty");
        assertThat(field.fieldName()).isEqualTo("报损数量");
        assertThat(field.value()).isEqualTo("10");
        assertThat(field.valueUnit()).isEqualTo("千克");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo("0");
        assertThat(field.max()).isEqualByComparingTo("500");
        assertThat(form.recordCard()).extracting(com.zhimu.server.biz.common.change.ChangeCardItem::label)
                .containsExactly("报损数量", "报损原因");
        assertThat(form.recordCard().get(0).value()).isEqualTo("10 千克");
        assertThat(form.recordCard().get(1).value()).isEqualTo("霉变");
    }

    @Test
    void validateChangeChecksQuantity() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.LOSS, 6401L));
        ChangeRecord record = handler.loadRecord(BizType.LOSS, 6401L);

        ChangeValidation validation = handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "5");
        assertThat(validation.afterValue()).isEqualTo("5");
        assertThat(validation.impacts()).containsExactly(ChangeImpact.INVENTORY);

        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "0"))
                .isInstanceOf(BizException.class).hasMessageContaining("必须大于 0");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "501"))
                .isInstanceOf(BizException.class).hasMessageContaining("不能超过当前可用结存");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "input_qty", "10"))
                .isInstanceOf(BizException.class).hasMessageContaining("新值与原值相同");
    }

    @Test
    void validateForApproveRejectsQuantityAboveAvailable() {
        ChangeRequest request = request("UPDATE", "{\"after\":{\"input_qty\":{\"value\":\"501\"}}}");

        assertThatThrownBy(() -> handler.validateForApprove(request,
                handler.loadRecord(BizType.LOSS, 6401L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过当前可用结存 500 千克");
    }

    @Test
    void applyUpdateAdjustsStockAndFlow() {
        ChangeRequest request = request("UPDATE", "{\"after\":{\"input_qty\":{\"value\":\"5\"}}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.LOSS, 6401L), 90001L);

        assertThat(result.recalcCount()).isEqualTo(2);
        ArgumentCaptor<BigDecimal> deltaQty = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> deltaAmount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ledger).adjustFeed(any(Feed.class), deltaQty.capture(), deltaAmount.capture());
        assertThat(deltaQty.getValue()).isEqualByComparingTo("5");
        assertThat(deltaAmount.getValue()).isEqualByComparingTo("16");
        ArgumentCaptor<StockLoss> saved = ArgumentCaptor.forClass(StockLoss.class);
        verify(lossMapper).updateById(saved.capture());
        assertThat(saved.getValue().getQty()).isEqualByComparingTo("5");
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("16");
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo(90001L);
        verify(flowMapper).updateById(any(StockFlow.class));
    }

    @Test
    void applyVoidRestoresStockAndVoidsFlow() {
        ChangeRequest request = request("VOID", "{\"after\":{}}");

        handler.apply(request, handler.loadRecord(BizType.LOSS, 6401L), 90001L);

        verify(ledger).adjustFeed(any(Feed.class), any(), any());
        verify(flowWriter).voidFlow(any(StockFlow.class), any());
        ArgumentCaptor<StockLoss> saved = ArgumentCaptor.forClass(StockLoss.class);
        verify(lossMapper).updateById(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("VOID");
    }

    private static ChangeRequest request(String changeType, String afterJson) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9601L);
        request.setBizType("LOSS");
        request.setBizId(6401L);
        request.setFarmId(1001L);
        request.setChangeType(changeType);
        request.setFieldCode("UPDATE".equals(changeType) ? "input_qty" : null);
        request.setFieldName("报损数量");
        request.setAfterJson(afterJson);
        request.setReason("报损数量登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
