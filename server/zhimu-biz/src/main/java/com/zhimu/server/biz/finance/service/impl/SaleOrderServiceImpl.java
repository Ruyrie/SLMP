package com.zhimu.server.biz.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.finance.entity.BatchSettlement;
import com.zhimu.server.biz.finance.entity.BatchSettlementSupplement;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleAdvice;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.entity.SaleReceipt;
import com.zhimu.server.biz.finance.mapper.BatchSettlementMapper;
import com.zhimu.server.biz.finance.mapper.BatchSettlementSupplementMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleAdviceMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.mapper.SaleReceiptMapper;
import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.service.SaleOrderService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.SaleOrderVos;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** API-E14–E23：出栏批次选择、预填、登记、列表、待收款、详情、修改、作废与收款。 */
@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements SaleOrderService {

    private static final DateTimeFormatter SALE_NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final SaleOrderMapper orderMapper;
    private final SaleReceiptMapper receiptMapper;
    private final FinanceRecordMapper recordMapper;
    private final FinanceCategoryMapper categoryMapper;
    private final BatchSettlementMapper settlementMapper;
    private final BatchSettlementSupplementMapper supplementMapper;
    private final SaleAdviceMapper adviceMapper;
    private final BatchSalePort batchSalePort;
    private final BatchQueryService batchQueryService;
    private final MarketPriceService marketPriceService;
    private final AccountQueryService accountQueryService;
    private final FinanceSupport support;

    // ===== E14 可出栏批次 =====

    @Override
    public List<SaleOrderVos.BatchOptionVO> batchOptions() {
        Long farmId = support.farmId();
        List<BatchInfo> batches = batchQueryService.listActive(farmId);
        List<SaleOrderVos.BatchOptionVO> result = new ArrayList<>();
        for (BatchInfo batch : batches) {
            if (batch.currentCount() == null || batch.currentCount() <= 0) {
                continue;
            }
            SaleOrderVos.BatchOptionVO vo = new SaleOrderVos.BatchOptionVO();
            vo.setBatchId(batch.batchId());
            vo.setBatchNo(batch.batchNo());
            vo.setCategoryId(batch.categoryId());
            vo.setCategoryName(support.categoryName(batch.categoryId()));
            vo.setBreedName(support.breedName(batch.breedId()));
            vo.setCountUnit(support.countUnit(batch.categoryId()));
            vo.setCountUnitText(support.countUnitText(batch.categoryId()));
            vo.setEntryDate(FinanceSupport.format(batch.entryDate()));
            int dayAge = (batch.entryAgeDays() == null ? 0 : batch.entryAgeDays())
                    + FinanceSupport.daysBetween(batch.entryDate(), BeijingTime.today());
            vo.setDayAge(dayAge);
            vo.setCurrentCount(batch.currentCount());
            List<SaleOrderVos.PenStock> pens = penStocks(batch);
            vo.setPens(pens);
            vo.setPenName(pens.stream().max(Comparator.comparing(SaleOrderVos.PenStock::getStockCount))
                    .map(SaleOrderVos.PenStock::getPenName).orElse(null));
            vo.setInWithdrawal(false);
            SaleAdvice withdrawal = withdrawalOf(batch.batchId());
            if (withdrawal != null && withdrawal.getSellableDate() != null
                    && !withdrawal.getSellableDate().isBefore(BeijingTime.today())) {
                vo.setInWithdrawal(true);
                vo.setSellableDate(FinanceSupport.format(withdrawal.getSellableDate()));
                vo.setWithdrawalDaysLeft(FinanceSupport.daysBetween(BeijingTime.today(),
                        withdrawal.getSellableDate()));
                vo.setWithdrawalDrugName(drugName(withdrawal.getReasonWithdrawal()));
            }
            result.add(vo);
        }
        return result;
    }

    // ===== E15 登记预填 =====

    @Override
    public SaleOrderVos.PrefillVO prefill(Long batchId, String saleDate) {
        Long farmId = support.farmId();
        LocalDate date = saleDate == null || saleDate.isBlank() ? BeijingTime.today() : parseDate(saleDate, "sale_date");
        BatchInfo batch = batchId == null ? null : support.requireBatch(batchId, farmId);
        Long categoryId = batch != null ? batch.categoryId() : marketPriceService.defaultCategoryId(farmId);

        SaleOrderVos.PrefillVO vo = new SaleOrderVos.PrefillVO();
        vo.setMarketPrice(marketPriceService.prefillPrice(farmId, categoryId, date));

        List<SaleOrder> recent = TenantSkip.call(() -> orderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getFarmId, farmId)
                .orderByDesc(SaleOrder::getCreatedAt)
                .last("LIMIT 10")));
        LinkedHashSet<String> buyers = new LinkedHashSet<>();
        for (SaleOrder order : recent) {
            if (order.getBuyerName() != null && buyers.size() < 5) {
                buyers.add(order.getBuyerName());
            }
        }
        vo.setRecentBuyers(new ArrayList<>(buyers));
        if (!recent.isEmpty()) {
            vo.setLastBuyer(recent.get(0).getBuyerName());
        }
        List<SaleReceipt> receipts = TenantSkip.call(() -> receiptMapper.selectList(Wrappers.<SaleReceipt>lambdaQuery()
                .eq(SaleReceipt::getFarmId, farmId)
                .eq(SaleReceipt::getStatus, "NORMAL")
                .orderByDesc(SaleReceipt::getCreatedAt)
                .last("LIMIT 1")));
        if (!receipts.isEmpty()) {
            vo.setLastPaymentMethod(receipts.get(0).getPaymentMethod());
        }
        return vo;
    }

    // ===== E16 登记出栏单 =====

    @Override
    @Transactional
    public SaleOrderVos.CreatedVO create(FinanceRequests.SaleOrderRequest request, String idempotencyKey) {
        Long farmId = support.farmId();
        Long userId = UserContext.requireUserId();
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            SaleOrder existing = orderMapper.selectOne(Wrappers.<SaleOrder>lambdaQuery()
                    .eq(SaleOrder::getFarmId, farmId)
                    .eq(SaleOrder::getClientRequestId, key)
                    .last("LIMIT 1"));
            if (existing != null) {
                return createdVO(existing, remaining(existing.getBatchId(), 0));
            }
        }
        FinanceErrors.require(request != null, "batch_id", "请选择批次");
        BatchInfo batch = support.requireBatch(request.getBatchId(), farmId);
        if (!batch.active() || batch.currentCount() == null || batch.currentCount() <= 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该批次已全部出栏，不能再登记出栏单");
        }
        LocalDate saleDate = parseDate(request.getSaleDate(), "sale_date");
        validateSaleDate(saleDate, batch);
        checkWithdrawal(batch, saleDate);
        int quantity = validateQuantity(request.getQuantity(), batch.currentCount());
        validatePrice(request.getTotalWeight(), request.getUnitPrice(), request.getPricingMethod(),
                request.getBuyerName());

        List<SaleOrderVos.PenStock> stocks = penStocks(batch);
        List<SaleOrderVos.PenAllocation> allocations = allocations(request.getPenQuantities(), quantity, stocks);
        LocalDate dueDate = request.getDueDate() == null || request.getDueDate().isBlank() ? null
                : parseDate(request.getDueDate(), "due_date");
        if (dueDate != null && dueDate.isBefore(saleDate)) {
            throw FinanceErrors.field("due_date", "约定收款日期不能早于出栏日期");
        }
        LocalDate effectiveDue = dueDate == null ? saleDate.plusDays(7) : dueDate;
        BigDecimal avgWeight = request.getTotalWeight().divide(BigDecimal.valueOf(quantity), 1,
                RoundingMode.HALF_UP);
        BigDecimal saleAmount = saleAmount(request.getPricingMethod(), request.getTotalWeight(),
                request.getUnitPrice(), quantity);
        String saleNo = nextSaleNo(saleDate);
        BigDecimal refPrice = marketPriceService.latest(farmId, batch.categoryId(), FinanceConst.MARKET_SLAUGHTER,
                saleDate) == null ? null
                : marketPriceService.latest(farmId, batch.categoryId(), FinanceConst.MARKET_SLAUGHTER, saleDate)
                .getPrice();

        SaleOrder order = new SaleOrder();
        order.setFarmId(farmId);
        order.setBatchId(batch.batchId());
        order.setSaleNo(saleNo);
        order.setPenId(primaryPen(allocations));
        order.setPenAllocations(FinanceSupport.json(allocations));
        order.setCategoryId(batch.categoryId());
        order.setBreedId(batch.breedId());
        order.setSaleDate(saleDate);
        order.setQuantity(quantity);
        order.setTotalWeight(FinanceSupport.scale2(request.getTotalWeight()));
        order.setAvgWeight(avgWeight);
        order.setPricingMethod(request.getPricingMethod());
        order.setUnitPrice(FinanceSupport.scale2(request.getUnitPrice()));
        order.setRefUnitPrice(refPrice);
        order.setSaleAmount(saleAmount);
        order.setBuyerName(request.getBuyerName().trim());
        order.setDueDate(dueDate);
        order.setEffectiveDueDate(effectiveDue);
        order.setReceivedAmount(BigDecimal.ZERO);
        order.setUnpaidAmount(saleAmount);
        order.setReceiptCount(0);
        order.setPaymentStatus(FinanceConst.PAY_UNPAID);
        order.setEndedBatch(false);
        order.setContentVersion(0);
        order.setClientRequestId(key);
        order.setVersion(0);
        order.setCreatedBy(userId);
        order.setUpdatedBy(userId);
        orderMapper.insert(order);

        Map<Long, Integer> penMap = allocations.stream().collect(Collectors.toMap(
                SaleOrderVos.PenAllocation::getPenId, SaleOrderVos.PenAllocation::getQuantity, Integer::sum,
                LinkedHashMap::new));
        int remaining = batchSalePort.saleOut(farmId, batch.batchId(), saleDate, penMap, order.getId());
        boolean ended = remaining >= 0 ? remaining == 0 : quantity >= batch.currentCount();
        int remainingCount = remaining >= 0 ? remaining
                : Math.max(0, batch.currentCount() - quantity);
        if (ended) {
            order.setEndedBatch(true);
            order.setUpdatedBy(userId);
            orderMapper.updateById(order);
        }
        return createdVO(order, remainingCount);
    }

    // ===== E17 出栏单列表 =====

    @Override
    public SaleOrderVos.OrderPageVO list(FinanceQueries.SaleOrderQuery query) {
        Long farmId = support.farmId();
        List<SaleOrder> all = TenantSkip.call(() -> orderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getFarmId, farmId)
                .orderByDesc(SaleOrder::getSaleDate)
                .orderByDesc(SaleOrder::getCreatedAt)));
        if (query.getMonth() != null && !query.getMonth().isBlank()) {
            YearMonth month = YearMonth.parse(query.getMonth(), BeijingTime.MONTH);
            all = all.stream().filter(o -> YearMonth.from(o.getSaleDate()).equals(month))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        Map<Long, String> batchNos = batchNoMap(all);
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        List<SaleOrder> keywordFiltered = all;
        if (keyword != null && !keyword.isEmpty()) {
            keywordFiltered = all.stream().filter(o -> contains(batchNos.get(o.getBatchId()), keyword)
                            || contains(o.getBuyerName(), keyword))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        String status = query.getStatus() == null ? FinanceConst.ALL : query.getStatus();
        List<SaleOrder> filtered = keywordFiltered.stream()
                .filter(o -> FinanceConst.ALL.equals(status) || status.equals(o.getPaymentStatus()))
                .collect(Collectors.toCollection(ArrayList::new));

        SaleOrderVos.OrderPageVO vo = new SaleOrderVos.OrderPageVO();
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", keywordFiltered.size());
        for (String key : List.of(FinanceConst.PAY_UNPAID, FinanceConst.PAY_PARTIAL, FinanceConst.PAY_PAID,
                FinanceConst.PAY_VOIDED)) {
            tabs.put(key, (int) keywordFiltered.stream().filter(o -> key.equals(o.getPaymentStatus())).count());
        }
        vo.setTabCounts(tabs);

        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<SaleOrderVos.ItemVO> list = new ArrayList<>();
        Set<YearMonth> months = new LinkedHashSet<>();
        Map<Long, String> penNames = new HashMap<>();
        all.forEach(o -> penNames.putIfAbsent(o.getPenId(), support.penName(o.getPenId())));
        for (SaleOrder order : filtered.subList(from, to)) {
            list.add(toItem(order, batchNos.get(order.getBatchId()), penNames.get(order.getPenId())));
            months.add(YearMonth.from(order.getSaleDate()));
        }
        vo.setList(list);
        vo.setTotal(filtered.size());
        vo.setPage(page);
        vo.setSize(size);
        vo.setHasMore((long) page * size < filtered.size());
        for (YearMonth month : months) {
            SaleOrderVos.MonthGroup group = new SaleOrderVos.MonthGroup();
            group.setMonth(FinanceSupport.formatMonth(month));
            group.setLabel(month.getYear() == BeijingTime.today().getYear() ? month.getMonthValue() + "月"
                    : month.getYear() + "年" + month.getMonthValue() + "月");
            List<SaleOrder> ofMonth = keywordFiltered.stream()
                    .filter(o -> YearMonth.from(o.getSaleDate()).equals(month)).toList();
            group.setCount(ofMonth.size());
            group.setSaleAmount(FinanceSupport.scale2(ofMonth.stream()
                    .filter(o -> !FinanceConst.PAY_VOIDED.equals(o.getPaymentStatus()))
                    .map(SaleOrder::getSaleAmount).map(FinanceSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add)));
            vo.getMonthGroups().add(group);
        }
        return vo;
    }

    // ===== E18 待收款 =====

    @Override
    public SaleOrderVos.ReceivableVO receivables(FinanceQueries.ReceivableQuery query) {
        Long farmId = support.farmId();
        LocalDate today = BeijingTime.today();
        List<SaleOrder> open = TenantSkip.call(() -> orderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getFarmId, farmId)
                .in(SaleOrder::getPaymentStatus, FinanceConst.PAY_UNPAID, FinanceConst.PAY_PARTIAL)
                .isNotNull(SaleOrder::getEffectiveDueDate)));
        String filter = query.getDueFilter() == null ? FinanceConst.ALL : query.getDueFilter();
        List<SaleOrder> filtered = open.stream().filter(o -> switch (filter) {
                    case "OVERDUE" -> today.isAfter(o.getEffectiveDueDate());
                    case "DUE_7D" -> !today.isAfter(o.getEffectiveDueDate())
                            && ChronoUnit.DAYS.between(today, o.getEffectiveDueDate()) <= 7;
                    default -> true;
                })
                .sorted(Comparator.comparing((SaleOrder o) -> !today.isAfter(o.getEffectiveDueDate()))
                        .thenComparing(SaleOrder::getEffectiveDueDate))
                .collect(Collectors.toCollection(ArrayList::new));

        SaleOrderVos.ReceivableVO vo = new SaleOrderVos.ReceivableVO();
        vo.setRemindRuleText(FinanceConst.reminderRuleText());
        vo.setReminderReceivers(support.context().isOwner() ? "OWNER_AND_MANAGERS" : "YOU_OWNER_OTHER_MANAGERS");
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", open.size());
        tabs.put("OVERDUE", (int) open.stream().filter(o -> today.isAfter(o.getEffectiveDueDate())).count());
        tabs.put("DUE_7D", (int) open.stream().filter(o -> !today.isAfter(o.getEffectiveDueDate())
                && ChronoUnit.DAYS.between(today, o.getEffectiveDueDate()) <= 7).count());
        vo.setTabCounts(tabs);

        Map<Long, String> batchNos = batchNoMap(open);
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        for (SaleOrder order : filtered.subList(from, to)) {
            vo.getList().add(toItem(order, batchNos.get(order.getBatchId()), support.penName(order.getPenId())));
        }
        vo.setTotal(filtered.size());
        vo.setPage(page);
        vo.setSize(size);
        vo.setHasMore((long) page * size < filtered.size());

        SaleOrderVos.ReceivableSummary summary = new SaleOrderVos.ReceivableSummary();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal overdueTotal = BigDecimal.ZERO;
        int overdueCount = 0;
        LocalDate nearest = null;
        for (SaleOrder order : open) {
            total = total.add(FinanceSupport.nz(order.getUnpaidAmount()));
            if (today.isAfter(order.getEffectiveDueDate())) {
                overdueCount++;
                overdueTotal = overdueTotal.add(FinanceSupport.nz(order.getUnpaidAmount()));
            } else if (nearest == null || order.getEffectiveDueDate().isBefore(nearest)) {
                nearest = order.getEffectiveDueDate();
            }
        }
        summary.setTotalUnpaidAmount(FinanceSupport.scale2(total));
        summary.setOrderCount(open.size());
        summary.setOverdueCount(overdueCount);
        summary.setOverdueUnpaidAmount(FinanceSupport.scale2(overdueTotal));
        summary.setNearestDueDate(FinanceSupport.format(nearest));
        vo.setSummary(summary);
        return vo;
    }

    // ===== E19 详情 =====

    @Override
    public SaleOrderVos.DetailVO detail(Long id) {
        return detailOf(requireOrder(id, support.farmId()));
    }

    // ===== E20 修改未收款出栏单 =====

    @Override
    @Transactional
    public SaleOrderVos.DetailVO update(Long id, FinanceRequests.SaleOrderRequest request) {
        Long farmId = support.farmId();
        Long userId = UserContext.requireUserId();
        SaleOrder order = requireOrder(id, farmId);
        if (FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张出栏单已作废");
        }
        if (FinanceSupport.nz(order.getReceivedAmount()).signum() > 0) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED,
                    "这张出栏单已有收款，修改数量、单价或作废需要提交修改申请");
        }
        support.checkVersion(order.getVersion(), request.getVersion());
        BatchInfo batch = support.requireBatch(order.getBatchId(), farmId);
        LocalDate saleDate = parseDate(request.getSaleDate(), "sale_date");
        validateSaleDate(saleDate, batch);
        checkWithdrawal(batch, saleDate);
        int maxQuantity = order.getQuantity() + (batch.currentCount() == null ? 0 : batch.currentCount());
        int quantity = validateQuantity(request.getQuantity(), maxQuantity,
                "不能超过 " + maxQuantity + " 头（原出栏 " + order.getQuantity() + " 头加批次当前存栏 "
                        + (batch.currentCount() == null ? 0 : batch.currentCount()) + " 头）");
        validatePrice(request.getTotalWeight(), request.getUnitPrice(), request.getPricingMethod(),
                request.getBuyerName());
        List<SaleOrderVos.PenStock> stocks = penStocks(batch);
        List<SaleOrderVos.PenAllocation> oldAllocations = allocationsOf(order);
        List<SaleOrderVos.PenAllocation> allocations = request.getPenQuantities() == null
                ? adjustAllocations(oldAllocations, order.getQuantity(), quantity, stocks)
                : allocations(request.getPenQuantities(), quantity, stocks);
        LocalDate dueDate = request.getDueDate() == null || request.getDueDate().isBlank() ? null
                : parseDate(request.getDueDate(), "due_date");
        if (dueDate != null && dueDate.isBefore(saleDate)) {
            throw FinanceErrors.field("due_date", "约定收款日期不能早于出栏日期");
        }
        BigDecimal avgWeight = request.getTotalWeight().divide(BigDecimal.valueOf(quantity), 1,
                RoundingMode.HALF_UP);
        BigDecimal saleAmount = saleAmount(request.getPricingMethod(), request.getTotalWeight(),
                request.getUnitPrice(), quantity);

        order.setPenId(primaryPen(allocations));
        order.setPenAllocations(FinanceSupport.json(allocations));
        order.setSaleDate(saleDate);
        order.setQuantity(quantity);
        order.setTotalWeight(FinanceSupport.scale2(request.getTotalWeight()));
        order.setAvgWeight(avgWeight);
        order.setPricingMethod(request.getPricingMethod());
        order.setUnitPrice(FinanceSupport.scale2(request.getUnitPrice()));
        order.setSaleAmount(saleAmount);
        order.setUnpaidAmount(saleAmount);
        order.setBuyerName(request.getBuyerName().trim());
        order.setDueDate(dueDate);
        order.setEffectiveDueDate(dueDate == null ? saleDate.plusDays(7) : dueDate);
        order.setContentVersion((order.getContentVersion() == null ? 0 : order.getContentVersion()) + 1);
        order.setUpdatedBy(userId);
        if (orderMapper.updateById(order) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        Map<Long, Integer> delta = new LinkedHashMap<>();
        Map<Long, Integer> oldMap = quantityMap(oldAllocations);
        Map<Long, Integer> newMap = quantityMap(allocations);
        Set<Long> penIds = new HashSet<>(oldMap.keySet());
        penIds.addAll(newMap.keySet());
        for (Long penId : penIds) {
            int diff = newMap.getOrDefault(penId, 0) - oldMap.getOrDefault(penId, 0);
            if (diff != 0) {
                delta.put(penId, diff);
            }
        }
        if (!delta.isEmpty()) {
            batchSalePort.adjustSale(farmId, batch.batchId(), saleDate, delta, order.getId());
        }
        return detailOf(order);
    }

    // ===== E21 作废未收款出栏单 =====

    @Override
    @Transactional
    public SaleOrderVos.DetailVO voidOrder(Long id, FinanceRequests.SaleOrderVoidRequest request) {
        Long farmId = support.farmId();
        Long userId = UserContext.requireUserId();
        SaleOrder order = requireOrder(id, farmId);
        if (FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张出栏单已作废");
        }
        if (FinanceSupport.nz(order.getReceivedAmount()).signum() > 0) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED,
                    "这张出栏单已有收款，修改数量、单价或作废需要提交修改申请");
        }
        String reason = request == null || request.getReason() == null ? "" : request.getReason().trim();
        FinanceErrors.require(!reason.isEmpty(), "reason", "请填写作废原因");
        FinanceErrors.require(reason.codePointCount(0, reason.length()) <= 200, "reason", "作废原因不能超过 200 字");
        support.checkVersion(order.getVersion(), request.getVersion());
        order.setPaymentStatus(FinanceConst.PAY_VOIDED);
        order.setUnpaidAmount(BigDecimal.ZERO);
        order.setVoidReason(reason);
        order.setVoidSource("DIRECT");
        order.setVoidedAt(BeijingTime.now());
        order.setVoidedBy(userId);
        order.setContentVersion((order.getContentVersion() == null ? 0 : order.getContentVersion()) + 1);
        order.setUpdatedBy(userId);
        if (orderMapper.updateById(order) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        batchSalePort.restoreSale(farmId, order.getBatchId(), order.getSaleDate(),
                quantityMap(allocationsOf(order)), order.getId());
        return detailOf(order);
    }

    // ===== E22 登记收款 =====

    @Override
    @Transactional
    public SaleOrderVos.DetailVO addReceipt(Long id, FinanceRequests.SaleReceiptRequest request,
                                            String idempotencyKey) {
        Long farmId = support.farmId();
        Long userId = UserContext.requireUserId();
        SaleOrder order = requireOrder(id, farmId);
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            SaleReceipt existing = receiptMapper.selectOne(Wrappers.<SaleReceipt>lambdaQuery()
                    .eq(SaleReceipt::getFarmId, farmId)
                    .eq(SaleReceipt::getClientRequestId, key)
                    .last("LIMIT 1"));
            if (existing != null) {
                SaleOrderVos.DetailVO vo = detailOf(order);
                SaleOrderVos.ReceiptResult result = new SaleOrderVos.ReceiptResult();
                result.setAmount(existing.getAmount());
                result.setUnpaidAmount(order.getUnpaidAmount());
                result.setPaidOff(existing.getAmount().compareTo(FinanceSupport.nz(order.getUnpaidAmount())) == 0);
                vo.setReceiptResult(result);
                return vo;
            }
        }
        if (FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张出栏单已作废，不能登记收款");
        }
        if (FinanceConst.PAY_PAID.equals(order.getPaymentStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张出栏单已收齐，不需要再登记收款");
        }
        FinanceErrors.require(request != null && request.getAmount() != null, "amount", "请输入收款金额");
        FinanceErrors.require(request.getAmount().signum() > 0, "amount", "收款金额必须大于 0");
        FinanceErrors.require(request.getAmount().stripTrailingZeros().scale() <= 2, "amount", "金额最多保留 2 位小数");
        BigDecimal unpaid = FinanceSupport.nz(order.getUnpaidAmount());
        if (request.getAmount().compareTo(unpaid) > 0) {
            throw FinanceErrors.field("amount", "不能超过未收金额 " + money(unpaid) + " 元");
        }
        LocalDate receiptDate = parseDate(request.getReceiptDate(), "receipt_date");
        FinanceErrors.require(!receiptDate.isAfter(BeijingTime.today()), "receipt_date", "收款日期不能晚于今天");
        FinanceErrors.require(!receiptDate.isBefore(order.getSaleDate()), "receipt_date",
                "收款日期不能早于出栏日期" + cnDate(order.getSaleDate()));
        FinanceErrors.require(request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank(),
                "payment_method", "请选择收款方式");

        SaleReceipt receipt = new SaleReceipt();
        receipt.setFarmId(farmId);
        receipt.setSaleOrderId(order.getId());
        receipt.setBatchId(order.getBatchId());
        receipt.setAmount(FinanceSupport.scale2(request.getAmount()));
        receipt.setReceiptDate(receiptDate);
        receipt.setPaymentMethod(request.getPaymentMethod());
        receipt.setStatus("NORMAL");
        receipt.setClientRequestId(key);
        receipt.setCreatedBy(userId);
        receiptMapper.insert(receipt);

        FinanceCategory category = categoryMapper.selectOne(Wrappers.<FinanceCategory>lambdaQuery()
                .eq(FinanceCategory::getCode, "SALE")
                .eq(FinanceCategory::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (category == null) {
            throw new BizException(ErrorCode.SERVER_ERROR, "出栏销售分类不存在");
        }
        FinanceRecord record = new FinanceRecord();
        record.setFarmId(farmId);
        record.setRecordType(FinanceConst.INCOME);
        record.setSourceType(FinanceConst.SOURCE_SALE);
        record.setSourceId(receipt.getId());
        record.setSourceNo(order.getSaleNo());
        record.setSaleOrderId(order.getId());
        record.setCategoryId(category.getId());
        record.setAmount(receipt.getAmount());
        record.setRecordDate(receiptDate);
        record.setBatchId(order.getBatchId());
        String receiptBatchNo = batchNo(order.getBatchId());
        record.setDetailText(receiptBatchNo == null ? null : receiptBatchNo
                + " · " + FinanceConst.paymentMethodText(receipt.getPaymentMethod()));
        record.setVoucherCount(0);
        record.setStatus(FinanceConst.RECORD_NORMAL);
        record.setVersion(0);
        record.setCreatedBy(0L);
        recordMapper.insert(record);
        receipt.setFinanceRecordId(record.getId());
        receiptMapper.updateById(receipt);

        BigDecimal received = FinanceSupport.nz(order.getReceivedAmount()).add(receipt.getAmount());
        BigDecimal newUnpaid = unpaid.subtract(receipt.getAmount());
        order.setReceivedAmount(FinanceSupport.scale2(received));
        order.setUnpaidAmount(FinanceSupport.scale2(newUnpaid));
        order.setReceiptCount((order.getReceiptCount() == null ? 0 : order.getReceiptCount()) + 1);
        order.setPaymentStatus(newUnpaid.signum() == 0 ? FinanceConst.PAY_PAID : FinanceConst.PAY_PARTIAL);
        if (newUnpaid.signum() == 0) {
            List<SaleReceipt> receipts = receiptMapper.selectList(Wrappers.<SaleReceipt>lambdaQuery()
                    .eq(SaleReceipt::getSaleOrderId, order.getId())
                    .eq(SaleReceipt::getStatus, "NORMAL"));
            order.setPaidOffDate(receipts.stream().map(SaleReceipt::getReceiptDate)
                    .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(receiptDate));
        }
        order.setUpdatedBy(userId);
        if (orderMapper.updateById(order) == 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张出栏单已收齐，不需要再登记收款");
        }
        SaleOrderVos.DetailVO vo = detailOf(order);
        SaleOrderVos.ReceiptResult result = new SaleOrderVos.ReceiptResult();
        result.setAmount(receipt.getAmount());
        result.setUnpaidAmount(order.getUnpaidAmount());
        result.setPaidOff(newUnpaid.signum() == 0);
        vo.setReceiptResult(result);
        return vo;
    }

    // ===== E23 修改申请预计结果 =====

    @Override
    public SaleOrderVos.ChangePreviewVO preview(Long id, FinanceRequests.SaleChangePreviewRequest request) {
        Long farmId = support.farmId();
        SaleOrder order = requireOrder(id, farmId);
        FinanceErrors.require(request != null, "change_type", "请选择修改类型");
        if (FinanceSupport.nz(order.getReceivedAmount()).signum() == 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张出栏单还没有收款，可以直接修改");
        }
        SaleOrderVos.ChangePreviewVO vo = new SaleOrderVos.ChangePreviewVO();
        BigDecimal before = FinanceSupport.nz(order.getSaleAmount());
        BigDecimal received = FinanceSupport.nz(order.getReceivedAmount());
        BigDecimal profitBefore = profitOf(order);
        vo.setSaleAmountBefore(before);
        vo.setUnpaidAmountBefore(FinanceSupport.nz(order.getUnpaidAmount()));
        vo.setReceivedAmount(received);
        vo.setProfitBefore(profitBefore);
        if ("VOID".equals(request.getChangeType())) {
            vo.setSaleAmountAfter(BigDecimal.ZERO);
            vo.setUnpaidAmountAfter(BigDecimal.ZERO);
            vo.setProfitAfter(profitBefore == null ? null : profitBefore.subtract(before));
            vo.setTurnToLoss(profitBefore != null && profitBefore.signum() >= 0
                    && vo.getProfitAfter() != null && vo.getProfitAfter().signum() < 0);
            vo.setRestoreCount(order.getQuantity());
            List<SaleReceipt> receipts = receiptMapper.selectList(Wrappers.<SaleReceipt>lambdaQuery()
                    .eq(SaleReceipt::getSaleOrderId, order.getId())
                    .eq(SaleReceipt::getStatus, "NORMAL"));
            vo.setVoidReceiptCount(receipts.size());
            vo.setVoidReceiptAmount(FinanceSupport.scale2(receipts.stream().map(SaleReceipt::getAmount)
                    .map(FinanceSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add)));
            return vo;
        }
        BigDecimal after = before;
        if ("unit_price".equals(request.getFieldCode())) {
            if (request.getNewUnitPrice() == null) {
                return vo;
            }
            FinanceErrors.require(request.getNewUnitPrice().signum() > 0, "new_unit_price", "单价必须大于 0");
            after = saleAmount(order.getPricingMethod(), order.getTotalWeight(), request.getNewUnitPrice(),
                    order.getQuantity());
        } else if ("quantity".equals(request.getFieldCode())) {
            if (request.getNewQuantity() == null) {
                return vo;
            }
            FinanceErrors.require(request.getNewQuantity() > 0, "new_quantity", "数量必须大于 0");
            BigDecimal totalWeight = request.getNewTotalWeight() != null ? request.getNewTotalWeight()
                    : FinanceSupport.nz(order.getAvgWeight()).multiply(BigDecimal.valueOf(request.getNewQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            after = saleAmount(order.getPricingMethod(), totalWeight, order.getUnitPrice(), request.getNewQuantity());
            vo.setRestoreCount(Math.max(0, order.getQuantity() - request.getNewQuantity()));
        } else {
            return vo;
        }
        vo.setSaleAmountAfter(after);
        vo.setUnpaidAmountAfter(FinanceSupport.scale2(after.subtract(received).max(BigDecimal.ZERO)));
        vo.setProfitAfter(profitBefore == null ? null : profitBefore.subtract(before.subtract(after)));
        vo.setTurnToLoss(profitBefore != null && profitBefore.signum() >= 0
                && vo.getProfitAfter() != null && vo.getProfitAfter().signum() < 0);
        return vo;
    }

    // ===== 内部 =====

    private SaleOrder requireOrder(Long id, Long farmId) {
        SaleOrder order = id == null ? null : orderMapper.selectById(id);
        if (order == null || !Objects.equals(farmId, order.getFarmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return order;
    }

    private SaleOrderVos.DetailVO detailOf(SaleOrder order) {
        SaleOrderVos.DetailVO vo = new SaleOrderVos.DetailVO();
        fillItem(vo, order, batchNo(order.getBatchId()), support.penName(order.getPenId()));
        vo.setSaleNo(order.getSaleNo());
        vo.setCategoryName(support.categoryName(order.getCategoryId()));
        vo.setBreedName(support.breedName(order.getBreedId()));
        vo.setPricingMethod(order.getPricingMethod());
        vo.setPricingMethodText(FinanceConst.pricingMethodText(order.getPricingMethod()));
        vo.setUnitPrice(order.getUnitPrice());
        vo.setAvgWeight(order.getAvgWeight());
        vo.setBatchStatus(batchStatus(order.getBatchId()));
        vo.setRegisteredAt(FinanceSupport.format(order.getCreatedAt()));
        vo.setContentVersion(order.getContentVersion());
        vo.setVersion(order.getVersion());
        vo.setRefUnitPrice(order.getRefUnitPrice());
        vo.setPenAllocations(allocationsOf(order));
        if (order.getCreatedBy() != null) {
            vo.setRegistrant(brief(order.getCreatedBy()));
        }
        List<SaleReceipt> receipts = TenantSkip.call(() -> receiptMapper.selectList(
                Wrappers.<SaleReceipt>lambdaQuery()
                        .eq(SaleReceipt::getSaleOrderId, order.getId())
                        .orderByAsc(SaleReceipt::getReceiptDate)
                        .orderByAsc(SaleReceipt::getId)));
        Map<Long, AccountBrief> names = accountNames(receipts.stream().map(SaleReceipt::getCreatedBy)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        for (SaleReceipt receipt : receipts) {
            SaleOrderVos.Receipt item = new SaleOrderVos.Receipt();
            item.setId(receipt.getId());
            item.setAmount(receipt.getAmount());
            item.setReceiptDate(FinanceSupport.format(receipt.getReceiptDate()));
            item.setPaymentMethod(receipt.getPaymentMethod());
            item.setPaymentMethodText(FinanceConst.paymentMethodText(receipt.getPaymentMethod()));
            AccountBrief operator = names.get(receipt.getCreatedBy());
            item.setOperatorName(operator == null ? null : operator.name());
            item.setFinanceRecordId(receipt.getFinanceRecordId());
            item.setStatus(receipt.getStatus());
            vo.getReceipts().add(item);
        }
        if (order.getPendingChangeRequestId() != null) {
            SaleOrderVos.PendingChange pending = new SaleOrderVos.PendingChange();
            pending.setChangeRequestId(order.getPendingChangeRequestId());
            pending.setSummaryText("修改申请审核中");
            vo.setPendingChange(pending);
        }
        boolean voided = FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus());
        boolean received = FinanceSupport.nz(order.getReceivedAmount()).signum() > 0;
        SaleOrderVos.Actions actions = new SaleOrderVos.Actions();
        actions.setCanReceive(!voided && !FinanceConst.PAY_PAID.equals(order.getPaymentStatus()));
        actions.setCanEdit(!voided && !received);
        actions.setCanVoid(!voided && !received);
        actions.setCanApplyChange(!voided && received);
        actions.setChangeInReview(order.getPendingChangeRequestId() != null);
        vo.setActions(actions);
        vo.setSettlementBrief(settlementBrief(order.getBatchId()));
        return vo;
    }

    private void fillItem(SaleOrderVos.ItemVO vo, SaleOrder order, String batchNo, String penName) {
        vo.setId(order.getId());
        vo.setBatchId(order.getBatchId());
        vo.setBatchNo(batchNo);
        vo.setPenName(penName);
        vo.setBuyerName(order.getBuyerName());
        vo.setSaleDate(FinanceSupport.format(order.getSaleDate()));
        vo.setQuantity(order.getQuantity());
        vo.setCountUnitText(support.countUnitText(order.getCategoryId()));
        vo.setTotalWeight(order.getTotalWeight());
        vo.setSaleAmount(order.getSaleAmount());
        vo.setReceivedAmount(order.getReceivedAmount());
        vo.setUnpaidAmount(order.getUnpaidAmount());
        vo.setProgressRate(progress(order));
        vo.setPaymentStatusText(FinanceConst.paymentStatusText(order.getPaymentStatus()));
        vo.setHasPendingChange(order.getPendingChangeRequestId() != null);
        vo.setEffectiveDueDate(FinanceSupport.format(order.getEffectiveDueDate()));
        vo.setVoidByReview("CHANGE_REQUEST".equals(order.getVoidSource()));
        vo.setCreatedAt(FinanceSupport.format(order.getCreatedAt()));
        vo.setPaymentStatus(order.getPaymentStatus());
        fillDue(vo, order);
        vo.setPaidOffDate(FinanceSupport.format(order.getPaidOffDate()));
        vo.setVoidedAt(FinanceSupport.format(order.getVoidedAt()));
        vo.setVoidReason(order.getVoidReason());
    }

    private static void fillDue(SaleOrderVos.ItemVO vo, SaleOrder order) {
        LocalDate today = BeijingTime.today();
        if (FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus())) {
            vo.setDueState("VOIDED");
            return;
        }
        if (FinanceConst.PAY_PAID.equals(order.getPaymentStatus())) {
            vo.setDueState("SETTLED");
            return;
        }
        LocalDate due = order.getEffectiveDueDate();
        if (due == null) {
            vo.setDueState("DUE_LATER");
            return;
        }
        long days = ChronoUnit.DAYS.between(today, due);
        if (days < 0) {
            vo.setDueState("OVERDUE");
            vo.setOverdueDays((int) -days);
            vo.setDueStateText("已逾期 " + (-days) + " 天");
        } else if (days <= 3) {
            vo.setDueState("DUE_SOON");
            vo.setDaysToDue((int) days);
            vo.setDueStateText(days == 0 ? "今天到期" : days == 1 ? "明天到期" : days + " 天后到期");
        } else {
            vo.setDueState("DUE_LATER");
            vo.setDaysToDue((int) days);
        }
    }

    private SaleOrderVos.ItemVO toItem(SaleOrder order, String batchNo, String penName) {
        SaleOrderVos.ItemVO vo = new SaleOrderVos.ItemVO();
        fillItem(vo, order, batchNo, penName);
        return vo;
    }

    private SaleOrderVos.SettlementBrief settlementBrief(Long batchId) {
        BatchSettlement settlement = settlementMapper.selectOne(Wrappers.<BatchSettlement>lambdaQuery()
                .eq(BatchSettlement::getBatchId, batchId)
                .last("LIMIT 1"));
        SaleOrderVos.SettlementBrief brief = new SaleOrderVos.SettlementBrief();
        brief.setStatus(settlement == null ? FinanceConst.SETTLE_RUNNING : settlement.getStatus());
        brief.setProfit(settlement == null ? null : settlement.getProfit());
        brief.setSaleIncome(settlement == null ? null : settlement.getSaleIncome());
        brief.setCostTotal(settlement == null ? null : settlement.getCostTotal());
        brief.setCostPerHead(settlement == null ? null : settlement.getCostPerHead());
        brief.setFcr(settlement == null ? null : settlement.getFcr());
        brief.setPendingSupplementCount((int) supplementMapper.selectCount(
                Wrappers.<BatchSettlementSupplement>lambdaQuery()
                        .eq(BatchSettlementSupplement::getBatchId, batchId)
                        .eq(BatchSettlementSupplement::getStatus, FinanceConst.SUPPLEMENT_PENDING)));
        return brief;
    }

    private BigDecimal profitOf(SaleOrder order) {
        BatchSettlement settlement = settlementMapper.selectOne(Wrappers.<BatchSettlement>lambdaQuery()
                .eq(BatchSettlement::getBatchId, order.getBatchId())
                .last("LIMIT 1"));
        if (settlement != null) {
            return settlement.getProfit();
        }
        return FinanceSupport.nz(order.getSaleAmount()).negate();
    }

    private List<SaleOrderVos.PenStock> penStocks(BatchInfo batch) {
        List<BatchSalePort.PenStock> port = batchSalePort.penStocks(batch.batchId());
        List<SaleOrderVos.PenStock> result = new ArrayList<>();
        if (port != null && !port.isEmpty()) {
            Map<Long, String> names = support.penNames(port.stream().map(BatchSalePort.PenStock::penId).toList());
            for (BatchSalePort.PenStock stock : port) {
                SaleOrderVos.PenStock vo = new SaleOrderVos.PenStock();
                vo.setPenId(stock.penId());
                vo.setPenName(stock.penName() != null ? stock.penName() : names.get(stock.penId()));
                vo.setStockCount(stock.stockCount());
                result.add(vo);
            }
            return result;
        }
        List<Long> penIds = batch.penIds() == null ? List.of() : batch.penIds();
        int count = batch.currentCount() == null ? 0 : batch.currentCount();
        Map<Long, String> names = support.penNames(penIds);
        if (penIds.size() <= 1) {
            SaleOrderVos.PenStock vo = new SaleOrderVos.PenStock();
            vo.setPenId(penIds.isEmpty() ? batch.entryPenId() : penIds.get(0));
            vo.setPenName(names.get(vo.getPenId()));
            vo.setStockCount(count);
            result.add(vo);
            return result;
        }
        int base = count / penIds.size();
        int remainder = count % penIds.size();
        for (int i = 0; i < penIds.size(); i++) {
            SaleOrderVos.PenStock vo = new SaleOrderVos.PenStock();
            vo.setPenId(penIds.get(i));
            vo.setPenName(names.get(penIds.get(i)));
            vo.setStockCount(base + (i == 0 ? remainder : 0));
            result.add(vo);
        }
        return result;
    }

    private List<SaleOrderVos.PenAllocation> allocations(List<FinanceRequests.PenQuantity> requested, int quantity,
                                                         List<SaleOrderVos.PenStock> stocks) {
        Map<Long, SaleOrderVos.PenStock> stockMap = stocks.stream()
                .collect(Collectors.toMap(SaleOrderVos.PenStock::getPenId, s -> s, (a, b) -> a, LinkedHashMap::new));
        if (requested == null || requested.isEmpty()) {
            if (stocks.size() != 1) {
                throw FinanceErrors.field("pen_quantities", "请填写各栏舍出栏数量");
            }
            SaleOrderVos.PenAllocation allocation = new SaleOrderVos.PenAllocation();
            allocation.setPenId(stocks.get(0).getPenId());
            allocation.setPenName(stocks.get(0).getPenName());
            allocation.setQuantity(quantity);
            return List.of(allocation);
        }
        int sum = 0;
        List<SaleOrderVos.PenAllocation> result = new ArrayList<>();
        for (FinanceRequests.PenQuantity item : requested) {
            if (item.getPenId() == null || item.getQuantity() == null) {
                throw FinanceErrors.field("pen_quantities", "请填写各栏舍出栏数量");
            }
            SaleOrderVos.PenStock stock = stockMap.get(item.getPenId());
            if (stock == null) {
                throw FinanceErrors.field("pen_quantities", "栏舍不属于该批次");
            }
            if (item.getQuantity() < 0 || item.getQuantity() > stock.getStockCount()) {
                throw FinanceErrors.field("pen_quantities",
                        stock.getPenName() + "本批次在栏 " + stock.getStockCount() + " 头，不能超过");
            }
            if (item.getQuantity() == 0) {
                continue;
            }
            SaleOrderVos.PenAllocation allocation = new SaleOrderVos.PenAllocation();
            allocation.setPenId(stock.getPenId());
            allocation.setPenName(stock.getPenName());
            allocation.setQuantity(item.getQuantity());
            result.add(allocation);
            sum += item.getQuantity();
        }
        if (sum != quantity) {
            throw FinanceErrors.field("pen_quantities", "各栏舍出栏数合计应等于出栏数量 " + quantity + " 头");
        }
        return result;
    }

    private List<SaleOrderVos.PenAllocation> adjustAllocations(List<SaleOrderVos.PenAllocation> old, int oldQuantity,
                                                               int newQuantity,
                                                               List<SaleOrderVos.PenStock> stocks) {
        if (old.isEmpty()) {
            return allocations(null, newQuantity, stocks);
        }
        List<SaleOrderVos.PenAllocation> result = new ArrayList<>();
        int remainder = newQuantity;
        for (int i = 0; i < old.size(); i++) {
            SaleOrderVos.PenAllocation item = old.get(i);
            SaleOrderVos.PenAllocation copy = new SaleOrderVos.PenAllocation();
            copy.setPenId(item.getPenId());
            copy.setPenName(item.getPenName());
            copy.setQuantity(i == old.size() - 1 ? remainder : Math.min(item.getQuantity(), remainder));
            remainder -= copy.getQuantity();
            if (copy.getQuantity() > 0) {
                result.add(copy);
            }
        }
        return result;
    }

    private static Map<Long, Integer> quantityMap(List<SaleOrderVos.PenAllocation> allocations) {
        Map<Long, Integer> map = new LinkedHashMap<>();
        for (SaleOrderVos.PenAllocation allocation : allocations) {
            map.merge(allocation.getPenId(), allocation.getQuantity() == null ? 0 : allocation.getQuantity(),
                    Integer::sum);
        }
        return map;
    }

    private List<SaleOrderVos.PenAllocation> allocationsOf(SaleOrder order) {
        SaleOrderVos.PenAllocation[] parsed = FinanceSupport.parse(order.getPenAllocations(),
                SaleOrderVos.PenAllocation[].class);
        return parsed == null ? new ArrayList<>() : new ArrayList<>(List.of(parsed));
    }

    private static Long primaryPen(List<SaleOrderVos.PenAllocation> allocations) {
        return allocations.stream().max(Comparator.comparing(SaleOrderVos.PenAllocation::getQuantity))
                .map(SaleOrderVos.PenAllocation::getPenId).orElse(null);
    }

    private void validateSaleDate(LocalDate saleDate, BatchInfo batch) {
        FinanceErrors.require(!saleDate.isAfter(BeijingTime.today()), "sale_date", "出栏日期不能晚于今天");
        if (batch.entryDate() != null && saleDate.isBefore(batch.entryDate())) {
            throw FinanceErrors.field("sale_date", "出栏日期不能早于入栏日期" + cnDate(batch.entryDate()));
        }
    }

    private void checkWithdrawal(BatchInfo batch, LocalDate saleDate) {
        SaleAdvice withdrawal = withdrawalOf(batch.batchId());
        if (withdrawal == null || withdrawal.getSellableDate() == null) {
            return;
        }
        if (saleDate.isBefore(withdrawal.getSellableDate())) {
            if (BeijingTime.today().isBefore(withdrawal.getSellableDate())) {
                throw new BizException(ErrorCode.WITHDRAWAL_PERIOD,
                        batch.batchNo() + " 在休药期内，可出栏日期" + cnDate(withdrawal.getSellableDate()),
                        Map.of("sellable_date", FinanceSupport.format(withdrawal.getSellableDate())));
            }
            throw FinanceErrors.field("sale_date",
                    "出栏日期不能早于可出栏日期" + cnDate(withdrawal.getSellableDate()));
        }
    }

    private SaleAdvice withdrawalOf(Long batchId) {
        List<SaleAdvice> advices = adviceMapper.selectList(Wrappers.<SaleAdvice>lambdaQuery()
                .eq(SaleAdvice::getBatchId, batchId)
                .eq(SaleAdvice::getStatus, FinanceConst.ADVICE_SUCCESS)
                .eq(SaleAdvice::getInWithdrawal, true)
                .isNotNull(SaleAdvice::getSellableDate)
                .orderByDesc(SaleAdvice::getGeneratedAt)
                .last("LIMIT 1"));
        return advices.isEmpty() ? null : advices.get(0);
    }

    private static String drugName(String reason) {
        if (reason == null) {
            return null;
        }
        int index = reason.indexOf("次用药");
        if (index > 0) {
            String name = reason.substring(0, index);
            return name.endsWith("粉末") ? name.substring(0, name.length() - 1) : name;
        }
        return null;
    }

    private static int validateQuantity(Integer quantity, int max) {
        return validateQuantity(quantity, max, "不能超过批次当前存栏 " + max + " 头");
    }

    private static int validateQuantity(Integer quantity, int max, String exceedMessage) {
        FinanceErrors.require(quantity != null, "quantity", "请输入数量");
        FinanceErrors.require(quantity > 0, "quantity", "数量必须大于 0");
        if (quantity > max) {
            throw FinanceErrors.field("quantity", exceedMessage);
        }
        return quantity;
    }

    private static void validatePrice(BigDecimal totalWeight, BigDecimal unitPrice, String method,
                                      String buyerName) {
        FinanceErrors.require(totalWeight != null, "total_weight", "请输入总重");
        FinanceErrors.require(totalWeight.signum() > 0, "total_weight", "总重必须大于 0");
        FinanceErrors.require(totalWeight.stripTrailingZeros().scale() <= 2, "total_weight", "总重最多保留 2 位小数");
        FinanceErrors.require(FinanceConst.PRICE_WEIGHT.equals(method) || FinanceConst.PRICE_HEAD.equals(method),
                "pricing_method", "请选择计价方式");
        FinanceErrors.require(unitPrice != null, "unit_price", "请输入单价");
        FinanceErrors.require(unitPrice.signum() > 0, "unit_price", "单价必须大于 0");
        FinanceErrors.require(unitPrice.stripTrailingZeros().scale() <= 2, "unit_price", "单价最多保留 2 位小数");
        FinanceErrors.require(unitPrice.compareTo(new BigDecimal("99999.99")) <= 0, "unit_price",
                "单价不能超过 99,999.99");
        String buyer = buyerName == null ? "" : buyerName.trim();
        FinanceErrors.require(!buyer.isEmpty(), "buyer_name", "请填写收购商");
        FinanceErrors.require(buyer.codePointCount(0, buyer.length()) <= 30, "buyer_name", "收购商名称不能超过 30 字");
    }

    private static BigDecimal saleAmount(String method, BigDecimal totalWeight, BigDecimal unitPrice, int quantity) {
        BigDecimal amount = FinanceConst.PRICE_HEAD.equals(method)
                ? unitPrice.multiply(BigDecimal.valueOf(quantity))
                : totalWeight.multiply(unitPrice);
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal progress(SaleOrder order) {
        BigDecimal sale = FinanceSupport.nz(order.getSaleAmount());
        if (sale.signum() == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return FinanceSupport.nz(order.getReceivedAmount()).divide(sale, 4, RoundingMode.HALF_UP);
    }

    private String nextSaleNo(LocalDate saleDate) {
        String prefix = "CL" + saleDate.format(SALE_NO_DATE);
        List<SaleOrder> orders = orderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .likeRight(SaleOrder::getSaleNo, prefix));
        int max = 0;
        for (SaleOrder order : orders) {
            String no = order.getSaleNo();
            if (no != null && no.length() == prefix.length() + 3) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 忽略异常单号
                }
            }
        }
        return prefix + String.format("%03d", max + 1);
    }

    private String batchNo(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            return batchQueryService.requireBatch(batchId).batchNo();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String batchStatus(Long batchId) {
        try {
            return batchQueryService.requireBatch(batchId).status();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Map<Long, String> batchNoMap(List<SaleOrder> orders) {
        Map<Long, String> map = new HashMap<>();
        for (Long id : orders.stream().map(SaleOrder::getBatchId).filter(Objects::nonNull).distinct().toList()) {
            map.put(id, batchNo(id));
        }
        return map;
    }

    private int remaining(Long batchId, int sold) {
        try {
            BatchInfo batch = batchQueryService.requireBatch(batchId);
            return Math.max(0, (batch.currentCount() == null ? 0 : batch.currentCount()) - sold);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private SaleOrderVos.CreatedVO createdVO(SaleOrder order, int remainingCount) {
        SaleOrderVos.CreatedVO vo = new SaleOrderVos.CreatedVO();
        vo.setId(order.getId());
        vo.setBatchNo(batchNo(order.getBatchId()));
        vo.setRemainingCount(remainingCount);
        vo.setCountUnitText(support.countUnitText(order.getCategoryId()));
        vo.setBatchEnded(remainingCount == 0);
        return vo;
    }

    private UserBriefVO brief(Long userId) {
        AccountBrief brief = accountQueryService.getBrief(userId);
        return UserBriefVO.builder().userId(userId).name(brief == null ? null : brief.name()).build();
    }

    private Map<Long, AccountBrief> accountNames(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : accountQueryService.listBriefs(ids);
    }

    private static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value, BeijingTime.DATE);
        } catch (Exception e) {
            throw FinanceErrors.field(field, "请选择日期");
        }
    }

    private static String cnDate(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private static String money(BigDecimal value) {
        return FinanceSupport.scale2(value).stripTrailingZeros().toPlainString();
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }

    private static String normalizeKey(String key) {
        return key == null || key.isBlank() ? null : key.trim();
    }
}

