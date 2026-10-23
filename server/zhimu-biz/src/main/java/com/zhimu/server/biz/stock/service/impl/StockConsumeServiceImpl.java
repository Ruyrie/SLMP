package com.zhimu.server.biz.stock.service.impl;

import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.FeedStockCommand;
import com.zhimu.server.biz.stock.api.FeedStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.biz.stock.api.StockCostAllocation;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.service.StockDrugLotService;
import com.zhimu.server.biz.stock.support.BatchAllocator;
import com.zhimu.server.biz.stock.support.DrugLotStatuses;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存扣减端口实现（模块06 / 08 调用，对应服务端文档 2.5 内部 Service 接口）。
 * <p>
 * 加入调用方事务（默认传播）：记录保存失败时扣减与批次成本一并回滚。不比对页面版本，只在结存不足时失败。
 * 饲料写 FEED_USE（计入饲料用量与批次成本）；药品按调用方给的 flow_type 写 VACCINE_USE / MEDICATION_USE /
 * DISINFECT_USE，biz_type 为来源记录类型，计入批次成本。作废按来源记录找到有效流水后回滚并置 VOID。
 */
@Service
@RequiredArgsConstructor
public class StockConsumeServiceImpl implements FeedStockPort, DrugStockPort {

    private final StockLookups lookups;
    private final StockLedger ledger;
    private final StockFlowWriter flowWriter;
    private final BatchAllocator allocator;
    private final StockDrugLotService drugLotService;
    private final DrugCatalogQueryService drugCatalogQueryService;

    // ------------------------------------------------------------------ 饲料

    @Override
    @Transactional
    public StockConsumeResult deduct(FeedStockCommand command) {
        Feed feed = lookups.requireFeed(command.feedId());
        BigDecimal qty = requireQty(command.qty());
        StockLedger.Move move = ledger.outFeed(feed, qty);

        List<StockCostAllocation> allocations = allocator.allocate(command.farmId(), command.batchId(),
                command.penId(), move.amount());
        StockFlow flow = new StockFlow();
        flow.setFarmId(feed.getFarmId());
        flow.setMaterialType(StockCodes.MATERIAL_FEED);
        flow.setFeedId(feed.getId());
        flow.setFlowType(StockCodes.FLOW_FEED_USE);
        flow.setDirection(StockCodes.DIRECTION_OUT);
        flow.setQty(qty);
        flow.setUnitPrice(move.unitPrice());
        flow.setAmount(move.amount());
        flow.setStockQtyAfter(StockQty.qty(move.qtyAfter()));
        flow.setStockAmountAfter(StockQty.amount(move.amountAfter()));
        flow.setBizType(StringUtils.hasText(command.bizType()) ? command.bizType()
                : StockCodes.BIZ_FEED_RECORD);
        flow.setBizId(command.bizId());
        flow.setBatchId(command.batchId());
        flow.setPenId(command.penId());
        flow.setOperatorId(command.operatorId());
        flow.setRelatedDesc(command.relatedDesc());
        flow.setOccurAt(command.occurAt() == null ? BeijingTime.now() : command.occurAt());
        flow.setBizDate(command.bizDate() == null ? BeijingTime.today() : command.bizDate());
        flow.setIsBatchCost(!allocations.isEmpty());
        flow.setIsFeedUsage(true);
        flowWriter.record(flow);
        flowWriter.writeCosts(flow, StockCodes.MATERIAL_FEED, command.penId(), allocations,
                flow.getBizDate());
        return new StockConsumeResult(flow.getId(), move.unitPrice(), move.amount(), move.qtyAfter(), null,
                allocations);
    }

    // ------------------------------------------------------------------ 药品

    @Override
    public List<DrugLotOption> lotsFor(Long farmId, Long drugId, String purpose, BigDecimal needQty) {
        return drugLotService.optionRecords(drugId, purpose, needQty);
    }

    @Override
    @Transactional
    public StockConsumeResult deduct(DrugStockCommand command) {
        Drug drug = lookups.requireDrug(command.drugId());
        if (drugCatalogQueryService.isBanned(drug.getCatalogId(), null)) {
            throw StockErrors.bannedUse();
        }
        DrugLot lot = command.drugLotId() != null ? lookups.requireLot(command.drugLotId())
                : firstUsableLot(drug);
        if (!drug.getId().equals(lot.getDrugId())) {
            throw StockErrors.notFound();
        }
        if (lot.getRecalledAt() != null) {
            throw StockErrors.lotUnavailable(lot.getLotNo(), false);
        }
        if (DrugLotStatuses.expired(lot.getExpireDate())) {
            throw StockErrors.lotUnavailable(lot.getLotNo(), true);
        }
        BigDecimal qty = requireQty(command.qty());
        StockLedger.Move move = ledger.outLot(lot, qty, StockCodes.unitText(drug.getUnit()));
        List<StockCostAllocation> allocations = allocator.allocate(command.farmId(), command.batchId(),
                command.penId(), move.amount());

        StockFlow flow = new StockFlow();
        flow.setFarmId(lot.getFarmId());
        flow.setMaterialType(StockCodes.MATERIAL_DRUG);
        flow.setDrugId(drug.getId());
        flow.setDrugLotId(lot.getId());
        flow.setFlowType(StringUtils.hasText(command.flowType()) ? command.flowType()
                : StockCodes.FLOW_MANUAL_USE);
        flow.setDirection(StockCodes.DIRECTION_OUT);
        flow.setQty(qty);
        flow.setUnitPrice(move.unitPrice());
        flow.setAmount(move.amount());
        flow.setStockQtyAfter(StockQty.qty(move.qtyAfter()));
        flow.setStockAmountAfter(StockQty.amount(move.amountAfter()));
        flow.setBizType(command.bizType());
        flow.setBizId(command.bizId());
        flow.setBatchId(command.batchId());
        flow.setPenId(command.penId());
        flow.setOperatorId(command.operatorId());
        flow.setRelatedDesc(command.relatedDesc());
        flow.setOccurAt(command.occurAt() == null ? BeijingTime.now() : command.occurAt());
        flow.setBizDate(command.bizDate() == null ? BeijingTime.today() : command.bizDate());
        flow.setIsBatchCost(!allocations.isEmpty());
        flow.setIsFeedUsage(false);
        flowWriter.record(flow);
        flowWriter.writeCosts(flow, StockCodes.MATERIAL_DRUG, command.penId(), allocations,
                flow.getBizDate());
        return new StockConsumeResult(flow.getId(), move.unitPrice(), move.amount(), move.qtyAfter(),
                lot.getLotNo(), allocations);
    }

    // ------------------------------------------------------------------ 回滚

    @Override
    @Transactional
    public void restore(String bizType, Long bizId) {
        List<StockFlow> flows = flowWriter.validFlows(bizType, bizId);
        if (flows.isEmpty()) {
            return;
        }
        for (StockFlow flow : flows) {
            if (StockCodes.isFeed(flow.getMaterialType())) {
                Feed feed = lookups.requireFeed(flow.getFeedId());
                ledger.adjustFeed(feed, flow.getQty(), flow.getAmount());
            } else {
                DrugLot lot = lookups.requireLot(flow.getDrugLotId());
                Drug drug = lookups.requireDrug(flow.getDrugId());
                ledger.adjustLot(lot, flow.getQty(), flow.getAmount(), StockCodes.unitText(drug.getUnit()));
            }
        }
        flowWriter.voidByBiz(bizType, bizId, StockCodes.VOID_SOURCE);
    }

    // ------------------------------------------------------------------ 内部

    private DrugLot firstUsableLot(Drug drug) {
        for (DrugLotOption option : lotsFor(drug.getFarmId(), drug.getId(),
                StockCodes.PURPOSE_USE, null)) {
            if (!option.canGoScrap() && option.stock() != null
                    && option.stock().getValue() != null && option.stock().getValue().signum() > 0) {
                return lookups.requireLot(option.lotId());
            }
        }
        throw StockErrors.notEnough(drug.getName() + "没有可用批号，请先入库");
    }

    private static BigDecimal requireQty(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw StockErrors.field("qty", "必须大于 0");
        }
        return StockQty.qty(qty);
    }

    /** 显式保留时间类型导入（内部接口统一北京时间） */
    @SuppressWarnings("unused")
    private static LocalDateTime now() {
        return BeijingTime.now();
    }

    @SuppressWarnings("unused")
    private static LocalDate today(LocalDateTime time) {
        return time.toLocalDate();
    }
}
