package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.biz.stock.api.StockCostAllocation;
import com.zhimu.server.biz.stock.entity.BatchMaterialCost;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.entity.StockPartner;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.BatchMaterialCostMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockOutboundMapper;
import com.zhimu.server.biz.stock.mapper.StockPartnerMapper;
import com.zhimu.server.biz.stock.mapper.StockPenBatchMapper;
import com.zhimu.server.biz.stock.request.DrugOutboundRequest;
import com.zhimu.server.biz.stock.request.FeedOtherOutRequest;
import com.zhimu.server.biz.stock.request.OutboundUpdateRequest;
import com.zhimu.server.biz.stock.request.VoidRequest;
import com.zhimu.server.biz.stock.service.StockOutboundService;
import com.zhimu.server.biz.stock.support.BatchAllocator;
import com.zhimu.server.biz.stock.support.DrugLotStatuses;
import com.zhimu.server.biz.stock.support.StockActions;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockNumbers;
import com.zhimu.server.biz.stock.support.StockPartners;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.biz.stock.vo.CostAllocationVO;
import com.zhimu.server.biz.stock.vo.StockOutboundVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 出库单实现（模块09 2.5 / 2.6 链 3）。
 * <p>
 * 饲料其他出库：金额 = 数量 × 当时均价，不改均价，不计批次成本、不计饲料用量。
 * 药品领用：写 MANUAL_USE 流水并按关联批次 / 栏舍分摊批次成本；退货写 RETURN 流水，不分摊、不记账。
 * 直接修改、作废只限登记当天，否则 40307 走修改申请。
 */
@Service
@RequiredArgsConstructor
public class StockOutboundServiceImpl implements StockOutboundService {

    private static final Set<String> DESTINATIONS = Set.of(StockCodes.DEST_RETURN_SUPPLIER,
            StockCodes.DEST_TRANSFER_FARM, StockCodes.DEST_SALE, StockCodes.DEST_OTHER);
    private static final Set<String> RETURN_REASONS = Set.of(StockCodes.RETURN_QUALITY,
            StockCodes.RETURN_PACKAGE_DAMAGED, StockCodes.RETURN_OVER_PURCHASE, StockCodes.RETURN_OTHER);

    private final StockOutboundMapper outboundMapper;
    private final FeedMapper feedMapper;
    private final DrugMapper drugMapper;
    private final StockFlowMapper flowMapper;
    private final StockPartnerMapper partnerMapper;
    private final BatchMaterialCostMapper costMapper;
    private final StockPenBatchMapper penBatchMapper;
    private final StockLedger ledger;
    private final StockFlowWriter flowWriter;
    private final StockNumbers numbers;
    private final StockLookups lookups;
    private final StockPartners partners;
    private final BatchAllocator allocator;
    private final BatchQueryService batchQueryService;
    private final PenQueryService penQueryService;
    private final DrugCatalogQueryService drugCatalogQueryService;

    // ------------------------------------------------------------------ 创建

    @Override
    @Transactional
    public StockOutboundVO createFeedOtherOut(FeedOtherOutRequest request, String requestId) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        Feed feed = lookups.requireFeed(request.getFeedId());
        if (!DESTINATIONS.contains(request.getDestination())) {
            throw StockErrors.field("destination", "请选择出库去向");
        }
        if ((StockCodes.DEST_TRANSFER_FARM.equals(request.getDestination())
                || StockCodes.DEST_OTHER.equals(request.getDestination()))
                && !StringUtils.hasText(request.getRemark())) {
            throw StockErrors.field("remark", StockCodes.DEST_TRANSFER_FARM.equals(request.getDestination())
                    ? "请写明调往哪个养殖场" : "请写明出库原因");
        }
        LocalDate today = BeijingTime.today();
        if (request.getOutDate().isAfter(today)) {
            throw StockErrors.field("out_date", "出库日期不能晚于今天");
        }
        requireStockVersion(request.getStockVersion(), feed.getVersion(),
                StockQty.plain(feed.getStockQty()) + " 千克");
        BigDecimal inputQty = request.getInputQty();
        String inputUnit = normalizeMeasureUnit(request.getInputUnit());
        BigDecimal qty = StockCodes.INPUT_PACK.equals(inputUnit)
                ? StockQty.qty(inputQty.multiply(requirePackSize(feed.getPackUnit(), feed.getPackSize())))
                : StockQty.qty(inputQty);
        BigDecimal available = StockQty.zeroIfNull(feed.getStockQty());
        if (available.compareTo(qty) < 0) {
            throw StockErrors.notEnoughOut(available, "千克");
        }

        StockOutbound outbound = new StockOutbound();
        outbound.setFarmId(farmId);
        outbound.setOutboundNo(numbers.nextOutboundNo(request.getOutDate()));
        outbound.setMaterialType(StockCodes.MATERIAL_FEED);
        outbound.setOutType(StockCodes.OUT_OTHER);
        outbound.setFeedId(feed.getId());
        outbound.setInputQty(inputQty);
        outbound.setInputUnit(inputUnit);
        outbound.setQty(qty);
        outbound.setMaxQty(available);
        outbound.setDestination(request.getDestination());
        outbound.setRemark(trimToNull(request.getRemark()));
        outbound.setOutDate(request.getOutDate());
        outbound.setStatus(StockCodes.STATUS_NORMAL);
        outbound.setClientRequestId(requestId);
        outbound.setVersion(0);
        outbound.setCreatedBy(userId);
        outbound.setUpdatedBy(userId);
        // unit_price / amount 列 NOT NULL，先写 0，出库流水算出实际金额后再更新
        outbound.setUnitPrice(BigDecimal.ZERO);
        outbound.setAmount(BigDecimal.ZERO);
        outboundMapper.insert(outbound);

        StockLedger.Move move = ledger.outFeed(feed, qty);
        outbound.setUnitPrice(move.unitPrice());
        outbound.setAmount(move.amount());
        StockFlow flow = outFlow(outbound, StockCodes.MATERIAL_FEED, feed.getId(), null, null,
                StockCodes.FLOW_OTHER_OUT, move, request.getOutDate());
        outbound.setFlowId(flow.getId());
        outboundMapper.updateById(outbound);
        lookups.log("CREATE", "OUTBOUND_CREATE", StockCodes.BIZ_OUTBOUND, outbound.getId(), feed.getName(),
                null, move.amount());
        return get(outbound.getId());
    }

    @Override
    @Transactional
    public StockOutboundVO createDrugOutbound(DrugOutboundRequest request, String requestId) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        boolean use = StockCodes.OUT_USE.equals(request.getOutType());
        if (!use && !StockCodes.OUT_RETURN.equals(request.getOutType())) {
            throw StockErrors.field("out_type", "出库类型只能是 USE 或 RETURN");
        }
        Drug drug = lookups.requireDrug(request.getDrugId());
        DrugLot lot = lookups.requireLot(request.getDrugLotId());
        if (!drug.getId().equals(lot.getDrugId())) {
            throw StockErrors.field("drug_lot_id", "请选择库存批号");
        }
        if (drugCatalogQueryService.isBanned(drug.getCatalogId(), null)) {
            throw StockErrors.bannedUse();
        }
        String unitText = StockCodes.unitText(drug.getUnit());
        if (lot.getRecalledAt() != null) {
            throw StockErrors.lotUnavailable(lot.getLotNo(), false);
        }
        if (DrugLotStatuses.expired(lot.getExpireDate())) {
            throw StockErrors.lotUnavailable(lot.getLotNo(), true);
        }
        LocalDate today = BeijingTime.today();
        if (request.getOutDate().isAfter(today)) {
            throw StockErrors.field("out_date", use ? "领用日期不能晚于今天" : "退货日期不能晚于今天");
        }
        if (lot.getFirstInboundDate() != null && request.getOutDate().isBefore(lot.getFirstInboundDate())) {
            throw StockErrors.field("out_date", "领用日期不能早于该批号的入库日期 "
                    + monthDay(lot.getFirstInboundDate()));
        }
        requireStockVersion(request.getStockVersion(), lot.getVersion(), packQty(lot, drug));
        String inputUnit = StringUtils.hasText(request.getInputUnit())
                ? request.getInputUnit().trim().toUpperCase() : StockCodes.INPUT_PACK;
        if (!StockCodes.INPUT_PACK.equals(inputUnit) && !StockCodes.INPUT_MEASURE.equals(inputUnit)) {
            throw StockErrors.field("input_unit", "录入单位只能是 MEASURE 或 PACK");
        }
        BigDecimal qty = StockCodes.INPUT_PACK.equals(inputUnit)
                ? StockQty.qty(request.getInputQty().multiply(requirePackSize(drug.getPackUnit(), drug.getPackSize())))
                : StockQty.qty(request.getInputQty());
        if (StockCodes.UNIT_DOSE.equals(drug.getUnit()) && qty.stripTrailingZeros().scale() > 0) {
            throw StockErrors.field("input_qty", "头份必须是整数");
        }
        BigDecimal available = StockQty.zeroIfNull(lot.getStockQty());
        if (available.compareTo(qty) < 0) {
            throw StockErrors.notEnough("超过该批号结存 " + packQty(lot, drug) + "，出库后库存不能为负数");
        }
        Long receiverId = request.getReceiverId();
        Long batchId = null;
        Long penId = null;
        if (use) {
            if (StockCodes.TARGET_BATCH.equals(request.getTargetType())) {
                if (request.getBatchId() == null) {
                    throw StockErrors.field("batch_id", "请选择批次");
                }
                BatchInfo batch = batchQueryService.requireBatch(request.getBatchId());
                if (!batch.active() || batch.currentCount() == null || batch.currentCount() <= 0) {
                    throw StockErrors.field("batch_id", batch.batchNo() + " 已全部出栏，请重新选择");
                }
                batchId = batch.batchId();
            } else if (StockCodes.TARGET_PEN.equals(request.getTargetType())) {
                if (request.getPenId() == null) {
                    throw StockErrors.field("pen_id", "请选择栏舍");
                }
                PenInfo pen = requirePen(request.getPenId());
                if (penBatchMapper.listStockedBatches(farmId, pen.penId()) == null
                        || penBatchMapper.listStockedBatches(farmId, pen.penId()).isEmpty()) {
                    throw StockErrors.field("pen_id", pen.penName() + "已没有在栏批次，请重新选择");
                }
                penId = pen.penId();
            } else {
                throw StockErrors.field("target_type", "请选择领用对象");
            }
            if (receiverId == null) {
                receiverId = userId;
            }
        } else {
            if (!StringUtils.hasText(request.getReturnTo())) {
                throw StockErrors.field("return_to", "请填写退回对象");
            }
            if (!RETURN_REASONS.contains(request.getReturnReason())) {
                throw StockErrors.field("return_reason", "请选择退货原因");
            }
            if (StockCodes.RETURN_OTHER.equals(request.getReturnReason())
                    && !StringUtils.hasText(request.getRemark())) {
                throw StockErrors.field("remark", "请写明退货原因");
            }
        }

        StockOutbound outbound = new StockOutbound();
        outbound.setFarmId(farmId);
        outbound.setOutboundNo(numbers.nextOutboundNo(request.getOutDate()));
        outbound.setMaterialType(StockCodes.MATERIAL_DRUG);
        outbound.setOutType(request.getOutType());
        outbound.setDrugId(drug.getId());
        outbound.setDrugLotId(lot.getId());
        outbound.setInputQty(request.getInputQty());
        outbound.setInputUnit(inputUnit);
        outbound.setQty(qty);
        outbound.setMaxQty(maxInInputUnit(available, inputUnit, drug.getPackSize()));
        outbound.setTargetType(use ? request.getTargetType() : null);
        outbound.setBatchId(batchId);
        outbound.setPenId(penId);
        outbound.setReceiverId(use ? receiverId : null);
        outbound.setReturnTo(use ? null : request.getReturnTo().trim());
        outbound.setReturnReason(use ? null : request.getReturnReason());
        outbound.setRemark(trimToNull(request.getRemark()));
        outbound.setOutDate(request.getOutDate());
        outbound.setStatus(StockCodes.STATUS_NORMAL);
        outbound.setClientRequestId(requestId);
        outbound.setVersion(0);
        outbound.setCreatedBy(userId);
        outbound.setUpdatedBy(userId);
        // unit_price / amount 列 NOT NULL，先写 0，出库流水算出实际金额后再更新
        outbound.setUnitPrice(BigDecimal.ZERO);
        outbound.setAmount(BigDecimal.ZERO);
        outboundMapper.insert(outbound);

        StockLedger.Move move = ledger.outLot(lot, qty, unitText);
        outbound.setUnitPrice(move.unitPrice());
        outbound.setAmount(move.amount());
        List<StockCostAllocation> allocations = use
                ? allocator.allocate(farmId, batchId, penId, move.amount()) : List.of();
        StockFlow flow = outFlow(outbound, StockCodes.MATERIAL_DRUG, null, drug.getId(), lot.getId(),
                use ? StockCodes.FLOW_MANUAL_USE : StockCodes.FLOW_RETURN, move, request.getOutDate());
        flow.setBatchId(batchId);
        flow.setPenId(penId);
        flow.setOperatorId(use ? receiverId : null);
        flow.setIsBatchCost(use);
        flowMapper.updateById(flow);
        if (use) {
            flowWriter.writeCosts(flow, StockCodes.MATERIAL_DRUG, penId, allocations, request.getOutDate());
        }
        outbound.setFlowId(flow.getId());
        outboundMapper.updateById(outbound);
        if (!use) {
            partners.touch(farmId, StockCodes.PARTNER_RETURN_TO, outbound.getReturnTo());
        }
        lookups.log("CREATE", "OUTBOUND_CREATE", StockCodes.BIZ_OUTBOUND, outbound.getId(), drug.getName(),
                null, move.amount());
        return get(outbound.getId());
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public StockOutboundVO get(Long id) {
        StockOutbound outbound = lookups.requireOutbound(id);
        StockOutboundVO vo = new StockOutboundVO();
        vo.setOutboundId(outbound.getId());
        vo.setOutboundNo(outbound.getOutboundNo());
        vo.setOutType(outbound.getOutType());
        vo.setOutTypeText(StockCodes.outTypeText(outbound.getOutType()));
        vo.setMaterialType(outbound.getMaterialType());
        vo.setFeedId(outbound.getFeedId());
        vo.setDrugId(outbound.getDrugId());
        vo.setDrugLotId(outbound.getDrugLotId());
        vo.setInputQty(outbound.getInputQty());
        vo.setInputUnit(outbound.getInputUnit());
        vo.setMaxQty(outbound.getMaxQty());
        vo.setDestination(outbound.getDestination());
        vo.setDestinationText(StockCodes.destinationText(outbound.getDestination()));
        vo.setTargetType(outbound.getTargetType());
        vo.setReturnTo(outbound.getReturnTo());
        vo.setReturnReason(outbound.getReturnReason());
        vo.setReturnReasonText(StockCodes.returnReasonText(outbound.getReturnReason()));
        vo.setRemark(outbound.getRemark());
        vo.setOutDate(BeijingTime.format(outbound.getOutDate()));
        vo.setStatus(outbound.getStatus());
        vo.setStatusText(StockCodes.statusText(outbound.getStatus()));
        vo.setUnitPrice(StockQty.price(outbound.getUnitPrice()));
        vo.setAmount(StockQty.amount(outbound.getAmount()));
        vo.setCreatedAt(BeijingTime.format(outbound.getCreatedAt()));
        vo.setVersion(outbound.getVersion());
        vo.setCreatedBy(lookups.userBrief(outbound.getCreatedBy()));
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            Feed feed = feedMapper.selectById(outbound.getFeedId());
            vo.setMaterialName(feed == null ? null : feed.getName());
            vo.setQty(StockQty.feed(outbound.getQty()));
            vo.setAllocations(List.of());
        } else {
            Drug drug = drugMapper.selectById(outbound.getDrugId());
            DrugLot lot = outbound.getDrugLotId() == null ? null : lookups.requireLot(outbound.getDrugLotId());
            if (drug != null) {
                vo.setMaterialName(drug.getName());
                vo.setCategory(drug.getCategory());
                vo.setQty(StockQty.drug(outbound.getQty(), drug.getUnit(), drug.getPackUnit(),
                        drug.getPackSize()));
            } else {
                vo.setQty(StockQty.drug(outbound.getQty(), null, null, null));
            }
            if (lot != null) {
                vo.setLotNo(lot.getLotNo());
                vo.setExpireDate(BeijingTime.format(lot.getExpireDate()));
            }
            vo.setAllocations(allocations(outbound));
        }
        if (outbound.getBatchId() != null) {
            vo.setBatch(batchBrief(outbound.getBatchId()));
        }
        if (outbound.getPenId() != null) {
            vo.setPen(lookups.penBrief(outbound.getPenId()));
        }
        if (outbound.getReceiverId() != null) {
            vo.setReceiver(lookups.userBrief(outbound.getReceiverId()));
        }
        if (StockCodes.STATUS_VOID.equals(outbound.getStatus())) {
            vo.setActions(StockActions.voided());
        } else if (StockActions.registeredToday(outbound.getCreatedAt())) {
            vo.setActions(StockActions.editable(BeijingTime.today()));
        } else {
            vo.setActions(StockActions.requestOnly(true));
        }
        return vo;
    }

    @Override
    public List<String> recentPartners(String kind, String keyword) {
        String normalized = StockCodes.PARTNER_RETURN_TO.equalsIgnoreCase(kind)
                ? StockCodes.PARTNER_RETURN_TO : StockCodes.PARTNER_SUPPLIER;
        List<StockPartner> rows = partnerMapper.selectList(Wrappers.<StockPartner>lambdaQuery()
                .eq(StockPartner::getKind, normalized)
                .like(StringUtils.hasText(keyword), StockPartner::getName,
                        keyword == null ? null : keyword.trim())
                .orderByDesc(StockPartner::getLastUsedAt)
                .orderByDesc(StockPartner::getId)
                .last("LIMIT 5"));
        List<String> names = new ArrayList<>(rows.size());
        for (StockPartner row : rows) {
            names.add(row.getName());
        }
        return names;
    }

    // ------------------------------------------------------------------ 修改 / 作废

    @Override
    @Transactional
    public StockOutboundVO update(Long id, OutboundUpdateRequest request) {
        StockOutbound outbound = lookups.requireOutbound(id);
        requireEditableToday(outbound);
        if (request.getVersion() == null || !request.getVersion().equals(outbound.getVersion())) {
            throw StockErrors.versionConflict();
        }
        LocalDate today = BeijingTime.today();
        if (request.getOutDate() != null && request.getOutDate().isAfter(today)) {
            throw StockErrors.field("out_date", "出库日期不能晚于今天");
        }
        boolean use = StockCodes.OUT_USE.equals(outbound.getOutType());
        BigDecimal oldQty = StockQty.zeroIfNull(outbound.getQty());
        BigDecimal oldAmount = StockQty.zeroIfNull(outbound.getAmount());
        String inputUnit = StringUtils.hasText(request.getInputUnit())
                ? request.getInputUnit().trim().toUpperCase() : outbound.getInputUnit();
        BigDecimal qty;
        BigDecimal newAmount;
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            Feed feed = lookups.requireFeed(outbound.getFeedId());
            qty = StockCodes.INPUT_PACK.equals(inputUnit)
                    ? StockQty.qty(request.getInputQty().multiply(requirePackSize(feed.getPackUnit(),
                    feed.getPackSize())))
                    : StockQty.qty(request.getInputQty());
            newAmount = StockQty.amount(qty.multiply(StockQty.zeroIfNull(feed.getAvgPrice())));
            BigDecimal currentStock = StockQty.zeroIfNull(feed.getStockQty());
            if (currentStock.add(oldQty).compareTo(qty) < 0) {
                throw StockErrors.notEnoughOut(currentStock.add(oldQty), "千克");
            }
            ledger.adjustFeed(feed, oldQty.subtract(qty), oldAmount.subtract(newAmount));
        } else {
            Drug drug = lookups.requireDrug(outbound.getDrugId());
            DrugLot lot = lookups.requireLot(outbound.getDrugLotId());
            qty = StockCodes.INPUT_PACK.equals(inputUnit)
                    ? StockQty.qty(request.getInputQty().multiply(requirePackSize(drug.getPackUnit(),
                    drug.getPackSize())))
                    : StockQty.qty(request.getInputQty());
            newAmount = StockQty.amount(qty.multiply(StockQty.zeroIfNull(lot.getAvgPrice())));
            BigDecimal currentStock = StockQty.zeroIfNull(lot.getStockQty());
            if (currentStock.add(oldQty).compareTo(qty) < 0) {
                throw StockErrors.notEnough("超过该批号结存 " + packQty(lot, drug)
                        + "，出库后库存不能为负数");
            }
            ledger.adjustLot(lot, oldQty.subtract(qty), oldAmount.subtract(newAmount),
                    StockCodes.unitText(drug.getUnit()));
            if (request.getReturnTo() != null) {
                outbound.setReturnTo(trimToNull(request.getReturnTo()));
            }
            if (request.getReturnReason() != null) {
                outbound.setReturnReason(request.getReturnReason());
            }
        }
        if (request.getDestination() != null) {
            if (!DESTINATIONS.contains(request.getDestination())) {
                throw StockErrors.field("destination", "请选择出库去向");
            }
            outbound.setDestination(request.getDestination());
        }
        outbound.setInputQty(request.getInputQty());
        outbound.setInputUnit(inputUnit);
        outbound.setQty(qty);
        outbound.setAmount(newAmount);
        outbound.setRemark(request.getRemark() == null ? outbound.getRemark() : trimToNull(request.getRemark()));
        outbound.setOutDate(request.getOutDate() == null ? outbound.getOutDate() : request.getOutDate());
        outbound.setUpdatedBy(lookups.userId());
        List<StockCostAllocation> allocations = List.of();
        if (use) {
            applyUseTarget(outbound, request);
            BigDecimal currentStock = currentMaterialStock(outbound);
            outbound.setMaxQty(StockQty.qty(currentStock.add(qty)));
            allocations = allocator.allocate(outbound.getFarmId(), outbound.getBatchId(), outbound.getPenId(),
                    newAmount);
            outbound.setTargetType(request.getTargetType() == null ? outbound.getTargetType()
                    : request.getTargetType());
            if (request.getReceiverId() != null) {
                outbound.setReceiverId(request.getReceiverId());
            }
        }
        outboundMapper.updateById(outbound);
        if (outbound.getFlowId() != null) {
            StockFlow flow = flowMapper.selectById(outbound.getFlowId());
            if (flow != null) {
                flow.setQty(qty);
                flow.setAmount(newAmount);
                flow.setBizDate(outbound.getOutDate());
                flow.setRelatedDesc(null);
                flow.setStockQtyAfter(StockQty.zeroIfNull(flow.getStockQtyAfter()).add(oldQty).subtract(qty));
                flow.setRecalcAt(BeijingTime.now());
                flow.setIsBatchCost(use);
                if (use) {
                    flow.setBatchId(outbound.getBatchId());
                    flow.setPenId(outbound.getPenId());
                    flow.setOperatorId(outbound.getReceiverId());
                }
                flowMapper.updateById(flow);
                flowWriter.voidCostsByFlow(flow.getId());
                if (use) {
                    flowWriter.writeCosts(flow, StockCodes.MATERIAL_DRUG, outbound.getPenId(), allocations,
                            outbound.getOutDate());
                }
            }
        }
        lookups.log("UPDATE", "OUTBOUND_UPDATE", StockCodes.BIZ_OUTBOUND, outbound.getId(),
                outbound.getOutboundNo(), oldAmount, newAmount);
        return get(outbound.getId());
    }

    @Override
    @Transactional
    public void voidOutbound(Long id, VoidRequest request) {
        StockOutbound outbound = lookups.requireOutbound(id);
        requireEditableToday(outbound);
        if (request.getVersion() == null || !request.getVersion().equals(outbound.getVersion())) {
            throw StockErrors.versionConflict();
        }
        BigDecimal qty = StockQty.zeroIfNull(outbound.getQty());
        BigDecimal amount = StockQty.zeroIfNull(outbound.getAmount());
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            ledger.adjustFeed(lookups.requireFeed(outbound.getFeedId()), qty, amount);
        } else {
            DrugLot lot = lookups.requireLot(outbound.getDrugLotId());
            Drug drug = drugMapper.selectById(lot.getDrugId());
            ledger.adjustLot(lot, qty, amount, StockCodes.unitText(drug == null ? null : drug.getUnit()));
        }
        if (outbound.getFlowId() != null) {
            StockFlow flow = flowMapper.selectById(outbound.getFlowId());
            if (flow != null) {
                flowWriter.voidFlow(flow, StockCodes.VOID_SOURCE);
            }
        }
        outbound.setStatus(StockCodes.STATUS_VOID);
        outbound.setVoidAt(BeijingTime.now());
        outbound.setVoidBy(lookups.userId());
        outbound.setUpdatedBy(lookups.userId());
        outboundMapper.updateById(outbound);
        lookups.log("VOID", "OUTBOUND_VOID", StockCodes.BIZ_OUTBOUND, outbound.getId(),
                outbound.getOutboundNo(), qty, null);
    }

    // ------------------------------------------------------------------ 分摊预览

    @Override
    public CostAllocationVO allocationPreview(Long drugLotId, BigDecimal inputQty, String inputUnit,
                                              String targetType, Long batchId, Long penId) {
        DrugLot lot = lookups.requireLot(drugLotId);
        Drug drug = lookups.requireDrug(lot.getDrugId());
        String unit = StringUtils.hasText(inputUnit) ? inputUnit.trim().toUpperCase() : StockCodes.INPUT_PACK;
        BigDecimal qty = StockCodes.INPUT_PACK.equals(unit)
                ? StockQty.qty(inputQty.multiply(requirePackSize(drug.getPackUnit(), drug.getPackSize())))
                : StockQty.qty(inputQty);
        BigDecimal unitPrice = StockQty.price(lot.getAvgPrice());
        BigDecimal total = StockQty.amount(qty.multiply(StockQty.zeroIfNull(lot.getAvgPrice())));
        List<StockCostAllocation> allocations = allocator.allocate(lot.getFarmId(),
                StockCodes.TARGET_BATCH.equals(targetType) ? batchId : null,
                StockCodes.TARGET_PEN.equals(targetType) ? penId : null, total);
        CostAllocationVO vo = new CostAllocationVO();
        vo.setTotalAmount(total);
        vo.setUnitPrice(unitPrice);
        for (StockCostAllocation allocation : allocations) {
            CostAllocationVO.Row row = new CostAllocationVO.Row();
            row.setBatchId(allocation.batchId());
            row.setBatchNo(allocation.batchNo());
            row.setHeadCount(allocation.headCount());
            row.setRatio(allocation.ratio());
            row.setAmount(allocation.amount());
            vo.getRows().add(row);
        }
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    private void applyUseTarget(StockOutbound outbound, OutboundUpdateRequest request) {
        String targetType = request.getTargetType() == null ? outbound.getTargetType() : request.getTargetType();
        if (StockCodes.TARGET_BATCH.equals(targetType)) {
            Long batchId = request.getBatchId() == null ? outbound.getBatchId() : request.getBatchId();
            BatchInfo batch = batchQueryService.requireBatch(batchId);
            if (!batch.active() || batch.currentCount() == null || batch.currentCount() <= 0) {
                throw StockErrors.field("batch_id", batch.batchNo() + " 已全部出栏，请重新选择");
            }
            outbound.setTargetType(StockCodes.TARGET_BATCH);
            outbound.setBatchId(batch.batchId());
            outbound.setPenId(null);
        } else if (StockCodes.TARGET_PEN.equals(targetType)) {
            Long penId = request.getPenId() == null ? outbound.getPenId() : request.getPenId();
            PenInfo pen = requirePen(penId);
            List<com.zhimu.server.biz.stock.query.PenBatchRow> rows =
                    penBatchMapper.listStockedBatches(outbound.getFarmId(), pen.penId());
            if (rows == null || rows.isEmpty()) {
                throw StockErrors.field("pen_id", pen.penName() + "已没有在栏批次，请重新选择");
            }
            outbound.setTargetType(StockCodes.TARGET_PEN);
            outbound.setPenId(pen.penId());
            outbound.setBatchId(null);
        } else {
            throw StockErrors.field("target_type", "请选择领用对象");
        }
    }

    private BigDecimal currentMaterialStock(StockOutbound outbound) {
        if (StockCodes.isFeed(outbound.getMaterialType())) {
            Feed feed = feedMapper.selectById(outbound.getFeedId());
            return feed == null ? BigDecimal.ZERO : StockQty.zeroIfNull(feed.getStockQty());
        }
        DrugLot lot = outbound.getDrugLotId() == null ? null : lookups.requireLot(outbound.getDrugLotId());
        return lot == null ? BigDecimal.ZERO : StockQty.zeroIfNull(lot.getStockQty());
    }

    private StockFlow outFlow(StockOutbound outbound, String materialType, Long feedId, Long drugId,
                              Long drugLotId, String flowType, StockLedger.Move move, LocalDate bizDate) {
        StockFlow flow = new StockFlow();
        flow.setFarmId(outbound.getFarmId());
        flow.setMaterialType(materialType);
        flow.setFeedId(feedId);
        flow.setDrugId(drugId);
        flow.setDrugLotId(drugLotId);
        flow.setFlowType(flowType);
        flow.setDirection(StockCodes.DIRECTION_OUT);
        flow.setQty(StockQty.qty(outbound.getQty()));
        flow.setUnitPrice(move.unitPrice());
        flow.setAmount(move.amount());
        flow.setStockQtyAfter(StockQty.qty(move.qtyAfter()));
        flow.setStockAmountAfter(StockQty.amount(move.amountAfter()));
        flow.setBizType(StockCodes.BIZ_OUTBOUND);
        flow.setBizId(outbound.getId());
        flow.setOccurAt(BeijingTime.now());
        flow.setBizDate(bizDate);
        flow.setIsBatchCost(false);
        flow.setIsFeedUsage(false);
        return flowWriter.record(flow);
    }

    private List<CostAllocationVO.Row> allocations(StockOutbound outbound) {
        if (outbound.getFlowId() == null) {
            return List.of();
        }
        List<BatchMaterialCost> costs = costMapper.selectList(Wrappers.<BatchMaterialCost>lambdaQuery()
                .eq(BatchMaterialCost::getFlowId, outbound.getFlowId())
                .eq(BatchMaterialCost::getStatus, StockCodes.STATUS_NORMAL));
        List<CostAllocationVO.Row> rows = new ArrayList<>(costs.size());
        for (BatchMaterialCost cost : costs) {
            CostAllocationVO.Row row = new CostAllocationVO.Row();
            row.setBatchId(cost.getBatchId());
            row.setBatchNo(batchNo(cost.getBatchId()));
            row.setHeadCount(cost.getHeadCount());
            row.setRatio(cost.getRatio());
            row.setAmount(StockQty.amount(cost.getAmount()));
            rows.add(row);
        }
        return rows;
    }

    private com.zhimu.server.common.vo.BatchBriefVO batchBrief(Long batchId) {
        try {
            BatchInfo batch = batchQueryService.requireBatch(batchId);
            return com.zhimu.server.common.vo.BatchBriefVO.builder()
                    .batchId(batch.batchId())
                    .batchNo(batch.batchNo())
                    .headCount(batch.currentCount())
                    .penName(batch.penIds() == null || batch.penIds().isEmpty()
                            ? null : lookups.penName(batch.penIds().get(0)))
                    .dayAge(batch.entryAgeDays() == null ? null
                            : batch.entryAgeDays() + daysSince(batch))
                    .build();
        } catch (BizException e) {
            return null;
        }
    }

    private static int daysSince(BatchInfo batch) {
        if (batch.entryDate() == null) {
            return 0;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(batch.entryDate(), BeijingTime.today());
    }

    private String batchNo(Long batchId) {
        try {
            return batchQueryService.requireBatch(batchId).batchNo();
        } catch (BizException e) {
            return null;
        }
    }

    private PenInfo requirePen(Long penId) {
        PenInfo pen = lookups.pen(penId);
        if (pen == null) {
            throw StockErrors.notFound();
        }
        if (!pen.enabled()) {
            throw StockErrors.field("pen_id", "该栏舍已停用，请重新选择");
        }
        return pen;
    }

    private void requireEditableToday(StockOutbound outbound) {
        if (StockCodes.STATUS_VOID.equals(outbound.getStatus())) {
            throw StockErrors.voided("这条记录已作废");
        }
        if (!StockActions.registeredToday(outbound.getCreatedAt())) {
            throw StockErrors.changeRequestRequired("这条记录已不是今天登记的，修改需要提交修改申请，由管理员审核");
        }
    }

    private static void requireStockVersion(Integer requestVersion, Integer version, String currentQtyText) {
        if (requestVersion == null || !requestVersion.equals(version)) {
            throw StockErrors.stockChanged(currentQtyText);
        }
    }

    /** 修改时的数量上限按录入单位：PACK 时取包装数，否则取记账数量 */
    private static BigDecimal maxInInputUnit(BigDecimal qty, String inputUnit, BigDecimal packSize) {
        if (StockCodes.INPUT_PACK.equals(inputUnit) && packSize != null && packSize.signum() > 0) {
            return BigDecimal.valueOf(qty.divide(packSize, 0, RoundingMode.DOWN).intValue());
        }
        return StockQty.qty(qty);
    }

    /** 批号库存的包装写法：8 桶 / 300 头份 */
    private String packQty(DrugLot lot, Drug drug) {
        BigDecimal qty = StockQty.zeroIfNull(lot.getStockQty());
        return drug == null ? StockQty.plain(qty) : packText(qty, drug);
    }

    private String packText(BigDecimal qty, Drug drug) {
        if (drug.getPackUnit() != null && drug.getPackSize() != null && drug.getPackSize().signum() > 0) {
            int count = qty.divide(drug.getPackSize(), 0, RoundingMode.DOWN).intValue();
            BigDecimal rest = qty.subtract(drug.getPackSize().multiply(BigDecimal.valueOf(count)));
            if (rest.signum() > 0) {
                return StockQty.plain(rest) + " " + StockCodes.unitText(drug.getUnit());
            }
            return count + " " + drug.getPackUnit();
        }
        return StockQty.plain(qty) + " " + StockCodes.unitText(drug.getUnit());
    }

    private static Integer packCountOf(DrugLot lot, Drug drug) {
        if (drug.getPackSize() == null || drug.getPackSize().signum() <= 0) {
            return null;
        }
        return StockQty.packCount(StockQty.zeroIfNull(lot.getStockQty()), drug.getPackSize());
    }

    private static BigDecimal requirePackSize(String packUnit, BigDecimal packSize) {
        if (packUnit == null || packSize == null || packSize.signum() <= 0) {
            throw StockErrors.field("input_unit", "该物资未设置包装，不能按包装录入");
        }
        return packSize;
    }

    private static String normalizeMeasureUnit(String inputUnit) {
        if (!StringUtils.hasText(inputUnit)) {
            return StockCodes.INPUT_MEASURE;
        }
        String unit = inputUnit.trim().toUpperCase();
        if (!StockCodes.INPUT_PACK.equals(unit) && !StockCodes.INPUT_MEASURE.equals(unit)) {
            throw StockErrors.field("input_unit", "录入单位只能是 MEASURE 或 PACK");
        }
        return unit;
    }

    private static String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private static String monthDay(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }
}
