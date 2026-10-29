package com.zhimu.server.biz.stock.service.impl;

import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockLoss;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.RecallProgressMapper;
import com.zhimu.server.biz.stock.mapper.StockLossMapper;
import com.zhimu.server.biz.stock.request.DrugScrapRequest;
import com.zhimu.server.biz.stock.request.FeedLossRequest;
import com.zhimu.server.biz.stock.service.StockLossService;
import com.zhimu.server.biz.stock.support.StockActions;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.biz.stock.vo.StockLossVO;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 报损与报废实现（模块09 2.5）：报损按饲料当时均价减少结存与金额、固定当天；
 * 报废按批号当时均价减少结存，日期不早于首次入库日期；两者都不计入批次成本与饲料用量。
 */
@Service
@RequiredArgsConstructor
public class StockLossServiceImpl implements StockLossService {

    private static final Set<String> FEED_REASONS = Set.of(StockCodes.LOSS_MILDEW, StockCodes.LOSS_DAMP,
            StockCodes.LOSS_RODENT, StockCodes.LOSS_EXPIRED, StockCodes.LOSS_OTHER);
    private static final Set<String> SCRAP_REASONS = Set.of(StockCodes.SCRAP_PLATFORM_RECALL,
            StockCodes.SCRAP_EXPIRED, StockCodes.SCRAP_DAMAGED, StockCodes.SCRAP_OTHER);

    private final StockLossMapper lossMapper;
    private final FeedMapper feedMapper;
    private final DrugMapper drugMapper;
    private final StockLedger ledger;
    private final StockFlowWriter flowWriter;
    private final StockLookups lookups;
    private final RecallProgressMapper recallProgressMapper;

    @Override
    @Transactional
    public StockLossVO createFeedLoss(FeedLossRequest request, String requestId) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        Feed feed = lookups.requireFeed(request.getFeedId());
        if (!FEED_REASONS.contains(request.getReason())) {
            throw StockErrors.field("reason", "请选择报损原因");
        }
        if (StockCodes.LOSS_OTHER.equals(request.getReason()) && !StringUtils.hasText(request.getRemark())) {
            throw StockErrors.field("remark", "请写明报损原因");
        }
        if (request.getStockVersion() == null || !request.getStockVersion().equals(feed.getVersion())) {
            throw StockErrors.stockChanged(StockQty.plain(feed.getStockQty()) + " 千克",
                    StockQty.zeroIfNull(feed.getStockQty()));
        }
        BigDecimal qty = StockQty.qty(request.getInputQty());
        StockLedger.Move move = ledger.outFeed(feed, qty);

        StockLoss loss = new StockLoss();
        loss.setFarmId(farmId);
        loss.setLossType(StockCodes.LOSS_TYPE_FEED);
        loss.setFeedId(feed.getId());
        loss.setInputQty(request.getInputQty());
        loss.setInputUnit(StockCodes.INPUT_MEASURE);
        loss.setQty(qty);
        loss.setReason(request.getReason());
        loss.setRemark(trimToNull(request.getRemark()));
        loss.setLossDate(BeijingTime.today());
        loss.setUnitPrice(move.unitPrice());
        loss.setAmount(move.amount());
        loss.setStatus(StockCodes.STATUS_NORMAL);
        loss.setClientRequestId(requestId);
        loss.setVersion(0);
        loss.setCreatedBy(userId);
        loss.setUpdatedBy(userId);
        lossMapper.insert(loss);

        StockFlow flow = flow(loss, StockCodes.MATERIAL_FEED, feed.getId(), null, null, StockCodes.FLOW_LOSS,
                move, loss.getLossDate(), null);
        loss.setFlowId(flow.getId());
        lossMapper.updateById(loss);
        lookups.log("CREATE", "LOSS_CREATE", StockCodes.BIZ_LOSS, loss.getId(), feed.getName(), null,
                move.amount());
        return get(loss.getId());
    }

    @Override
    @Transactional
    public StockLossVO createDrugScrap(DrugScrapRequest request, String requestId) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        DrugLot lot = lookups.requireLot(request.getDrugLotId());
        Drug drug = lookups.requireDrug(lot.getDrugId());
        if (!SCRAP_REASONS.contains(request.getReason())) {
            throw StockErrors.field("reason", "请选择报废原因");
        }
        if (StockCodes.SCRAP_PLATFORM_RECALL.equals(request.getReason()) && lot.getRecalledAt() == null) {
            throw StockErrors.field("reason", "只有平台停用的批号才能选择“平台停用”");
        }
        if (StockCodes.SCRAP_OTHER.equals(request.getReason()) && !StringUtils.hasText(request.getRemark())) {
            throw StockErrors.field("remark", "请写明报废原因");
        }
        if (StockQty.zeroIfNull(lot.getStockQty()).signum() <= 0) {
            throw StockErrors.notEnough("批号 " + lot.getLotNo() + " 结存已为 0，不能报废");
        }
        String inputUnit = StringUtils.hasText(request.getInputUnit())
                ? request.getInputUnit().trim().toUpperCase(java.util.Locale.ROOT) : StockCodes.INPUT_MEASURE;
        if (request.getStockVersion() == null || !request.getStockVersion().equals(lot.getVersion())) {
            throw StockErrors.stockChanged(packText(lot, drug),
                    StockQty.inInputUnit(lot.getStockQty(), inputUnit, drug.getPackSize()));
        }
        LocalDateHolder date = scrapDate(request, lot);
        BigDecimal qty = scrapQuantity(request.getInputQty(), inputUnit, drug);
        StockLedger.Move move = ledger.outLot(lot, qty, StockCodes.unitText(drug.getUnit()));

        StockLoss loss = new StockLoss();
        loss.setFarmId(farmId);
        loss.setLossType(StockCodes.LOSS_TYPE_DRUG);
        loss.setDrugId(drug.getId());
        loss.setDrugLotId(lot.getId());
        loss.setInputQty(request.getInputQty());
        loss.setInputUnit(inputUnit);
        loss.setQty(qty);
        loss.setReason(request.getReason());
        loss.setRemark(trimToNull(request.getRemark()));
        loss.setLossDate(date.value());
        loss.setUnitPrice(move.unitPrice());
        loss.setAmount(move.amount());
        loss.setStatus(StockCodes.STATUS_NORMAL);
        loss.setClientRequestId(requestId);
        loss.setVersion(0);
        loss.setCreatedBy(userId);
        loss.setUpdatedBy(userId);
        lossMapper.insert(loss);

        StockFlow flow = flow(loss, StockCodes.MATERIAL_DRUG, null, drug.getId(), lot.getId(),
                StockCodes.FLOW_SCRAP, move, loss.getLossDate(), "批号 " + lot.getLotNo());
        loss.setFlowId(flow.getId());
        lossMapper.updateById(loss);
        lookups.log("CREATE", "LOSS_CREATE", StockCodes.BIZ_LOSS, loss.getId(), drug.getName(), null,
                move.amount());
        if (StockCodes.SCRAP_PLATFORM_RECALL.equals(request.getReason()) && lot.getRecallId() != null) {
            recallProgressMapper.addScrap(lot.getRecallId(), lot.getFarmId(), qty, loss.getLossDate(),
                    BeijingTime.now());
        }
        return get(loss.getId());
    }

    @Override
    public StockLossVO get(Long id) {
        StockLoss loss = lookups.requireLoss(id);
        StockLossVO vo = new StockLossVO();
        vo.setLossId(loss.getId());
        vo.setLossType(loss.getLossType());
        vo.setLossTypeText(StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType()) ? "报损" : "报废");
        vo.setDrugLotId(loss.getDrugLotId());
        vo.setReason(loss.getReason());
        vo.setRemark(loss.getRemark());
        vo.setLossDate(BeijingTime.format(loss.getLossDate()));
        vo.setStatus(loss.getStatus());
        vo.setUnitPrice(StockQty.price(loss.getUnitPrice()));
        vo.setAmount(StockQty.amount(loss.getAmount()));
        vo.setCreatedAt(BeijingTime.format(loss.getCreatedAt()));
        vo.setVersion(loss.getVersion());
        vo.setCreatedBy(lookups.userBrief(loss.getCreatedBy()));
        vo.setActions(StockActions.requestOnly(true));
        if (StockCodes.LOSS_TYPE_FEED.equals(loss.getLossType())) {
            Feed feed = feedMapper.selectById(loss.getFeedId());
            vo.setMaterialName(feed == null ? null : feed.getName());
            vo.setQty(StockQty.feed(loss.getQty()));
            vo.setRemaining(StockQty.feed(feed == null ? null : feed.getStockQty()));
            vo.setReasonText(StockCodes.feedLossReasonText(loss.getReason()));
        } else {
            DrugLot lot = loss.getDrugLotId() == null ? null : lookups.requireLot(loss.getDrugLotId());
            Drug drug = lot == null ? null : drugMapper.selectById(lot.getDrugId());
            vo.setMaterialName(drug == null ? null : drug.getName());
            if (lot != null) {
                vo.setLotNo(lot.getLotNo());
            }
            if (drug != null) {
                vo.setQty(StockQty.drug(loss.getQty(), drug.getUnit(), drug.getPackUnit(), drug.getPackSize()));
                vo.setRemaining(StockQty.drug(lot.getStockQty(), drug.getUnit(), drug.getPackUnit(),
                        drug.getPackSize()));
            } else {
                vo.setQty(StockQty.drug(loss.getQty(), null, null, null));
            }
            vo.setReasonText(StockCodes.scrapReasonText(loss.getReason()));
        }
        return vo;
    }

    private StockFlow flow(StockLoss loss, String materialType, Long feedId, Long drugId, Long drugLotId,
                           String flowType, StockLedger.Move move, java.time.LocalDate bizDate,
                           String relatedDesc) {
        StockFlow flow = new StockFlow();
        flow.setFarmId(loss.getFarmId());
        flow.setMaterialType(materialType);
        flow.setFeedId(feedId);
        flow.setDrugId(drugId);
        flow.setDrugLotId(drugLotId);
        flow.setRelatedDesc(relatedDesc);
        flow.setFlowType(flowType);
        flow.setDirection(StockCodes.DIRECTION_OUT);
        flow.setQty(StockQty.qty(loss.getQty()));
        flow.setUnitPrice(move.unitPrice());
        flow.setAmount(move.amount());
        flow.setStockQtyAfter(StockQty.qty(move.qtyAfter()));
        flow.setStockAmountAfter(StockQty.amount(move.amountAfter()));
        flow.setBizType(StockCodes.BIZ_LOSS);
        flow.setBizId(loss.getId());
        // 经办人：登记报损 / 报废的人
        flow.setOperatorId(loss.getCreatedBy());
        flow.setOccurAt(BeijingTime.now());
        flow.setBizDate(bizDate);
        flow.setIsBatchCost(false);
        flow.setIsFeedUsage(false);
        return flowWriter.record(flow);
    }

    private static BigDecimal scrapQuantity(BigDecimal input, String unit, Drug drug) {
        if (input == null || input.signum() <= 0) {
            throw StockErrors.field("input_qty", "数量必须大于 0");
        }
        BigDecimal qty = input;
        if (StockCodes.INPUT_PACK.equals(unit)) {
            if (!StringUtils.hasText(drug.getPackUnit()) || drug.getPackSize() == null
                    || drug.getPackSize().signum() <= 0) {
                throw StockErrors.field("input_unit", "该药品未设置包装，不能按包装录入");
            }
            qty = input.multiply(drug.getPackSize());
        } else if (!StockCodes.INPUT_MEASURE.equals(unit)) {
            throw StockErrors.field("input_unit", "录入单位只能是 MEASURE 或 PACK");
        }
        if (qty.stripTrailingZeros().scale() > StockQty.QTY_SCALE) {
            throw StockErrors.field("input_qty", "换算后的数量最多保留 3 位小数");
        }
        if (StockCodes.UNIT_DOSE.equals(drug.getUnit()) && qty.stripTrailingZeros().scale() > 0) {
            throw StockErrors.field("input_qty", "头份必须是整数");
        }
        return StockQty.qty(qty);
    }

    private record LocalDateHolder(java.time.LocalDate value) {
    }

    private static LocalDateHolder scrapDate(DrugScrapRequest request, DrugLot lot) {
        java.time.LocalDate date = request.getScrapDate() == null ? BeijingTime.today() : request.getScrapDate();
        if (date.isAfter(BeijingTime.today())) {
            throw StockErrors.field("scrap_date", "报废日期不能晚于今天");
        }
        if (lot.getFirstInboundDate() != null && date.isBefore(lot.getFirstInboundDate())) {
            throw StockErrors.field("scrap_date", "报废日期不能早于入库日期 "
                    + lot.getFirstInboundDate().getMonthValue() + "月" + lot.getFirstInboundDate().getDayOfMonth()
                    + "日");
        }
        return new LocalDateHolder(date);
    }

    private static String packText(DrugLot lot, Drug drug) {
        BigDecimal qty = StockQty.zeroIfNull(lot.getStockQty());
        if (drug.getPackUnit() != null && drug.getPackSize() != null && drug.getPackSize().signum() > 0
                && qty.remainder(drug.getPackSize()).signum() == 0) {
            int count = qty.divide(drug.getPackSize(), 0, java.math.RoundingMode.DOWN).intValue();
            return count + " " + drug.getPackUnit();
        }
        return StockQty.plain(qty) + " " + StockCodes.unitText(drug.getUnit());
    }

    private static String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
