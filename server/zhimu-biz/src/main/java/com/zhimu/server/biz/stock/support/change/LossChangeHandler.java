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
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockLoss;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockLossMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报损 / 报废（LOSS）的修改申请适配：登记后任何时候修改数量、原因、说明都要走修改申请。
 * 审核通过后按差额更正库存流水与结存；删除 / 作废时全额退回库存并作废流水。表单与 App mock 一致（input_qty）。
 */
@Component
@RequiredArgsConstructor
public class LossChangeHandler implements ChangeRequestHandler {

    public static final String FIELD_INPUT_QTY = "input_qty";
    private static final List<String> RECALC_TAGS = List.of("库存");
    private static final List<ChangeImpact> IMPACTS = List.of(ChangeImpact.INVENTORY);

    private final StockLossMapper lossMapper;
    private final StockFlowMapper flowMapper;
    private final StockLedger ledger;
    private final StockFlowWriter flowWriter;
    private final StockChangeSupport support;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.LOSS);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.LOSS);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        StockLoss loss = load(bizId);
        if (loss == null) {
            return null;
        }
        boolean feed = StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType());
        String name = materialName(loss);
        String action = feed ? "报损" : "报废";
        String date = StockChangeSupport.monthDay(loss.getLossDate());
        String locator = action + " · " + date;
        String title = action + " · " + (name == null ? "物资" : name);
        String subtitle = date + " · " + nullSafe(support.userName(loss.getCreatedBy())) + "登记";
        boolean voided = StockCodes.STATUS_VOID.equals(loss.getStatus());
        return new ChangeRecord(BizType.LOSS, loss.getId(), loss.getFarmId(), null, null, locator, title,
                subtitle, versionOf(loss), false, voided);
    }

    /** 报损 / 报废登记后任何时候都要走修改申请 */
    @Override
    public boolean requiresRequest(ChangeRecord record) {
        return load(record.bizId()) != null;
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        StockLoss loss = load(record.bizId());
        if (loss == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        boolean feed = StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType());
        String unit = unitText(loss);
        String action = feed ? "报损" : "报废";
        String reason = feed ? StockCodes.feedLossReasonText(loss.getReason())
                : StockCodes.scrapReasonText(loss.getReason());
        BigDecimal max = StockQty.zeroIfNull(currentStock(loss)).add(StockQty.zeroIfNull(loss.getQty()));
        max = max.divide(inputUnit(loss).factor(), feed ? 2 : StockQty.QTY_SCALE,
                java.math.RoundingMode.DOWN).stripTrailingZeros();
        ChangeField qty = new ChangeField(FIELD_INPUT_QTY, action + "数量",
                StockChangeSupport.number(loss.getInputQty()), inputUnitText(loss), ChangeField.NUMBER,
                List.of(), BigDecimal.ZERO, max, RECALC_TAGS, null);
        return new ChangeForm(
                List.of(new ChangeCardItem(action + "数量",
                                StockChangeSupport.qtyText(loss.getQty()) + " " + unit),
                        new ChangeCardItem(action + "原因", reason)),
                List.of(qty),
                List.of("修改" + action + "数量会重新计算结存"),
                "这条记录会影响库存和成本统计，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                true, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            return ChangeValidation.withoutField(IMPACTS, RECALC_TAGS);
        }
        ChangeRequestJson.requireField(form, fieldCode);
        ChangeField field = form.fieldOf(fieldCode);
        if (!FIELD_INPUT_QTY.equals(fieldCode)) {
            throw StockChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        BigDecimal qty = parseDecimal(newValue);
        if (qty == null || qty.signum() <= 0) {
            throw StockChangeSupport.fieldError("new_value", "数量必须大于 0");
        }
        StockLoss loss = load(record.bizId());
        checkedMeasureQty(loss, qty);
        if (field.max() != null && qty.compareTo(field.max()) > 0) {
            throw StockChangeSupport.fieldError("new_value",
                    "不能超过当前可用结存 " + StockChangeSupport.number(field.max()) + " " + field.valueUnit());
        }
        if (qty.compareTo(StockQty.zeroIfNull(loss.getInputQty())) == 0) {
            throw StockChangeSupport.fieldError("new_value", "新值与原值相同，不需要提交");
        }
        String normalized = StockChangeSupport.number(qty);
        return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(), field.value(),
                ChangeRequestJson.display(field.value(), field.valueUnit()), normalized,
                ChangeRequestJson.display(normalized, field.valueUnit()), IMPACTS, RECALC_TAGS);
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        if (!ChangeType.UPDATE.name().equals(request.getChangeType())) {
            return;
        }
        StockLoss loss = load(record.bizId());
        if (loss == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        String raw = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_INPUT_QTY);
        BigDecimal qty = parseDecimal(raw);
        if (qty == null || qty.signum() <= 0) {
            throw StockChangeSupport.fieldError("new_value", "数量必须大于 0");
        }
        BigDecimal available = StockQty.zeroIfNull(currentStock(loss)).add(StockQty.zeroIfNull(loss.getQty()));
        if (checkedMeasureQty(loss, qty).compareTo(available) > 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                    "超过当前可用结存 " + StockChangeSupport.qtyText(available) + " " + unitText(loss)
                            + "，出库后库存不能为负数");
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        StockLoss loss = load(record.bizId());
        if (loss == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        ChangeType changeType = changeTypeOf(request.getChangeType());
        String unit = unitText(loss);
        boolean feed = StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (changeType == ChangeType.UPDATE) {
            BigDecimal oldInputQty = StockQty.zeroIfNull(loss.getInputQty());
            BigDecimal oldQty = StockQty.zeroIfNull(loss.getQty());
            BigDecimal oldAmount = StockQty.zeroIfNull(loss.getAmount());
            BigDecimal newInputQty = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(),
                    FIELD_INPUT_QTY));
            if (newInputQty == null) {
                newInputQty = oldInputQty;
            }
            BigDecimal newQty = checkedMeasureQty(loss, newInputQty);
            BigDecimal unitPrice = StockQty.zeroIfNull(loss.getUnitPrice());
            BigDecimal available = StockQty.zeroIfNull(currentStock(loss)).add(oldQty);
            BigDecimal newAmount = newQty.compareTo(available) == 0
                    ? StockQty.amount(currentStockAmount(loss).add(oldAmount))
                    : StockQty.amount(newQty.multiply(unitPrice));
            BigDecimal deltaQty = oldQty.subtract(newQty);
            BigDecimal deltaAmount = oldAmount.subtract(newAmount);
            adjust(loss, deltaQty, deltaAmount);
            loss.setInputQty(newInputQty);
            loss.setQty(newQty);
            loss.setAmount(newAmount);
            loss.setUpdatedBy(adminId);
            lossMapper.updateById(loss);
            updateFlow(loss, newQty, newAmount, deltaQty, deltaAmount);
            before.put(FIELD_INPUT_QTY, Map.of("name", "数量",
                    "display", StockChangeSupport.qtyText(oldQty) + " " + unit));
            after.put(FIELD_INPUT_QTY, Map.of("name", "数量",
                    "display", StockChangeSupport.qtyText(newQty) + " " + unit));
        } else if (changeType == ChangeType.DELETE || changeType == ChangeType.VOID) {
            adjust(loss, StockQty.zeroIfNull(loss.getQty()), StockQty.zeroIfNull(loss.getAmount()));
            if (loss.getFlowId() != null) {
                StockFlow flow = flowMapper.selectById(loss.getFlowId());
                if (flow != null) {
                    flowWriter.voidFlow(flow, StockCodes.VOID_SOURCE);
                }
            }
            loss.setStatus(StockCodes.STATUS_VOID);
            loss.setUpdatedBy(adminId);
            lossMapper.updateById(loss);
            before.put("record_status", Map.of("name", "记录状态", "display", "有效"));
            after.put("record_status", Map.of("name", "记录状态",
                    "display", changeType == ChangeType.DELETE ? "已删除" : "已作废"));
        } else {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "不支持的修改类型");
        }
        support.record(request, record, BizType.LOSS.name(), changeType, materialName(loss), before, after);
        return new ChangeApplyResult(2, "已重新计算报损数量和库存");
    }

    private void adjust(StockLoss loss, BigDecimal deltaQty, BigDecimal deltaAmount) {
        if (StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType())) {
            Feed feed = support.feed(loss.getFeedId());
            if (feed == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "饲料档案不存在");
            }
            ledger.adjustFeed(feed, deltaQty, deltaAmount);
        } else {
            DrugLot lot = support.lot(loss.getDrugLotId());
            if (lot == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "药品批号不存在");
            }
            Drug drug = support.drug(lot.getDrugId());
            ledger.adjustLot(lot, deltaQty, deltaAmount, StockCodes.unitText(drug == null ? null : drug.getUnit()));
        }
    }

    private void updateFlow(StockLoss loss, BigDecimal newQty, BigDecimal newAmount, BigDecimal deltaQty,
                            BigDecimal deltaAmount) {
        StockFlow flow = loss.getFlowId() == null ? null : flowMapper.selectById(loss.getFlowId());
        if (flow == null) {
            return;
        }
        flow.setQty(StockQty.qty(newQty));
        flow.setAmount(newAmount);
        flow.setStockQtyAfter(StockQty.qty(StockQty.zeroIfNull(flow.getStockQtyAfter()).add(deltaQty)));
        flow.setStockAmountAfter(StockQty.amount(StockQty.zeroIfNull(flow.getStockAmountAfter()).add(deltaAmount)));
        flow.setRecalcAt(BeijingTime.now());
        flowMapper.updateById(flow);
    }

    private BigDecimal currentStock(StockLoss loss) {
        if (StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType())) {
            Feed feed = support.feed(loss.getFeedId());
            return feed == null ? BigDecimal.ZERO : StockQty.zeroIfNull(feed.getStockQty());
        }
        DrugLot lot = support.lot(loss.getDrugLotId());
        return lot == null ? BigDecimal.ZERO : StockQty.zeroIfNull(lot.getStockQty());
    }

    private BigDecimal currentStockAmount(StockLoss loss) {
        if (StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType())) {
            Feed feed = support.feed(loss.getFeedId());
            return feed == null ? BigDecimal.ZERO : StockQty.zeroIfNull(feed.getStockAmount());
        }
        DrugLot lot = support.lot(loss.getDrugLotId());
        return lot == null ? BigDecimal.ZERO : StockQty.zeroIfNull(lot.getStockAmount());
    }

    private BigDecimal checkedMeasureQty(StockLoss loss, BigDecimal input) {
        if (loss == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        if (input == null || input.signum() <= 0) {
            throw StockChangeSupport.fieldError("new_value", "数量必须大于 0");
        }
        int scale = StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType()) ? 2 : StockQty.QTY_SCALE;
        if (input.stripTrailingZeros().scale() > scale) {
            throw StockChangeSupport.fieldError("new_value", "数量最多保留 " + scale + " 位小数");
        }
        BigDecimal qty = input.multiply(inputUnit(loss).factor());
        if (qty.stripTrailingZeros().scale() > StockQty.QTY_SCALE) {
            throw StockChangeSupport.fieldError("new_value", "换算后的数量最多保留 3 位小数");
        }
        if (!StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType())) {
            DrugLot lot = support.lot(loss.getDrugLotId());
            Drug drug = lot == null ? null : support.drug(lot.getDrugId());
            if (drug != null && StockCodes.UNIT_DOSE.equals(drug.getUnit())
                    && qty.stripTrailingZeros().scale() > 0) {
                throw StockChangeSupport.fieldError("new_value", "头份必须是整数");
            }
        }
        return StockQty.qty(qty);
    }

    private StockChangeSupport.UnitInfo inputUnit(StockLoss loss) {
        if (StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType())) {
            Feed feed = support.feed(loss.getFeedId());
            return support.inputUnit(loss.getInputUnit(), StockCodes.UNIT_KG,
                    feed == null ? null : feed.getPackUnit(), feed == null ? null : feed.getPackSize());
        }
        DrugLot lot = support.lot(loss.getDrugLotId());
        Drug drug = lot == null ? null : support.drug(lot.getDrugId());
        return support.inputUnit(loss.getInputUnit(), drug == null ? null : drug.getUnit(),
                drug == null ? null : drug.getPackUnit(), drug == null ? null : drug.getPackSize());
    }

    private String inputUnitText(StockLoss loss) {
        return inputUnit(loss).text();
    }

    private String unitText(StockLoss loss) {
        if (StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType())) {
            return StockCodes.unitText(StockCodes.UNIT_KG);
        }
        DrugLot lot = support.lot(loss.getDrugLotId());
        Drug drug = lot == null ? null : support.drug(lot.getDrugId());
        return drug == null ? null : StockCodes.unitText(drug.getUnit());
    }

    private String materialName(StockLoss loss) {
        if (StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType())) {
            return support.feedName(loss.getFeedId());
        }
        DrugLot lot = support.lot(loss.getDrugLotId());
        Drug drug = lot == null ? null : support.drug(lot.getDrugId());
        return drug == null ? null : drug.getName();
    }

    private StockLoss load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> lossMapper.selectById(bizId));
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

    private static String versionOf(StockLoss loss) {
        return loss.getVersion() == null ? "0" : String.valueOf(loss.getVersion());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static ChangeType changeTypeOf(String code) {
        ChangeType type = ChangeRequestJson.parseChangeType(code);
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "修改类型不正确");
        }
        return type;
    }
}
