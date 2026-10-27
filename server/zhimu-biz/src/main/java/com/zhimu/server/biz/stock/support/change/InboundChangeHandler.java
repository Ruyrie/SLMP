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
import com.zhimu.server.biz.finance.api.FinanceSourceWriteService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockInbound;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockInboundMapper;
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
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入库单（INBOUND）的修改申请适配：已发生出库的入库单才能提交修改申请，可改入库数量与单价。
 * 审核通过后按差额调整饲料结存 / 药品批号结存、更正入库流水与批次成本。表单与 App mock 一致。
 */
@Component
@RequiredArgsConstructor
public class InboundChangeHandler implements ChangeRequestHandler {

    public static final String FIELD_INPUT_QTY = "input_qty";
    public static final String FIELD_UNIT_PRICE = "unit_price";
    private static final List<String> RECALC_TAGS = List.of("库存均价", "出库成本", "批次物资成本", "收支流水");
    private static final List<ChangeImpact> IMPACTS = List.of(ChangeImpact.INVENTORY, ChangeImpact.COST,
            ChangeImpact.STATS);

    private final StockInboundMapper inboundMapper;
    private final StockFlowMapper flowMapper;
    private final StockLedger ledger;
    private final FinanceSourceWriteService financeWriter;
    private final StockChangeSupport support;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.INBOUND);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.INBOUND);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        StockInbound inbound = load(bizId);
        if (inbound == null) {
            return null;
        }
        String name = materialName(inbound);
        String locator = nullSafe(inbound.getInboundNo()) + " · " + StockChangeSupport.monthDay(inbound.getInboundDate());
        String title = (StockCodes.isFeed(inbound.getMaterialType()) ? "饲料入库 · " : "药品入库 · ")
                + (name == null ? "" : name);
        String subtitle = StockChangeSupport.monthDay(inbound.getInboundDate()) + " · "
                + nullSafe(support.userName(inbound.getCreatedBy())) + "登记";
        boolean voided = StockCodes.STATUS_VOID.equals(inbound.getStatus());
        return new ChangeRecord(BizType.INBOUND, inbound.getId(), inbound.getFarmId(), null, null,
                locator, title, subtitle, versionOf(inbound), false, voided);
    }

    /** 已发生出库的入库单不能直接改，必须走修改申请 */
    @Override
    public boolean requiresRequest(ChangeRecord record) {
        StockInbound inbound = load(record.bizId());
        return inbound != null && hasOutbound(inbound);
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        StockInbound inbound = load(record.bizId());
        if (inbound == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        StockChangeSupport.UnitInfo inputUnit = inputUnit(inbound);
        String measureUnit = measureUnit(inbound);
        List<ChangeField> fields = List.of(
                new ChangeField(FIELD_INPUT_QTY, "入库数量", StockChangeSupport.number(inbound.getInputQty()),
                        inputUnit.text(), ChangeField.NUMBER, List.of(), BigDecimal.ZERO,
                        new BigDecimal("99999999"), RECALC_TAGS, null),
                new ChangeField(FIELD_UNIT_PRICE, "单价", StockChangeSupport.priceText(inbound.getUnitPrice()),
                        "元/" + measureUnit, ChangeField.NUMBER, List.of(), BigDecimal.ZERO,
                        new BigDecimal("999999"), RECALC_TAGS, null));
        return new ChangeForm(
                List.of(new ChangeCardItem("入库数量",
                                StockChangeSupport.qtyText(inbound.getQty()) + " " + measureUnit),
                        new ChangeCardItem("单价", StockChangeSupport.priceText(inbound.getMeasurePrice())
                                + " 元/" + measureUnit)),
                fields,
                List.of("修改入库数量或单价会重新计算均价和已出库的批次成本"),
                "这条记录会影响库存和成本统计，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            return ChangeValidation.withoutField(IMPACTS, RECALC_TAGS);
        }
        ChangeRequestJson.requireField(form, fieldCode);
        ChangeField field = form.fieldOf(fieldCode);
        if (!FIELD_INPUT_QTY.equals(fieldCode) && !FIELD_UNIT_PRICE.equals(fieldCode)) {
            throw StockChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        BigDecimal value = parseDecimal(newValue);
        if (value == null) {
            throw StockChangeSupport.fieldError("new_value", "请填写数字");
        }
        if (value.signum() <= 0) {
            throw StockChangeSupport.fieldError("new_value",
                    FIELD_INPUT_QTY.equals(fieldCode) ? "入库数量必须大于 0" : "单价必须大于 0");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw StockChangeSupport.fieldError("new_value",
                    FIELD_INPUT_QTY.equals(fieldCode) ? "入库数量最多保留 2 位小数" : "单价最多保留 2 位小数");
        }
        if (value.compareTo(new BigDecimal(field.value())) == 0) {
            throw StockChangeSupport.fieldError("new_value", "新值与原值相同，不需要提交");
        }
        String normalized = FIELD_INPUT_QTY.equals(fieldCode)
                ? StockChangeSupport.number(value) : StockChangeSupport.priceText(value);
        return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(), field.value(),
                ChangeRequestJson.display(field.value(), field.valueUnit()), normalized,
                ChangeRequestJson.display(normalized, field.valueUnit()), IMPACTS, RECALC_TAGS);
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        if (!ChangeType.UPDATE.name().equals(request.getChangeType())) {
            return;
        }
        StockInbound inbound = load(record.bizId());
        if (inbound == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        String raw = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_INPUT_QTY);
        if (raw == null) {
            return;
        }
        BigDecimal inputQty = parseDecimal(raw);
        if (inputQty == null || inputQty.signum() <= 0) {
            throw StockChangeSupport.fieldError("new_value", "入库数量必须大于 0");
        }
        BigDecimal newQty = measureQty(inbound, inputQty);
        BigDecimal delta = newQty.subtract(StockQty.zeroIfNull(inbound.getQty()));
        if (delta.signum() < 0) {
            BigDecimal current = currentStock(inbound);
            if (current.compareTo(delta.abs()) < 0) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                        "库存不足，当前结存 " + StockChangeSupport.qtyText(current) + " "
                                + measureUnit(inbound) + "，不能把入库数量改到 " + StockChangeSupport.qtyText(newQty));
            }
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        StockInbound inbound = load(record.bizId());
        if (inbound == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        if (!ChangeType.UPDATE.name().equals(request.getChangeType())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "入库单不支持删除或作废");
        }
        BigDecimal oldInputQty = StockQty.zeroIfNull(inbound.getInputQty());
        BigDecimal oldQty = StockQty.zeroIfNull(inbound.getQty());
        BigDecimal oldAmount = StockQty.zeroIfNull(inbound.getAmount());
        BigDecimal oldPrice = StockQty.zeroIfNull(inbound.getUnitPrice());
        BigDecimal newInputQty = oldInputQty;
        BigDecimal newPrice = oldPrice;
        if (FIELD_INPUT_QTY.equals(request.getFieldCode())) {
            BigDecimal value = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_INPUT_QTY));
            if (value == null) {
                value = oldInputQty;
            }
            newInputQty = value;
        } else if (FIELD_UNIT_PRICE.equals(request.getFieldCode())) {
            BigDecimal value = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_UNIT_PRICE));
            if (value == null) {
                value = oldPrice;
            }
            newPrice = value;
        }
        BigDecimal newQty = measureQty(inbound, newInputQty);
        BigDecimal newAmount = StockQty.amount(newInputQty.multiply(newPrice));
        BigDecimal deltaQty = newQty.subtract(oldQty);
        BigDecimal deltaAmount = newAmount.subtract(oldAmount);
        BigDecimal measurePrice = newQty.signum() == 0 ? newPrice
                : newAmount.divide(newQty, StockQty.PRICE_SCALE, RoundingMode.HALF_UP);
        if (StockCodes.isFeed(inbound.getMaterialType())) {
            Feed feed = support.feed(inbound.getFeedId());
            if (feed == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "饲料档案不存在");
            }
            ledger.adjustFeed(feed, deltaQty, deltaAmount);
        } else {
            DrugLot lot = support.lot(inbound.getDrugLotId());
            if (lot == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "药品批号不存在");
            }
            Drug drug = support.drug(inbound.getDrugId());
            ledger.adjustLot(lot, deltaQty, deltaAmount,
                    StockCodes.unitText(drug == null ? null : drug.getUnit()));
        }
        inbound.setInputQty(newInputQty);
        inbound.setQty(newQty);
        inbound.setUnitPrice(newPrice);
        inbound.setAmount(newAmount);
        inbound.setMeasurePrice(measurePrice);
        inbound.setUpdatedBy(adminId);
        inbound.setFinanceRecordId(financeWriter.syncInboundExpense(new FinanceSourceWriteService.InboundExpense(
                inbound.getFarmId(), StockCodes.isFeed(inbound.getMaterialType()) ? FinanceConst.SOURCE_FEED : FinanceConst.SOURCE_DRUG,
                inbound.getId(), inbound.getInboundNo(), inbound.getAmount(), inbound.getInboundDate(),
                materialName(inbound) + " " + newQty.stripTrailingZeros().toPlainString() + " " + measureUnit(inbound),
                !Boolean.FALSE.equals(inbound.getIsSyncFinance()) && StockCodes.STATUS_NORMAL.equals(inbound.getStatus()), adminId)));
        inboundMapper.updateById(inbound);
        updateFlow(inbound, newQty, measurePrice, newAmount, deltaQty, deltaAmount);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        before.put(FIELD_INPUT_QTY, Map.of("name", "入库数量",
                "display", StockChangeSupport.qtyText(oldInputQty) + " " + inputUnit(inbound).text()));
        after.put(FIELD_INPUT_QTY, Map.of("name", "入库数量",
                "display", StockChangeSupport.qtyText(newInputQty) + " " + inputUnit(inbound).text()));
        support.record(request, record, BizType.INBOUND.name(), ChangeType.UPDATE,
                materialName(inbound), before, after);
        return new ChangeApplyResult(3, "已重新计算库存均价和出库成本");
    }

    private void updateFlow(StockInbound inbound, BigDecimal newQty, BigDecimal measurePrice, BigDecimal newAmount,
                            BigDecimal deltaQty, BigDecimal deltaAmount) {
        StockFlow flow = inbound.getFlowId() == null ? null : flowMapper.selectById(inbound.getFlowId());
        if (flow == null) {
            return;
        }
        flow.setQty(StockQty.qty(newQty));
        flow.setUnitPrice(measurePrice);
        flow.setAmount(newAmount);
        flow.setStockQtyAfter(StockQty.qty(StockQty.zeroIfNull(flow.getStockQtyAfter()).add(deltaQty)));
        flow.setStockAmountAfter(StockQty.amount(StockQty.zeroIfNull(flow.getStockAmountAfter()).add(deltaAmount)));
        flow.setRecalcAt(BeijingTime.now());
        flowMapper.updateById(flow);
    }

    private boolean hasOutbound(StockInbound inbound) {
        if (inbound.getFlowId() == null) {
            return false;
        }
        if (StockCodes.isFeed(inbound.getMaterialType())) {
            return flowMapper.countOutboundAfterFeed(inbound.getFlowId(), inbound.getFeedId()) > 0;
        }
        return flowMapper.countOutboundAfterLot(inbound.getFlowId(), inbound.getDrugLotId()) > 0;
    }

    private BigDecimal currentStock(StockInbound inbound) {
        if (StockCodes.isFeed(inbound.getMaterialType())) {
            Feed feed = support.feed(inbound.getFeedId());
            return feed == null ? BigDecimal.ZERO : StockQty.zeroIfNull(feed.getStockQty());
        }
        DrugLot lot = support.lot(inbound.getDrugLotId());
        return lot == null ? BigDecimal.ZERO : StockQty.zeroIfNull(lot.getStockQty());
    }

    private BigDecimal measureQty(StockInbound inbound, BigDecimal inputQty) {
        return support.measureQty(inputQty, inputUnit(inbound));
    }

    private StockChangeSupport.UnitInfo inputUnit(StockInbound inbound) {
        if (StockCodes.isFeed(inbound.getMaterialType())) {
            Feed feed = support.feed(inbound.getFeedId());
            return support.inputUnit(inbound.getInputUnit(), StockCodes.UNIT_KG,
                    feed == null ? null : feed.getPackUnit(), feed == null ? null : feed.getPackSize());
        }
        Drug drug = support.drug(inbound.getDrugId());
        return support.inputUnit(inbound.getInputUnit(), drug == null ? null : drug.getUnit(),
                drug == null ? null : drug.getPackUnit(), drug == null ? null : drug.getPackSize());
    }

    private String measureUnit(StockInbound inbound) {
        return StockCodes.isFeed(inbound.getMaterialType())
                ? StockCodes.unitText(StockCodes.UNIT_KG) : support.measureUnitText("DRUG", inbound.getDrugId());
    }

    private String materialName(StockInbound inbound) {
        return StockCodes.isFeed(inbound.getMaterialType())
                ? support.feedName(inbound.getFeedId()) : support.drugName(inbound.getDrugId());
    }

    private StockInbound load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> inboundMapper.selectById(bizId));
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

    private static String versionOf(StockInbound inbound) {
        return inbound.getVersion() == null ? "0" : String.valueOf(inbound.getVersion());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
