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
import com.zhimu.server.biz.stock.entity.Stocktake;
import com.zhimu.server.biz.stock.entity.StocktakeItem;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeItemMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 盘点修改申请：actual_qty 表单、实盘数量校验、审核后重算盘盈盘亏与单据汇总。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StocktakeChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private StocktakeMapper stocktakeMapper;
    @Mock private StocktakeItemMapper itemMapper;
    @Mock private StockFlowMapper flowMapper;
    @Mock private StockLedger ledger;
    @Mock private StockFlowWriter flowWriter;
    @Mock private FeedMapper feedMapper;
    @Mock private DrugMapper drugMapper;
    @Mock private DrugLotMapper drugLotMapper;
    @Mock private AccountQueryService accountQueryService;
    @Mock private OperationLogRecorder operationLogRecorder;

    private StocktakeChangeHandler handler;
    private Stocktake stocktake;
    private StocktakeItem first;
    private StocktakeItem second;

    @BeforeEach
    void setUp() {
        com.zhimu.server.biz.common.support.TableInfos.ensure(com.zhimu.server.biz.stock.entity.StocktakeItem.class);
        BeijingTime.setClock(CLOCK);
        StockChangeSupport support = new StockChangeSupport(feedMapper, drugMapper, drugLotMapper,
                accountQueryService, operationLogRecorder);
        handler = new StocktakeChangeHandler(stocktakeMapper, itemMapper, flowMapper, ledger, flowWriter, support);
        stocktake = new Stocktake();
        stocktake.setId(6301L);
        stocktake.setFarmId(1001L);
        stocktake.setStocktakeNo("PD2610010001");
        stocktake.setStocktakeDate(LocalDate.of(2026, 10, 1));
        stocktake.setItemCount(2);
        stocktake.setGainCount(0);
        stocktake.setLossCount(2);
        stocktake.setGainAmount(BigDecimal.ZERO);
        stocktake.setLossAmount(new BigDecimal("484.80"));
        stocktake.setVersion(1);
        stocktake.setCreatedBy(10001L);
        when(stocktakeMapper.selectById(6301L)).thenReturn(stocktake);

        first = item(8001L, 5101L, "500", "488", "-12", "-38.40", "3.20", 7301L);
        second = item(8002L, 5102L, "2000", "1856", "-144", "-446.40", "3.10", null);
        when(itemMapper.selectByStocktakeId(6301L)).thenReturn(List.of(first, second));

        Feed feedA = new Feed();
        feedA.setId(5101L);
        feedA.setName("育肥全价料");
        feedA.setStockQty(new BigDecimal("488"));
        feedA.setStockAmount(new BigDecimal("1561.60"));
        feedA.setAvgPrice(new BigDecimal("3.2"));
        Feed feedB = new Feed();
        feedB.setId(5102L);
        feedB.setName("保育料");
        feedB.setStockQty(new BigDecimal("1856"));
        feedB.setStockAmount(new BigDecimal("5753.60"));
        feedB.setAvgPrice(new BigDecimal("3.1"));
        when(feedMapper.selectById(5101L)).thenReturn(feedA);
        when(feedMapper.selectById(5102L)).thenReturn(feedB);
        StockFlow flow = new StockFlow();
        flow.setId(7301L);
        flow.setFeedId(5101L);
        flow.setQty(new BigDecimal("12"));
        flow.setAmount(new BigDecimal("38.40"));
        flow.setStockQtyAfter(new BigDecimal("488"));
        flow.setStockAmountAfter(new BigDecimal("1561.60"));
        flow.setStatus("NORMAL");
        when(flowMapper.selectById(7301L)).thenReturn(flow);
        when(ledger.adjustFeed(any(Feed.class), any(), any()))
                .thenReturn(new StockLedger.Move(new BigDecimal("450"), new BigDecimal("1440"),
                        new BigDecimal("3.2"), new BigDecimal("3.2"), new BigDecimal("-121.60"), 8));
        when(accountQueryService.getBrief(any())).thenAnswer(invocation ->
                new AccountBrief(invocation.getArgument(0), "王建国", "13800000000", "NORMAL", false));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsStocktakeAndInventoryModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.STOCKTAKE);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.INVENTORY);
    }

    @Test
    void loadRecordUsesStocktakeNoAndDate() {
        ChangeRecord record = handler.loadRecord(BizType.STOCKTAKE, 6301L);

        assertThat(record.recordLocator()).isEqualTo("PD2610010001 · 10月1日");
        assertThat(record.recordTitle()).isEqualTo("盘点 · 10月1日");
        assertThat(record.recordSubtitle()).isEqualTo("10月1日 · 王建国盘点");
        assertThat(record.recordVersion()).isEqualTo("1");
        assertThat(handler.requiresRequest(record)).isTrue();
    }

    @Test
    void describeFormMatchesAppMock() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.STOCKTAKE, 6301L));

        assertThat(form.fields()).hasSize(2);
        ChangeField field = form.fields().get(0);
        assertThat(field.fieldCode()).isEqualTo("actual_qty");
        assertThat(field.fieldName()).isEqualTo("育肥全价料实盘数量");
        assertThat(field.value()).isEqualTo("488");
        assertThat(field.valueUnit()).isEqualTo("千克");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo("0");
        assertThat(field.max()).isEqualByComparingTo("99999999");
        assertThat(form.fields().get(1).fieldCode()).isEqualTo("actual_qty_8002");
        assertThat(form.recordCard()).extracting(com.zhimu.server.biz.common.change.ChangeCardItem::label)
                .containsExactly("育肥全价料", "保育料");
        assertThat(form.recordCard().get(0).value()).isEqualTo("实盘 488 千克");
        assertThat(form.recordCard().get(1).value()).isEqualTo("实盘 1,856 千克");
    }

    @Test
    void validateChangeChecksActualQty() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.STOCKTAKE, 6301L));
        ChangeRecord record = handler.loadRecord(BizType.STOCKTAKE, 6301L);

        ChangeValidation validation = handler.validateChange(record, form, ChangeType.UPDATE,
                "actual_qty", "450");
        assertThat(validation.afterValue()).isEqualTo("450");
        assertThat(validation.impacts()).containsExactly(ChangeImpact.INVENTORY);

        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "actual_qty", "-1"))
                .isInstanceOf(BizException.class).hasMessageContaining("不能小于 0");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "actual_qty", "488"))
                .isInstanceOf(BizException.class).hasMessageContaining("新值与原值相同");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "actual_qty_9999", "100"))
                .isInstanceOf(BizException.class).hasMessageContaining("请选择要修改的项目");
    }

    @Test
    void applyUpdateRecalculatesItemFlowAndTotals() {
        ChangeRequest request = request("actual_qty", "{\"after\":{\"actual_qty\":{\"value\":\"450\"}}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.STOCKTAKE, 6301L), 90001L);

        assertThat(result.recalcCount()).isEqualTo(2);
        ArgumentCaptor<BigDecimal> deltaQty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ledger).adjustFeed(any(Feed.class), deltaQty.capture(), any());
        assertThat(deltaQty.getValue()).isEqualByComparingTo("-38");
        ArgumentCaptor<StocktakeItem> savedItem = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(itemMapper).updateById(savedItem.capture());
        assertThat(savedItem.getValue().getActualQty()).isEqualByComparingTo("450");
        assertThat(savedItem.getValue().getDiffQty()).isEqualByComparingTo("-50");
        assertThat(savedItem.getValue().getResult()).isEqualTo("LOSS");
        ArgumentCaptor<Stocktake> savedSheet = ArgumentCaptor.forClass(Stocktake.class);
        verify(stocktakeMapper).updateById(savedSheet.capture());
        assertThat(savedSheet.getValue().getLossCount()).isEqualTo(2);
        assertThat(savedSheet.getValue().getLossAmount()).isEqualByComparingTo("606.40");
        verify(flowMapper).updateById(any(StockFlow.class));
    }

    private static StocktakeItem item(Long id, Long feedId, String book, String actual, String diff,
                                      String diffAmount, String avgPrice, Long flowId) {
        StocktakeItem item = new StocktakeItem();
        item.setId(id);
        item.setStocktakeId(6301L);
        item.setFarmId(1001L);
        item.setFeedId(feedId);
        item.setBookQty(new BigDecimal(book));
        item.setActualQty(new BigDecimal(actual));
        item.setDiffQty(new BigDecimal(diff));
        item.setResult("LOSS");
        item.setAvgPrice(new BigDecimal(avgPrice));
        item.setDiffAmount(new BigDecimal(diffAmount));
        item.setFlowId(flowId);
        return item;
    }

    private static ChangeRequest request(String fieldCode, String afterJson) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9501L);
        request.setBizType("STOCKTAKE");
        request.setBizId(6301L);
        request.setFarmId(1001L);
        request.setChangeType("UPDATE");
        request.setFieldCode(fieldCode);
        request.setFieldName("育肥全价料实盘数量");
        request.setAfterJson(afterJson);
        request.setReason("盘点数量登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
