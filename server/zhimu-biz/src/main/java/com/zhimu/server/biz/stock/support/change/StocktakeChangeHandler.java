package com.zhimu.server.biz.stock.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestHandler;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.Stocktake;
import com.zhimu.server.biz.stock.entity.StocktakeItem;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeItemMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeMapper;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 盘点单（STOCKTAKE）的修改申请适配：已确认的盘点只能提交修改申请，主修改项 actual_qty（某个饲料的实盘数量）。
 * 审核通过后按差额调整饲料结存与金额、更正盘盈 / 盘亏流水，并重算单据汇总。表单与 App mock 一致。
 */
@Component
@RequiredArgsConstructor
public class StocktakeChangeHandler implements ChangeRequestHandler {

    public static final String FIELD_ACTUAL_QTY = "actual_qty";
    private static final String FIELD_ACTUAL_QTY_PREFIX = "actual_qty_";
    private static final BigDecimal MAX_QTY = new BigDecimal("99999999");
    private static final List<String> RECALC_TAGS = List.of("库存", "盘点差异");

    private final StocktakeMapper stocktakeMapper;
    private final StocktakeItemMapper itemMapper;
    private final StockFlowMapper flowMapper;
    private final StockLedger ledger;
    private final StockFlowWriter flowWriter;
    private final StockChangeSupport support;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.STOCKTAKE);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.STOCKTAKE);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        Stocktake stocktake = load(bizId);
        if (stocktake == null) {
            return null;
        }
        String date = StockChangeSupport.monthDay(stocktake.getStocktakeDate());
        String locator = nullSafe(stocktake.getStocktakeNo()) + " · " + date;
        String title = "盘点 · " + date;
        String subtitle = date + " · " + nullSafe(support.userName(stocktake.getCreatedBy())) + "盘点";
        return new ChangeRecord(BizType.STOCKTAKE, stocktake.getId(), stocktake.getFarmId(), null, null,
                locator, title, subtitle, versionOf(stocktake), false, false);
    }

    /** 已确认的盘点任何时候都只能提交修改申请 */
    @Override
    public boolean requiresRequest(ChangeRecord record) {
        return load(record.bizId()) != null;
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        Stocktake stocktake = load(record.bizId());
        if (stocktake == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        List<StocktakeItem> items = items(stocktake.getId());
        List<ChangeCardItem> card = new ArrayList<>();
        List<ChangeField> fields = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            StocktakeItem item = items.get(i);
            String feedName = support.feedName(item.getFeedId());
            String name = (feedName == null ? "饲料" : feedName) + "实盘数量";
            card.add(new ChangeCardItem(feedName == null ? "饲料" : feedName,
                    "实盘 " + StockChangeSupport.qtyText(item.getActualQty()) + " 千克"));
            String code = i == 0 ? FIELD_ACTUAL_QTY : FIELD_ACTUAL_QTY_PREFIX + item.getId();
            fields.add(new ChangeField(code, name, StockChangeSupport.number(item.getActualQty()),
                    "千克", ChangeField.NUMBER, List.of(), BigDecimal.ZERO, MAX_QTY, RECALC_TAGS, null));
        }
        return new ChangeForm(card, fields,
                List.of("修改实盘数量会重新生成盘盈、盘亏记录"),
                "这条记录会影响库存和成本统计，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            return ChangeValidation.withoutField(List.of(ChangeImpact.INVENTORY), RECALC_TAGS);
        }
        ChangeRequestJson.requireField(form, fieldCode);
        ChangeField field = form.fieldOf(fieldCode);
        BigDecimal actual = parseDecimal(newValue);
        if (actual == null) {
            throw StockChangeSupport.fieldError("new_value", "请填写数字");
        }
        if (actual.signum() < 0) {
            throw StockChangeSupport.fieldError("new_value", "实盘数量不能小于 0");
        }
        if (actual.stripTrailingZeros().scale() > 2) {
            throw StockChangeSupport.fieldError("new_value", "实盘数量最多保留 2 位小数");
        }
        if (actual.compareTo(new BigDecimal(field.value())) == 0) {
            throw StockChangeSupport.fieldError("new_value", "新值与原值相同，不需要提交");
        }
        String normalized = StockChangeSupport.number(actual);
        return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(), field.value(),
                ChangeRequestJson.display(field.value(), field.valueUnit()), normalized,
                ChangeRequestJson.display(normalized, field.valueUnit()),
                List.of(ChangeImpact.INVENTORY), RECALC_TAGS);
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        if (!ChangeType.UPDATE.name().equals(request.getChangeType())) {
            return;
        }
        Stocktake stocktake = load(record.bizId());
        if (stocktake == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        List<StocktakeItem> items = items(stocktake.getId());
        StocktakeItem item = itemOf(items, request.getFieldCode());
        if (item == null) {
            throw StockChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        String raw = ChangeRequestJson.afterValue(request.getAfterJson(), request.getFieldCode());
        BigDecimal actual = parseDecimal(raw);
        if (actual == null || actual.signum() < 0) {
            throw StockChangeSupport.fieldError("new_value", "实盘数量不能小于 0");
        }
        Feed feed = support.feed(item.getFeedId());
        BigDecimal current = feed == null ? BigDecimal.ZERO : StockQty.zeroIfNull(feed.getStockQty());
        BigDecimal delta = actual.subtract(StockQty.zeroIfNull(item.getActualQty()));
        if (delta.signum() < 0 && current.compareTo(delta.abs()) < 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                    "库存不足，当前结存 " + StockChangeSupport.qtyText(current) + " 千克，不能把实盘数量改到 "
                            + StockChangeSupport.qtyText(actual));
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        Stocktake stocktake = load(record.bizId());
        if (stocktake == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        if (!ChangeType.UPDATE.name().equals(request.getChangeType())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "盘点单不支持删除或作废");
        }
        List<StocktakeItem> items = items(stocktake.getId());
        StocktakeItem item = itemOf(items, request.getFieldCode());
        if (item == null) {
            throw StockChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        BigDecimal actual = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(),
                request.getFieldCode()));
        if (actual == null || actual.signum() < 0) {
            throw StockChangeSupport.fieldError("new_value", "实盘数量不能小于 0");
        }
        Feed feed = support.feed(item.getFeedId());
        if (feed == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "饲料档案不存在");
        }
        BigDecimal bookQty = StockQty.zeroIfNull(item.getBookQty());
        BigDecimal oldActual = StockQty.zeroIfNull(item.getActualQty());
        BigDecimal oldDiff = StockQty.zeroIfNull(item.getDiffQty());
        BigDecimal oldDiffAmount = StockQty.zeroIfNull(item.getDiffAmount());
        BigDecimal avgPrice = StockQty.zeroIfNull(item.getAvgPrice());
        BigDecimal newDiff = StockQty.qty(actual.subtract(bookQty));
        BigDecimal newDiffAmount = StockQty.amount(newDiff.multiply(avgPrice));
        String result = newDiff.signum() > 0 ? StockCodes.RESULT_GAIN
                : newDiff.signum() < 0 ? StockCodes.RESULT_LOSS : StockCodes.RESULT_MATCH;
        BigDecimal deltaQty = actual.subtract(oldActual);
        BigDecimal deltaAmount = newDiffAmount.subtract(oldDiffAmount);
        ledger.adjustFeed(feed, deltaQty, deltaAmount);

        item.setActualQty(actual);
        item.setDiffQty(newDiff);
        item.setDiffAmount(newDiffAmount);
        item.setResult(result);
        itemMapper.updateById(item);
        updateFlow(stocktake, item, newDiff, newDiffAmount, avgPrice, deltaQty, deltaAmount);
        recalcTotals(stocktake);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        before.put(request.getFieldCode(), Map.of("name", itemName(feed),
                "display", StockChangeSupport.qtyText(oldActual) + " 千克"));
        after.put(request.getFieldCode(), Map.of("name", itemName(feed),
                "display", StockChangeSupport.qtyText(actual) + " 千克"));
        support.record(request, record, BizType.STOCKTAKE.name(), ChangeType.UPDATE,
                stocktake.getStocktakeNo(), before, after);
        return new ChangeApplyResult(2, "已重新计算盘点差异和库存");
    }

    private void updateFlow(Stocktake stocktake, StocktakeItem item, BigDecimal newDiff, BigDecimal newDiffAmount,
                            BigDecimal avgPrice, BigDecimal deltaQty, BigDecimal deltaAmount) {
        StockFlow flow = item.getFlowId() == null ? null : flowMapper.selectById(item.getFlowId());
        if (flow != null && newDiff.signum() == 0) {
            flowWriter.voidFlow(flow, StockCodes.VOID_CORRECTED);
            item.setFlowId(null);
            itemMapper.updateById(item);
            return;
        }
        if (flow != null) {
            flow.setQty(StockQty.qty(newDiff.abs()));
            flow.setDirection(newDiff.signum() > 0 ? StockCodes.DIRECTION_IN : StockCodes.DIRECTION_OUT);
            flow.setFlowType(newDiff.signum() > 0 ? StockCodes.FLOW_STOCKTAKE_GAIN : StockCodes.FLOW_STOCKTAKE_LOSS);
            flow.setUnitPrice(StockQty.price(avgPrice));
            flow.setAmount(StockQty.amount(newDiffAmount.abs()));
            flow.setStockQtyAfter(StockQty.qty(StockQty.zeroIfNull(flow.getStockQtyAfter()).add(deltaQty)));
            flow.setStockAmountAfter(StockQty.amount(StockQty.zeroIfNull(flow.getStockAmountAfter())
                    .add(deltaAmount)));
            flow.setRecalcAt(BeijingTime.now());
            flowMapper.updateById(flow);
            return;
        }
        if (newDiff.signum() == 0) {
            return;
        }
        StockFlow created = new StockFlow();
        created.setFarmId(stocktake.getFarmId());
        created.setMaterialType(StockCodes.MATERIAL_FEED);
        created.setFeedId(item.getFeedId());
        created.setFlowType(newDiff.signum() > 0 ? StockCodes.FLOW_STOCKTAKE_GAIN : StockCodes.FLOW_STOCKTAKE_LOSS);
        created.setDirection(newDiff.signum() > 0 ? StockCodes.DIRECTION_IN : StockCodes.DIRECTION_OUT);
        created.setQty(StockQty.qty(newDiff.abs()));
        created.setUnitPrice(StockQty.price(avgPrice));
        created.setAmount(StockQty.amount(newDiffAmount.abs()));
        created.setStockQtyAfter(StockQty.qty(StockQty.zeroIfNull(support.feed(item.getFeedId()).getStockQty())));
        created.setStockAmountAfter(StockQty.amount(
                StockQty.zeroIfNull(support.feed(item.getFeedId()).getStockAmount())));
        created.setBizType(StockCodes.BIZ_STOCKTAKE);
        created.setBizId(stocktake.getId());
        created.setOccurAt(BeijingTime.now());
        created.setBizDate(stocktake.getStocktakeDate());
        created.setIsBatchCost(false);
        created.setIsFeedUsage(false);
        StockFlow saved = flowWriter.record(created);
        item.setFlowId(saved.getId());
        itemMapper.updateById(item);
    }

    private void recalcTotals(Stocktake stocktake) {
        List<StocktakeItem> rows = items(stocktake.getId());
        int gainCount = 0;
        int lossCount = 0;
        BigDecimal gainAmount = BigDecimal.ZERO;
        BigDecimal lossAmount = BigDecimal.ZERO;
        for (StocktakeItem row : rows) {
            if (StockCodes.RESULT_GAIN.equals(row.getResult())) {
                gainCount++;
                gainAmount = gainAmount.add(StockQty.zeroIfNull(row.getDiffAmount()).abs());
            } else if (StockCodes.RESULT_LOSS.equals(row.getResult())) {
                lossCount++;
                lossAmount = lossAmount.add(StockQty.zeroIfNull(row.getDiffAmount()).abs());
            }
        }
        stocktake.setItemCount(rows.size());
        stocktake.setGainCount(gainCount);
        stocktake.setLossCount(lossCount);
        stocktake.setGainAmount(StockQty.amount(gainAmount));
        stocktake.setLossAmount(StockQty.amount(lossAmount));
        stocktakeMapper.updateById(stocktake);
    }

    private List<StocktakeItem> items(Long stocktakeId) {
        List<StocktakeItem> rows = itemMapper.selectByStocktakeId(stocktakeId);
        return rows == null ? List.of() : rows;
    }

    private StocktakeItem itemOf(List<StocktakeItem> items, String fieldCode) {
        if (fieldCode == null || items.isEmpty()) {
            return null;
        }
        if (FIELD_ACTUAL_QTY.equals(fieldCode)) {
            return items.get(0);
        }
        if (fieldCode.startsWith(FIELD_ACTUAL_QTY_PREFIX)) {
            String id = fieldCode.substring(FIELD_ACTUAL_QTY_PREFIX.length());
            for (StocktakeItem item : items) {
                if (item.getId() != null && String.valueOf(item.getId()).equals(id)) {
                    return item;
                }
            }
        }
        return null;
    }

    private String itemName(Feed feed) {
        return feed == null ? "实盘数量" : feed.getName() + "实盘数量";
    }

    private Stocktake load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> stocktakeMapper.selectById(bizId));
    }

    private static BigDecimal parseDecimal(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String versionOf(Stocktake stocktake) {
        return stocktake.getVersion() == null ? "0" : String.valueOf(stocktake.getVersion());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
