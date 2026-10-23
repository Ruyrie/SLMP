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
import com.zhimu.server.biz.stock.api.StockCostAllocation;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockOutboundMapper;
import com.zhimu.server.biz.stock.support.BatchAllocator;
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
 * 手工出库（OUTBOUND）的修改申请适配：次日或之后出库数量要改必须提交修改申请（领用还可能是批次已结算锁定）。
 * 审核通过后按差额更正库存流水、结存与批次成本分摊；删除 / 作废时全额退回库存并作废流水。
 * 表单与 App mock 一致（input_qty 出库数量）。
 */
@Component
@RequiredArgsConstructor
public class OutboundChangeHandler implements ChangeRequestHandler {

    public static final String FIELD_INPUT_QTY = "input_qty";
    private static final List<String> RECALC_TAGS = List.of("库存", "批次物资成本");

    private final StockOutboundMapper outboundMapper;
    private final StockFlowMapper flowMapper;
    private final StockLedger ledger;
    private final StockFlowWriter flowWriter;
    private final BatchAllocator allocator;
    private final StockChangeSupport support;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.OUTBOUND);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.OUTBOUND);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        StockOutbound outbound = load(bizId);
        if (outbound == null) {
            return null;
        }
        String name = materialName(outbound);
        String locator = nullSafe(outbound.getOutboundNo()) + " · "
                + StockChangeSupport.monthDay(outbound.getOutDate());
        String title = StockCodes.outTypeText(outbound.getOutType()) + " · " + (name == null ? "物资" : name);
        String subtitle = StockChangeSupport.monthDay(outbound.getOutDate()) + " · "
                + nullSafe(support.userName(outbound.getCreatedBy())) + "登记";
        boolean voided = StockCodes.STATUS_VOID.equals(outbound.getStatus());
        return new ChangeRecord(BizType.OUTBOUND, outbound.getId(), outbound.getFarmId(), outbound.getBatchId(),
                outbound.getPenId(), locator, title, subtitle, versionOf(outbound), false, voided);
    }

    /** 当天登记的出库单可以直接修改；次日起必须走修改申请 */
    @Override
    public boolean requiresRequest(ChangeRecord record) {
        StockOutbound outbound = load(record.bizId());
        if (outbound == null || outbound.getCreatedAt() == null) {
            return false;
        }
        return outbound.getCreatedAt().toLocalDate().isBefore(BeijingTime.today());
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        StockOutbound outbound = load(record.bizId());
        if (outbound == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        String unit = unitText(outbound);
        BigDecimal current = currentStock(outbound);
        BigDecimal max = current.add(StockQty.zeroIfNull(outbound.getQty()));
        ChangeField qty = new ChangeField(FIELD_INPUT_QTY, "出库数量",
                StockChangeSupport.number(outbound.getInputQty()), inputUnitText(outbound), ChangeField.NUMBER,
                List.of(), BigDecimal.ZERO, max, RECALC_TAGS, null);
        return new ChangeForm(
                List.of(new ChangeCardItem("出库数量",
                                StockChangeSupport.qtyText(outbound.getQty()) + " " + unit),
                        new ChangeCardItem("出库去向", directionText(outbound))),
                List.of(qty),
                List.of("修改出库数量会重新计算结存"),
                "这条记录会影响库存和成本统计，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                true, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            return ChangeValidation.withoutField(impacts(record), RECALC_TAGS);
        }
        ChangeRequestJson.requireField(form, fieldCode);
        ChangeField field = form.fieldOf(fieldCode);
        if (!FIELD_INPUT_QTY.equals(fieldCode)) {
            throw StockChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        BigDecimal qty = parseDecimal(newValue);
        if (qty == null || qty.signum() <= 0) {
            throw StockChangeSupport.fieldError("new_value", "出库数量必须大于 0");
        }
        if (qty.stripTrailingZeros().scale() > 2) {
            throw StockChangeSupport.fieldError("new_value", "出库数量最多保留 2 位小数");
        }
        if (field.max() != null && qty.compareTo(field.max()) > 0) {
            throw StockChangeSupport.fieldError("new_value",
                    "超过当前可用结存 " + StockChangeSupport.number(field.max()) + " " + field.valueUnit()
                            + "，出库后库存不能为负数");
        }
        if (qty.compareTo(new BigDecimal(StockChangeSupport.number(inputQty(record)))) == 0) {
            throw StockChangeSupport.fieldError("new_value", "新值与原值相同，不需要提交");
        }
        String normalized = StockChangeSupport.number(qty);
        return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(), field.value(),
                ChangeRequestJson.display(field.value(), field.valueUnit()), normalized,
                ChangeRequestJson.display(normalized, field.valueUnit()), impacts(record), RECALC_TAGS);
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        if (!ChangeType.UPDATE.name().equals(request.getChangeType())) {
            return;
        }
        StockOutbound outbound = load(record.bizId());
        if (outbound == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        BigDecimal inputQty = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(),
                FIELD_INPUT_QTY));
        if (inputQty == null || inputQty.signum() <= 0) {
            throw StockChangeSupport.fieldError("new_value", "出库数量必须大于 0");
        }
        BigDecimal newQty = support.measureQty(inputQty, inputUnit(outbound));
        BigDecimal available = currentStock(outbound).add(StockQty.zeroIfNull(outbound.getQty()));
        if (newQty.compareTo(available) > 0) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                    "超过当前可用结存 " + StockChangeSupport.qtyText(available) + " " + unitText(outbound)
                            + "，出库后库存不能为负数");
        }
        if (!StockCodes.isFeed(outbound.getMaterialType())) {
            DrugLot lot = support.lot(outbound.getDrugLotId());
            if (lot == null) {
                throw new BizException(ErrorCode.LOT_UNAVAILABLE, "该批号已过期或已停用，请换一个批号");
            }
            if (lot.getRecallId() != null || lot.getRecalledAt() != null) {
                throw new BizException(ErrorCode.LOT_UNAVAILABLE,
                        "批号 " + lot.getLotNo() + " 已被平台停用，不能出库，只能登记报废");
            }
            if (lot.getExpireDate() != null && outbound.getOutDate() != null
                    && lot.getExpireDate().isBefore(outbound.getOutDate())) {
                throw new BizException(ErrorCode.LOT_UNAVAILABLE,
                        "批号 " + lot.getLotNo() + " 已过期，不能出库，只能登记报废");
            }
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        StockOutbound outbound = load(record.bizId());
        if (outbound == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        ChangeType changeType = changeTypeOf(request.getChangeType());
        String unit = unitText(outbound);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (changeType == ChangeType.UPDATE) {
            BigDecimal oldInputQty = StockQty.zeroIfNull(outbound.getInputQty());
            BigDecimal oldQty = StockQty.zeroIfNull(outbound.getQty());
            BigDecimal oldAmount = StockQty.zeroIfNull(outbound.getAmount());
            BigDecimal newInputQty = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(),
                    FIELD_INPUT_QTY));
            if (newInputQty == null) {
                newInputQty = oldInputQty;
            }
            BigDecimal newQty = support.measureQty(newInputQty, inputUnit(outbound));
            BigDecimal avgPrice = avgPrice(outbound);
            BigDecimal newAmount = StockQty.amount(newQty.multiply(avgPrice));
            BigDecimal deltaQty = oldQty.subtract(newQty);
            BigDecimal deltaAmount = oldAmount.subtract(newAmount);
            adjust(outbound, deltaQty, deltaAmount);
            outbound.setInputQty(newInputQty);
            outbound.setQty(newQty);
            outbound.setAmount(newAmount);
            outbound.setMaxQty(StockQty.qty(currentStock(outbound).add(newQty)));
            outbound.setUpdatedBy(adminId);
            outboundMapper.updateById(outbound);
            updateFlow(outbound, newQty, newAmount, deltaQty, deltaAmount);
            before.put(FIELD_INPUT_QTY, Map.of("name", "出库数量",
                    "display", StockChangeSupport.qtyText(oldInputQty) + " " + inputUnitText(outbound)));
            after.put(FIELD_INPUT_QTY, Map.of("name", "出库数量",
                    "display", StockChangeSupport.qtyText(newInputQty) + " " + inputUnitText(outbound)));
        } else if (changeType == ChangeType.DELETE || changeType == ChangeType.VOID) {
            adjust(outbound, StockQty.zeroIfNull(outbound.getQty()), StockQty.zeroIfNull(outbound.getAmount()));
            if (outbound.getFlowId() != null) {
                StockFlow flow = flowMapper.selectById(outbound.getFlowId());
                if (flow != null) {
                    flowWriter.voidFlow(flow, StockCodes.VOID_SOURCE);
                }
            }
            outbound.setStatus(StockCodes.STATUS_VOID);
            outbound.setVoidAt(BeijingTime.now());
            outbound.setVoidBy(adminId);
            outbound.setUpdatedBy(adminId);
            outboundMapper.updateById(outbound);
            before.put("record_status", Map.of("name", "记录状态", "display", "有效"));
            after.put("record_status", Map.of("name", "记录状态",
                    "display", changeType == ChangeType.DELETE ? "已删除" : "已作废"));
        } else {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "不支持的修改类型");
        }
        support.record(request, record, BizType.OUTBOUND.name(), changeType, materialName(outbound),
                before, after);
        return new ChangeApplyResult(2, "已重新计算出库数量和结存");
    }

    private void adjust(StockOutbound outbound, BigDecimal deltaQty, BigDecimal deltaAmount) {
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            Feed feed = support.feed(outbound.getFeedId());
            if (feed == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "饲料档案不存在");
            }
            ledger.adjustFeed(feed, deltaQty, deltaAmount);
        } else {
            DrugLot lot = support.lot(outbound.getDrugLotId());
            if (lot == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "药品批号不存在");
            }
            Drug drug = support.drug(outbound.getDrugId());
            ledger.adjustLot(lot, deltaQty, deltaAmount, StockCodes.unitText(drug == null ? null : drug.getUnit()));
        }
    }

    private void updateFlow(StockOutbound outbound, BigDecimal newQty, BigDecimal newAmount, BigDecimal deltaQty,
                            BigDecimal deltaAmount) {
        StockFlow flow = outbound.getFlowId() == null ? null : flowMapper.selectById(outbound.getFlowId());
        if (flow == null) {
            return;
        }
        flow.setQty(StockQty.qty(newQty));
        flow.setAmount(newAmount);
        flow.setStockQtyAfter(StockQty.qty(StockQty.zeroIfNull(flow.getStockQtyAfter()).add(deltaQty)));
        flow.setStockAmountAfter(StockQty.amount(StockQty.zeroIfNull(flow.getStockAmountAfter()).add(deltaAmount)));
        flow.setRecalcAt(BeijingTime.now());
        flowMapper.updateById(flow);
        if (StockCodes.OUT_USE.equals(outbound.getOutType())) {
            flowWriter.voidCostsByFlow(flow.getId());
            List<StockCostAllocation> allocations = allocator.allocate(outbound.getFarmId(),
                    outbound.getBatchId(), outbound.getPenId(), newAmount);
            flowWriter.writeCosts(flow, outbound.getMaterialType(), outbound.getPenId(), allocations,
                    outbound.getOutDate());
        }
    }

    private List<ChangeImpact> impacts(ChangeRecord record) {
        StockOutbound outbound = record == null ? null : load(record.bizId());
        if (outbound != null && StockCodes.OUT_USE.equals(outbound.getOutType())) {
            return List.of(ChangeImpact.INVENTORY, ChangeImpact.COST);
        }
        return List.of(ChangeImpact.INVENTORY);
    }

    private String directionText(StockOutbound outbound) {
        if (StockCodes.OUT_OTHER.equals(outbound.getOutType())) {
            return StockCodes.destinationText(outbound.getDestination());
        }
        if (StockCodes.OUT_RETURN.equals(outbound.getOutType())) {
            return outbound.getReturnTo() == null ? StockCodes.returnReasonText(outbound.getReturnReason())
                    : outbound.getReturnTo();
        }
        return StockCodes.outTypeText(outbound.getOutType());
    }

    private BigDecimal currentStock(StockOutbound outbound) {
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            Feed feed = support.feed(outbound.getFeedId());
            return feed == null ? BigDecimal.ZERO : StockQty.zeroIfNull(feed.getStockQty());
        }
        DrugLot lot = support.lot(outbound.getDrugLotId());
        return lot == null ? BigDecimal.ZERO : StockQty.zeroIfNull(lot.getStockQty());
    }

    private BigDecimal avgPrice(StockOutbound outbound) {
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            Feed feed = support.feed(outbound.getFeedId());
            return feed == null ? BigDecimal.ZERO : StockQty.zeroIfNull(feed.getAvgPrice());
        }
        DrugLot lot = support.lot(outbound.getDrugLotId());
        return lot == null ? BigDecimal.ZERO : StockQty.zeroIfNull(lot.getAvgPrice());
    }

    private BigDecimal inputQty(ChangeRecord record) {
        StockOutbound outbound = record == null ? null : load(record.bizId());
        return outbound == null ? BigDecimal.ZERO : StockQty.zeroIfNull(outbound.getInputQty());
    }

    private StockChangeSupport.UnitInfo inputUnit(StockOutbound outbound) {
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            Feed feed = support.feed(outbound.getFeedId());
            return support.inputUnit(outbound.getInputUnit(), StockCodes.UNIT_KG,
                    feed == null ? null : feed.getPackUnit(), feed == null ? null : feed.getPackSize());
        }
        Drug drug = support.drug(outbound.getDrugId());
        return support.inputUnit(outbound.getInputUnit(), drug == null ? null : drug.getUnit(),
                drug == null ? null : drug.getPackUnit(), drug == null ? null : drug.getPackSize());
    }

    private String inputUnitText(StockOutbound outbound) {
        return inputUnit(outbound).text();
    }

    private String unitText(StockOutbound outbound) {
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            return StockCodes.unitText(StockCodes.UNIT_KG);
        }
        Drug drug = support.drug(outbound.getDrugId());
        return drug == null ? null : StockCodes.unitText(drug.getUnit());
    }

    private String materialName(StockOutbound outbound) {
        return StockCodes.isFeed(outbound.getMaterialType())
                ? support.feedName(outbound.getFeedId()) : support.drugName(outbound.getDrugId());
    }

    private StockOutbound load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> outboundMapper.selectById(bizId));
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

    private static String versionOf(StockOutbound outbound) {
        return outbound.getVersion() == null ? "0" : String.valueOf(outbound.getVersion());
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
