package com.zhimu.server.biz.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.finance.api.StockCostPort;
import com.zhimu.server.biz.finance.entity.BatchSettlement;
import com.zhimu.server.biz.finance.entity.BatchSettlementSupplement;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.BatchSettlementMapper;
import com.zhimu.server.biz.finance.mapper.BatchSettlementSupplementMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.BatchSettlementService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.SettlementVos;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** API-E26–E28：批次选择、实时核算、确认补录并重算。 */
@Service
@RequiredArgsConstructor
public class BatchSettlementServiceImpl implements BatchSettlementService {

    private final BatchSettlementMapper settlementMapper;
    private final BatchSettlementSupplementMapper supplementMapper;
    private final SaleOrderMapper orderMapper;
    private final FinanceRecordMapper recordMapper;
    private final FinanceCategoryMapper categoryMapper;
    private final StockCostPort stockCostPort;
    private final BatchQueryService batchQueryService;
    private final FinanceSupport support;

    @Override
    public SettlementVos.OptionPageVO options(FinanceQueries.SettlementQuery query) {
        Long farmId = support.farmId();
        Set<Long> batchIds = new LinkedHashSet<>();
        batchQueryService.listActive(farmId).forEach(b -> batchIds.add(b.batchId()));
        TenantSkip.call(() -> settlementMapper.selectList(Wrappers.<BatchSettlement>lambdaQuery()
                .eq(BatchSettlement::getFarmId, farmId))).forEach(s -> batchIds.add(s.getBatchId()));
        List<BatchInfo> batches = new ArrayList<>();
        for (Long id : batchIds) {
            try {
                BatchInfo batch = batchQueryService.requireBatch(id);
                if (batch != null && farmId.equals(batch.farmId())) {
                    batches.add(batch);
                }
            } catch (RuntimeException ignored) {
                // 批次已删除时跳过
            }
        }
        batches.sort(Comparator.comparing((BatchInfo b) -> !b.active())
                .thenComparing(BatchInfo::batchNo, Comparator.nullsLast(Comparator.reverseOrder())));
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        if (keyword != null && !keyword.isEmpty()) {
            batches = batches.stream().filter(b -> b.batchNo() != null
                    && b.batchNo().toLowerCase().contains(keyword.toLowerCase())).toList();
        }
        Map<Long, BatchSettlement> settlements = TenantSkip.call(() -> settlementMapper.selectList(
                        Wrappers.<BatchSettlement>lambdaQuery().eq(BatchSettlement::getFarmId, farmId)))
                .stream().collect(Collectors.toMap(BatchSettlement::getBatchId, s -> s, (a, b) -> a));
        int farmPending = supplementMapper.selectCount(Wrappers.<BatchSettlementSupplement>lambdaQuery()
                .eq(BatchSettlementSupplement::getFarmId, farmId)
                .eq(BatchSettlementSupplement::getStatus, FinanceConst.SUPPLEMENT_PENDING)).intValue();

        SettlementVos.OptionPageVO vo = new SettlementVos.OptionPageVO();
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, batches.size());
        int to = Math.min(from + size, batches.size());
        for (BatchInfo batch : batches.subList(from, to)) {
            SettlementVos.OptionVO item = new SettlementVos.OptionVO();
            item.setBatchId(batch.batchId());
            item.setBatchNo(batch.batchNo());
            item.setPenName(primaryPenName(batch));
            item.setBatchStatus(batch.status());
            BatchSettlement settlement = settlements.get(batch.batchId());
            item.setSettlementStatus(settlement == null ? FinanceConst.SETTLE_RUNNING : settlement.getStatus());
            item.setPendingSupplementCount(supplementMapper.selectCount(
                    Wrappers.<BatchSettlementSupplement>lambdaQuery()
                            .eq(BatchSettlementSupplement::getBatchId, batch.batchId())
                            .eq(BatchSettlementSupplement::getStatus, FinanceConst.SUPPLEMENT_PENDING)).intValue());
            vo.getList().add(item);
        }
        vo.setTotal(batches.size());
        vo.setPage(page);
        vo.setSize(size);
        vo.setHasMore((long) page * size < batches.size());
        vo.setFarmPendingSupplementCount(farmPending);
        return vo;
    }

    @Override
    @Transactional
    public SettlementVos.BatchSettlementVO detail(Long batchId) {
        Long farmId = support.farmId();
        BatchInfo batch = support.requireBatch(batchId, farmId);
        BatchSettlement settlement = settlementMapper.selectOne(Wrappers.<BatchSettlement>lambdaQuery()
                .eq(BatchSettlement::getBatchId, batchId)
                .last("LIMIT 1"));
        if (settlement == null) {
            settlement = new BatchSettlement();
            settlement.setFarmId(farmId);
            settlement.setBatchId(batchId);
            settlement.setStatus(FinanceConst.SETTLE_RUNNING);
            settlement.setSaleIncome(BigDecimal.ZERO);
            settlement.setCostFeed(BigDecimal.ZERO);
            settlement.setFeedKg(BigDecimal.ZERO);
            settlement.setCostIntro(BigDecimal.ZERO);
            settlement.setIntroCount(0);
            settlement.setCostDrug(BigDecimal.ZERO);
            settlement.setCostOther(BigDecimal.ZERO);
            settlement.setCostTotal(BigDecimal.ZERO);
            settlement.setProfit(BigDecimal.ZERO);
            settlement.setSoldCount(0);
            settlement.setCalcAt(BeijingTime.now());
            settlement.setVersion(0);
            settlementMapper.insert(settlement);
        }
        if (FinanceConst.SETTLE_RUNNING.equals(settlement.getStatus())) {
            recompute(settlement, batch, false);
            settlementMapper.updateById(settlement);
        }
        return toVo(settlement, batch);
    }

    @Override
    @Transactional
    public SettlementVos.BatchSettlementVO confirm(Long batchId, FinanceRequests.SupplementConfirmRequest request) {
        Long farmId = support.farmId();
        Long userId = UserContext.requireUserId();
        BatchInfo batch = support.requireBatch(batchId, farmId);
        BatchSettlement settlement = settlementMapper.selectOne(Wrappers.<BatchSettlement>lambdaQuery()
                .eq(BatchSettlement::getBatchId, batchId)
                .last("LIMIT 1"));
        if (settlement == null || !FinanceConst.SETTLE_LOCKED.equals(settlement.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "补录已被处理，页面已刷新");
        }
        FinanceErrors.require(request != null && request.getSupplementIds() != null
                && !request.getSupplementIds().isEmpty(), "supplement_ids", "请选择要确认的补录");
        support.checkVersion(settlement.getVersion(), request.getVersion());
        List<BatchSettlementSupplement> supplements = supplementMapper.selectList(
                Wrappers.<BatchSettlementSupplement>lambdaQuery()
                        .in(BatchSettlementSupplement::getId, request.getSupplementIds())
                        .eq(BatchSettlementSupplement::getBatchId, batchId)
                        .eq(BatchSettlementSupplement::getStatus, FinanceConst.SUPPLEMENT_PENDING));
        if (supplements.size() != request.getSupplementIds().size()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "补录已被处理，页面已刷新");
        }
        for (BatchSettlementSupplement supplement : supplements) {
            BigDecimal amount = FinanceSupport.nz(supplement.getAmount());
            switch (supplement.getCostGroup()) {
                case FinanceConst.COST_FEED -> {
                    settlement.setCostFeed(FinanceSupport.nz(settlement.getCostFeed()).add(amount));
                    settlement.setFeedKg(FinanceSupport.nz(settlement.getFeedKg())
                            .add(FinanceSupport.nz(supplement.getQtyDelta())));
                }
                case FinanceConst.COST_INTRO -> settlement.setCostIntro(
                        FinanceSupport.nz(settlement.getCostIntro()).add(amount));
                case FinanceConst.COST_DRUG -> settlement.setCostDrug(
                        FinanceSupport.nz(settlement.getCostDrug()).add(amount));
                default -> settlement.setCostOther(FinanceSupport.nz(settlement.getCostOther()).add(amount));
            }
            supplement.setStatus(FinanceConst.SUPPLEMENT_CONFIRMED);
            supplement.setConfirmedBy(userId);
            supplement.setConfirmedAt(BeijingTime.now());
            supplementMapper.updateById(supplement);
        }
        recompute(settlement, batch, true);
        if (settlementMapper.updateById(settlement) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        return toVo(settlement, batch);
    }

    // ===== 计算 =====

    private void recompute(BatchSettlement settlement, BatchInfo batch, boolean locked) {
        List<SaleOrder> orders = TenantSkip.call(() -> orderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getBatchId, batch.batchId())
                .ne(SaleOrder::getPaymentStatus, FinanceConst.PAY_VOIDED)));
        BigDecimal saleIncome = FinanceSupport.scale2(orders.stream().map(SaleOrder::getSaleAmount)
                .map(FinanceSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add));
        int soldCount = orders.stream().mapToInt(o -> o.getQuantity() == null ? 0 : o.getQuantity()).sum();
        settlement.setSaleIncome(saleIncome);
        settlement.setSoldCount(soldCount);

        StockCostPort.BatchCost cost = stockCostPort.costOf(batch.batchId());
        settlement.setCostFeed(FinanceSupport.scale2(cost.feed()));
        settlement.setFeedKg(FinanceSupport.scale2(cost.feedKg()));
        settlement.setCostDrug(FinanceSupport.scale2(cost.drug()));

        if (settlement.getIntroUnitPrice() != null && batch.entryCount() != null) {
            settlement.setIntroCount(batch.entryCount());
            settlement.setCostIntro(FinanceSupport.scale2(settlement.getIntroUnitPrice()
                    .multiply(BigDecimal.valueOf(batch.entryCount()))));
        }
        settlement.setCostOther(otherCost(batch.batchId()));
        BigDecimal total = FinanceSupport.scale2(FinanceSupport.nz(settlement.getCostFeed())
                .add(FinanceSupport.nz(settlement.getCostIntro()))
                .add(FinanceSupport.nz(settlement.getCostDrug()))
                .add(FinanceSupport.nz(settlement.getCostOther())));
        settlement.setCostTotal(total);
        settlement.setProfit(FinanceSupport.scale2(saleIncome.subtract(total)));

        int perHeadCount = locked ? soldCount : (batch.currentCount() == null ? 0 : batch.currentCount());
        if (perHeadCount > 0) {
            settlement.setCostPerHead(total.divide(BigDecimal.valueOf(perHeadCount), 2, RoundingMode.HALF_UP));
            settlement.setProfitPerHead(settlement.getProfit().divide(BigDecimal.valueOf(perHeadCount), 2,
                    RoundingMode.HALF_UP));
        } else {
            settlement.setCostPerHead(null);
            settlement.setProfitPerHead(null);
        }
        settlement.setCalcAt(BeijingTime.now());
        if (locked && settlement.getStatus() == null) {
            settlement.setStatus(FinanceConst.SETTLE_LOCKED);
        }
    }

    private BigDecimal otherCost(Long batchId) {
        List<FinanceRecord> records = TenantSkip.call(() -> recordMapper.selectList(
                Wrappers.<FinanceRecord>lambdaQuery()
                        .eq(FinanceRecord::getBatchId, batchId)
                        .eq(FinanceRecord::getRecordType, FinanceConst.EXPENSE)
                        .eq(FinanceRecord::getStatus, FinanceConst.RECORD_NORMAL)));
        BigDecimal total = BigDecimal.ZERO;
        for (FinanceRecord record : records) {
            FinanceCategory category = categoryMapper.selectById(record.getCategoryId());
            if (category == null || Boolean.TRUE.equals(category.getMarkOnly())) {
                continue;
            }
            total = total.add(FinanceSupport.nz(record.getAmount()));
        }
        return FinanceSupport.scale2(total);
    }

    // ===== 转换 =====

    private SettlementVos.BatchSettlementVO toVo(BatchSettlement settlement, BatchInfo batch) {
        SettlementVos.BatchSettlementVO vo = new SettlementVos.BatchSettlementVO();
        boolean locked = FinanceConst.SETTLE_LOCKED.equals(settlement.getStatus());
        vo.setSettlementId(settlement.getId());
        vo.setStatus(settlement.getStatus());
        vo.setSettledAt(FinanceSupport.format(settlement.getSettledAt()));
        vo.setCalcAt(FinanceSupport.format(settlement.getCalcAt()));
        vo.setProfit(settlement.getProfit());
        vo.setSaleIncome(settlement.getSaleIncome());
        vo.setReceivedAmount(FinanceSupport.scale2(TenantSkip.call(() -> orderMapper.selectList(
                        Wrappers.<SaleOrder>lambdaQuery().eq(SaleOrder::getBatchId, batch.batchId())))
                .stream().map(SaleOrder::getReceivedAmount).map(FinanceSupport::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        vo.setCostTotal(settlement.getCostTotal());
        vo.setPerHeadBasis(locked ? "SOLD" : "STOCK");
        vo.setPerHeadCount(locked ? (settlement.getSoldCount() == null ? 0 : settlement.getSoldCount())
                : (batch.currentCount() == null ? 0 : batch.currentCount()));
        vo.setVersion(settlement.getVersion());
        vo.setCostPerHead(settlement.getCostPerHead());
        vo.setProfitPerHead(settlement.getProfitPerHead());
        vo.setFcr(settlement.getFcr());
        vo.setFeedKg(settlement.getFeedKg());
        vo.setNetGainKg(settlement.getNetGainKg());

        SettlementVos.SettlementBatch batchVo = new SettlementVos.SettlementBatch();
        batchVo.setBatchId(batch.batchId());
        batchVo.setBatchNo(batch.batchNo());
        batchVo.setPenName(primaryPenName(batch));
        batchVo.setBreedName(support.breedName(batch.breedId()));
        batchVo.setCategoryName(support.categoryName(batch.categoryId()));
        batchVo.setCountUnitText(support.countUnitText(batch.categoryId()));
        batchVo.setDayAge((batch.entryAgeDays() == null ? 0 : batch.entryAgeDays())
                + FinanceSupport.daysBetween(batch.entryDate(), BeijingTime.today()));
        batchVo.setEntryCount(batch.entryCount());
        batchVo.setDeathCount(batch.deathCount());
        batchVo.setSoldCount(batch.soldCount());
        batchVo.setCurrentCount(batch.currentCount());
        vo.setBatch(batchVo);

        BigDecimal total = FinanceSupport.nz(settlement.getCostTotal());
        List<SettlementVos.CostItem> costs = new ArrayList<>();
        addCost(costs, FinanceConst.COST_FEED, settlement.getCostFeed(), total, null);
        addCost(costs, FinanceConst.COST_INTRO, settlement.getCostIntro(), total,
                settlement.getIntroCount() == null || settlement.getIntroUnitPrice() == null ? null
                        : settlement.getIntroCount() + " 头 × " + plain(settlement.getIntroUnitPrice()) + " 元");
        addCost(costs, FinanceConst.COST_DRUG, settlement.getCostDrug(), total, null);
        addCost(costs, FinanceConst.COST_OTHER, settlement.getCostOther(), total, "人工、水电等");
        vo.setCosts(costs);

        List<SaleOrder> orders = TenantSkip.call(() -> orderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getBatchId, batch.batchId())
                .orderByAsc(SaleOrder::getSaleDate)));
        for (SaleOrder order : orders) {
            SettlementVos.SettlementSaleOrder item = new SettlementVos.SettlementSaleOrder();
            item.setId(order.getId());
            item.setSaleDate(FinanceSupport.format(order.getSaleDate()));
            item.setQuantity(order.getQuantity());
            item.setTotalWeight(order.getTotalWeight());
            item.setUnitPrice(order.getUnitPrice());
            item.setPaymentStatus(order.getPaymentStatus());
            item.setSaleAmount(order.getSaleAmount());
            vo.getSaleOrders().add(item);
        }

        List<BatchSettlementSupplement> pending = supplementMapper.selectList(
                Wrappers.<BatchSettlementSupplement>lambdaQuery()
                        .eq(BatchSettlementSupplement::getBatchId, batch.batchId())
                        .eq(BatchSettlementSupplement::getStatus, FinanceConst.SUPPLEMENT_PENDING)
                        .orderByAsc(BatchSettlementSupplement::getBizDate));
        BigDecimal deltaOther = BigDecimal.ZERO;
        BigDecimal deltaTotal = BigDecimal.ZERO;
        for (BatchSettlementSupplement supplement : pending) {
            SettlementVos.Supplement item = new SettlementVos.Supplement();
            item.setId(supplement.getId());
            item.setBizDate(FinanceSupport.format(supplement.getBizDate()));
            item.setTitle(supplement.getTitle());
            item.setAmount(supplement.getAmount());
            item.setCostGroup(supplement.getCostGroup());
            vo.getSupplements().add(item);
            BigDecimal amount = FinanceSupport.nz(supplement.getAmount());
            deltaTotal = deltaTotal.add(amount);
            if (FinanceConst.COST_OTHER.equals(supplement.getCostGroup())) {
                deltaOther = deltaOther.add(amount);
            }
            SettlementVos.CostRecord record = new SettlementVos.CostRecord();
            record.setTitle(supplement.getTitle());
            record.setBizDate(FinanceSupport.format(supplement.getBizDate()));
            record.setAmount(supplement.getAmount());
            record.setCostGroupText(FinanceConst.costGroupText(supplement.getCostGroup()));
            vo.getRecentCostRecords().add(record);
        }
        if (!pending.isEmpty()) {
            SettlementVos.PreviewAfter after = new SettlementVos.PreviewAfter();
            after.setCostOther(FinanceSupport.scale2(FinanceSupport.nz(settlement.getCostOther()).add(deltaOther)));
            after.setCostTotal(FinanceSupport.scale2(FinanceSupport.nz(settlement.getCostTotal()).add(deltaTotal)));
            after.setProfit(FinanceSupport.scale2(FinanceSupport.nz(settlement.getSaleIncome())
                    .subtract(after.getCostTotal())));
            vo.setPreviewAfter(after);
        }
        return vo;
    }

    private static void addCost(List<SettlementVos.CostItem> costs, String group, BigDecimal amount,
                                BigDecimal total, String note) {
        if (FinanceSupport.nz(amount).signum() <= 0) {
            return;
        }
        SettlementVos.CostItem item = new SettlementVos.CostItem();
        item.setCostGroup(group);
        item.setCostGroupText(FinanceConst.costGroupText(group));
        item.setAmount(FinanceSupport.scale2(amount));
        item.setShareRate(total.signum() == 0 ? BigDecimal.ZERO
                : amount.divide(total, 4, RoundingMode.HALF_UP));
        item.setNote(note);
        costs.add(item);
    }

    private String primaryPenName(BatchInfo batch) {
        Set<Long> penIds = new LinkedHashSet<>();
        if (batch.penIds() != null) {
            penIds.addAll(batch.penIds());
        }
        if (penIds.isEmpty() && batch.entryPenId() != null) {
            penIds.add(batch.entryPenId());
        }
        Map<Long, String> names = support.penNames(penIds);
        for (Long penId : penIds) {
            if (names.get(penId) != null) {
                return names.get(penId);
            }
        }
        return null;
    }

    private static String plain(BigDecimal value) {
        return FinanceSupport.scale2(value).stripTrailingZeros().toPlainString();
    }
}
