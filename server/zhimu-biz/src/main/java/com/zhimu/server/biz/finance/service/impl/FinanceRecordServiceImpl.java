package com.zhimu.server.biz.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.finance.entity.BatchSettlement;
import com.zhimu.server.biz.finance.entity.BatchSettlementSupplement;
import com.zhimu.server.biz.finance.entity.ExpenseSubcategory;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.entity.SaleReceipt;
import com.zhimu.server.biz.finance.mapper.BatchSettlementMapper;
import com.zhimu.server.biz.finance.mapper.BatchSettlementSupplementMapper;
import com.zhimu.server.biz.finance.mapper.ExpenseSubcategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.mapper.SaleReceiptMapper;
import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.FinanceRecordService;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.FinanceRecordVos;
import com.zhimu.server.biz.finance.vo.OverviewVos;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** API-E01–E07、E43：收支汇总、流水、手工记账与作废。 */
@Service
@RequiredArgsConstructor
public class FinanceRecordServiceImpl implements FinanceRecordService {

    private static final String TYPE_AUTO = "AUTO";
    private static final String TYPE_MANUAL = "MANUAL";

    private final FinanceRecordMapper recordMapper;
    private final FinanceCategoryMapper categoryMapper;
    private final ExpenseSubcategoryMapper subcategoryMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleReceiptMapper saleReceiptMapper;
    private final BatchSettlementMapper settlementMapper;
    private final BatchSettlementSupplementMapper supplementMapper;
    private final FinanceSupport support;
    private final MarketPriceService marketPriceService;
    private final FarmQueryService farmQueryService;
    private final BizMediaService bizMediaService;
    private final BatchQueryService batchQueryService;
    private final FileMapper fileMapper;
    private final OperationLogRecorder operationLogRecorder;

    // ===== E01 经营概览 =====

    @Override
    public OverviewVos.BusinessOverviewVO overview() {
        Long farmId = support.farmId();
        LocalDate today = BeijingTime.today();
        LocalDate monthStart = today.withDayOfMonth(1);

        OverviewVos.BusinessOverviewVO vo = new OverviewVos.BusinessOverviewVO();
        vo.setToday(FinanceSupport.format(today));
        vo.setPendingSupplementCount(supplementMapper.selectCount(
                Wrappers.<BatchSettlementSupplement>lambdaQuery()
                        .eq(BatchSettlementSupplement::getFarmId, farmId)
                        .eq(BatchSettlementSupplement::getStatus, FinanceConst.SUPPLEMENT_PENDING)).intValue());

        OverviewVos.FinanceSummaryVO summary = monthlySummary(FinanceSupport.formatMonth(YearMonth.from(today)), null);
        OverviewVos.MonthBalance balance = new OverviewVos.MonthBalance();
        balance.setIncome(summary.getIncome());
        balance.setExpenseAmount(summary.getExpenseAmount());
        balance.setBalanceAmount(summary.getBalanceAmount());
        balance.setPrevBalanceAmount(summary.getPrevBalanceAmount());
        balance.setBalanceMomRate(summary.getBalanceMomRate());
        balance.setComparable(summary.getBalanceComparable());
        vo.setMonthBalance(balance);

        List<SaleOrder> open = TenantSkip.call(() -> saleOrderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getFarmId, farmId)
                .in(SaleOrder::getPaymentStatus, FinanceConst.PAY_UNPAID, FinanceConst.PAY_PARTIAL)
                .isNotNull(SaleOrder::getEffectiveDueDate)
                .orderByAsc(SaleOrder::getEffectiveDueDate)));
        OverviewVos.ReceivableSummary receivable = receivableSummary(open, today);
        vo.setReceivable(receivable);
        vo.setPriceCard(marketPriceService.priceCard(farmId));

        List<SaleOrder> monthOrders = TenantSkip.call(() -> saleOrderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getFarmId, farmId)
                .between(SaleOrder::getSaleDate, monthStart, today)));
        OverviewVos.MonthSales sales = new OverviewVos.MonthSales();
        sales.setOrderCount(monthOrders.size());
        sales.setSaleAmount(FinanceSupport.scale2(monthOrders.stream()
                .filter(o -> !FinanceConst.PAY_VOIDED.equals(o.getPaymentStatus()))
                .map(SaleOrder::getSaleAmount).map(FinanceSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add)));
        vo.setMonthSales(sales);

        OverviewVos.MonthRecords records = new OverviewVos.MonthRecords();
        records.setTotal(summary.getRecordCount());
        records.setAutoCount(summary.getAutoCount());
        records.setManualCount(summary.getManualCount());
        vo.setMonthRecords(records);

        OverviewVos.Todos todos = new OverviewVos.Todos();
        List<OverviewVos.TodoItem> items = new ArrayList<>();
        for (SaleOrder order : open) {
            if (items.size() >= 3) {
                break;
            }
            OverviewVos.TodoItem item = new OverviewVos.TodoItem();
            item.setTodoId(null);
            item.setTodoType("RECEIVABLE");
            boolean overdue = today.isAfter(order.getEffectiveDueDate());
            String batchNo = batchNo(order.getBatchId());
            item.setTitle((batchNo == null ? "出栏单" : batchNo) + (overdue ? " 收款已逾期" : " 待收款"));
            item.setSubtitle("未收 " + money(order.getUnpaidAmount()) + " 元 · 约定 "
                    + (order.getEffectiveDueDate().getMonthValue()) + "月" + order.getEffectiveDueDate().getDayOfMonth()
                    + "日");
            item.setDueDate(FinanceSupport.format(order.getEffectiveDueDate()));
            item.setOverdue(overdue);
            item.setTagText(overdue ? "已逾期" : dueSoonText(order.getEffectiveDueDate(), today));
            item.setBizType(FinanceConst.KIND_SALE_ORDER);
            item.setBizId(order.getId());
            items.add(item);
        }
        todos.setTotal(open.size());
        todos.setItems(items);
        vo.setTodos(todos);
        return vo;
    }

    // ===== E02 月度汇总 =====

    @Override
    public OverviewVos.FinanceSummaryVO monthlySummary(String month, String scope) {
        YearMonth target;
        try {
            target = month == null ? null : YearMonth.parse(month, BeijingTime.MONTH);
        } catch (Exception e) {
            throw FinanceErrors.field("month", "月份格式应为 yyyy-MM");
        }
        FinanceErrors.require(target != null, "month", "请选择月份");
        YearMonth current = YearMonth.from(BeijingTime.today());
        FinanceErrors.require(!target.isAfter(current), "month", "月份不能晚于本月");

        LocalDate periodStart = target.atDay(1);
        LocalDate periodEnd = target.equals(current) ? BeijingTime.today() : target.atEndOfMonth();
        LocalDate prevStart = target.minusMonths(1).atDay(1);
        LocalDate prevEnd = target.equals(current)
                ? target.minusMonths(1).atDay(Math.min(BeijingTime.today().getDayOfMonth(),
                target.minusMonths(1).lengthOfMonth()))
                : target.minusMonths(1).atEndOfMonth();

        ScopeFarms scopeFarms = scopeFarms(scope);
        List<FinanceRecord> records = records(scopeFarms.ids(), periodStart, periodEnd);
        List<FinanceRecord> prevRecords = records(scopeFarms.ids(), prevStart, prevEnd);

        Totals currentTotals = totals(records);
        Totals prevTotals = totals(prevRecords);

        OverviewVos.FinanceSummaryVO vo = new OverviewVos.FinanceSummaryVO();
        vo.setPeriodStart(FinanceSupport.format(periodStart));
        vo.setPeriodEnd(FinanceSupport.format(periodEnd));
        vo.setIsCurrentMonth(target.equals(current));
        vo.setIncome(currentTotals.income());
        vo.setExpenseAmount(currentTotals.expense());
        vo.setBalanceAmount(currentTotals.balance());
        vo.setPrevIncome(prevTotals.income());
        vo.setPrevExpenseAmount(prevTotals.expense());
        vo.setPrevBalanceAmount(prevTotals.balance());
        vo.setIncomeComparable(prevTotals.income().signum() != 0);
        vo.setExpenseComparable(prevTotals.expense().signum() != 0);
        vo.setBalanceComparable(prevTotals.balance().signum() != 0);
        vo.setIncomeMomRate(vo.getIncomeComparable() ? FinanceSupport.rate(currentTotals.income(), prevTotals.income()) : null);
        vo.setExpenseMomRate(vo.getExpenseComparable() ? FinanceSupport.rate(currentTotals.expense(), prevTotals.expense()) : null);
        vo.setBalanceMomRate(vo.getBalanceComparable() ? FinanceSupport.rate(currentTotals.balance(), prevTotals.balance()) : null);
        vo.setRecordCount(records.size());
        vo.setAutoCount((int) records.stream().filter(r -> !FinanceConst.SOURCE_MANUAL.equals(r.getSourceType())).count());
        vo.setManualCount((int) records.stream().filter(r -> FinanceConst.SOURCE_MANUAL.equals(r.getSourceType())).count());
        vo.setManageableFarmCount(scopeFarms.ids().size());

        if (scopeFarms.all()) {
            vo.setExpenseShares(null);
            vo.setIncomeShares(null);
            vo.setFarms(farmFinances(scopeFarms, records, prevRecords));
            vo.setStaffFarmNames(scopeFarms.staffFarmNames());
        } else {
            Map<Long, FinanceCategory> categories = categoryMap(records, prevRecords);
            vo.setExpenseShares(shares(FinanceConst.EXPENSE, records, prevRecords, categories));
            vo.setIncomeShares(shares(FinanceConst.INCOME, records, prevRecords, categories));
            vo.setFarms(new ArrayList<>());
            vo.setStaffFarmNames(new ArrayList<>());
        }
        return vo;
    }

    private List<OverviewVos.CategoryShareVO> shares(String direction, List<FinanceRecord> records,
                                                     List<FinanceRecord> prevRecords,
                                                     Map<Long, FinanceCategory> categories) {
        Map<Long, BigDecimal> current = sumByCategory(records, direction);
        Map<Long, BigDecimal> previous = sumByCategory(prevRecords, direction);
        BigDecimal total = current.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<OverviewVos.CategoryShareVO> result = new ArrayList<>();
        current.entrySet().stream().filter(e -> e.getValue().signum() > 0)
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> {
                    FinanceCategory category = categories.get(entry.getKey());
                    if (category == null) {
                        return;
                    }
                    OverviewVos.CategoryShareVO share = new OverviewVos.CategoryShareVO();
                    share.setCategoryId(category.getId());
                    share.setName(category.getName());
                    share.setIcon(category.getIcon());
                    share.setAmount(FinanceSupport.scale2(entry.getValue()));
                    share.setShareRate(total.signum() == 0 ? BigDecimal.ZERO
                            : entry.getValue().divide(total, 4, java.math.RoundingMode.HALF_UP));
                    BigDecimal prev = previous.get(entry.getKey());
                    share.setComparable(prev != null && prev.signum() != 0);
                    share.setMomRate(share.getComparable() ? FinanceSupport.rate(entry.getValue(), prev) : null);
                    share.setTrend(FinanceSupport.trendOf(share.getMomRate()));
                    result.add(share);
                });
        return result;
    }

    private List<OverviewVos.FarmFinanceVO> farmFinances(ScopeFarms scope, List<FinanceRecord> records,
                                                         List<FinanceRecord> prevRecords) {
        Map<Long, List<FinanceRecord>> byFarm = records.stream()
                .collect(Collectors.groupingBy(FinanceRecord::getFarmId));
        Map<Long, List<FinanceRecord>> prevByFarm = prevRecords.stream()
                .collect(Collectors.groupingBy(FinanceRecord::getFarmId));
        BigDecimal totalIncome = records.stream().filter(r -> FinanceConst.INCOME.equals(r.getRecordType()))
                .map(FinanceRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<OverviewVos.FarmFinanceVO> result = new ArrayList<>();
        for (FarmBriefVO farm : scope.farms()) {
            Totals cur = totals(byFarm.getOrDefault(farm.getFarmId(), List.of()));
            Totals prev = totals(prevByFarm.getOrDefault(farm.getFarmId(), List.of()));
            OverviewVos.FarmFinanceVO vo = new OverviewVos.FarmFinanceVO();
            vo.setFarmId(farm.getFarmId());
            vo.setFarmName(farm.getName());
            vo.setRole(farm.getRole());
            vo.setRoleText(roleText(farm.getRole()));
            vo.setIncome(cur.income());
            vo.setExpenseAmount(cur.expense());
            vo.setBalanceAmount(cur.balance());
            vo.setIncomeComparable(prev.income().signum() != 0);
            vo.setExpenseComparable(prev.expense().signum() != 0);
            vo.setBalanceComparable(prev.balance().signum() != 0);
            vo.setIncomeMomRate(vo.getIncomeComparable() ? FinanceSupport.rate(cur.income(), prev.income()) : null);
            vo.setExpenseMomRate(vo.getExpenseComparable() ? FinanceSupport.rate(cur.expense(), prev.expense()) : null);
            vo.setBalanceMomRate(vo.getBalanceComparable() ? FinanceSupport.rate(cur.balance(), prev.balance()) : null);
            vo.setIncomeShareRate(totalIncome.signum() == 0 ? null
                    : cur.income().divide(totalIncome, 4, java.math.RoundingMode.HALF_UP));
            result.add(vo);
        }
        return result;
    }

    // ===== E03/E04 流水与详情 =====

    @Override
    public FinanceRecordVos.RecordPageVO listRecords(FinanceQueries.FinanceRecordQuery query) {
        ScopeFarms scope = scopeFarms(query.getScope());
        List<FinanceRecord> all;
        if ((query.getMonth() == null || query.getMonth().isBlank()) && query.getBatchId() != null) {
            // E10 批次核算进入 E04：不传月份时按该批次全部收支查询
            all = recordsOfBatch(scope.ids(), query.getBatchId());
        } else {
            YearMonth target;
            try {
                target = YearMonth.parse(query.getMonth(), BeijingTime.MONTH);
            } catch (Exception e) {
                throw FinanceErrors.field("month", "月份格式应为 yyyy-MM");
            }
            FinanceErrors.require(!target.isAfter(YearMonth.from(BeijingTime.today())), "month", "月份不能晚于本月");
            all = records(scope.ids(), target.atDay(1), target.atEndOfMonth());
        }

        String type = query.getType() == null ? FinanceConst.ALL : query.getType();
        Set<Long> categoryIds = parseIds(query.getCategoryIds());
        List<FinanceRecord> filtered = all.stream()
                .filter(r -> switch (type) {
                    case FinanceConst.EXPENSE, FinanceConst.INCOME -> type.equals(r.getRecordType());
                    case TYPE_AUTO -> !FinanceConst.SOURCE_MANUAL.equals(r.getSourceType());
                    case TYPE_MANUAL -> FinanceConst.SOURCE_MANUAL.equals(r.getSourceType());
                    default -> true;
                })
                .filter(r -> categoryIds.isEmpty() || categoryIds.contains(r.getCategoryId()))
                .filter(r -> query.getBatchId() == null || query.getBatchId().equals(r.getBatchId()))
                .filter(r -> query.getHasVoucher() == null
                        || query.getHasVoucher() == (r.getVoucherCount() != null && r.getVoucherCount() > 0))
                .sorted(Comparator.comparing(FinanceRecord::getRecordDate).reversed()
                        .thenComparing(Comparator.comparing(FinanceRecord::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())).reversed()))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<Long, FinanceCategory> categories = categoryMap(filtered, List.of());
        Map<Long, ExpenseSubcategory> subcategories = subcategoryMap(filtered);
        Map<Long, String> batchNos = batchNos(filtered);
        Map<Long, FarmBriefVO> farmMap = scope.farmMap();

        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        if (!keyword.isEmpty()) {
            BigDecimal numeric = numeric(keyword);
            filtered = filtered.stream().filter(r -> {
                if (numeric != null && numeric.compareTo(FinanceSupport.nz(r.getAmount())) == 0) {
                    return true;
                }
                FinanceCategory category = categories.get(r.getCategoryId());
                ExpenseSubcategory sub = subcategories.get(r.getSubcategoryId());
                return contains(category == null ? null : category.getName(), keyword)
                        || contains(sub == null ? null : sub.getName(), keyword)
                        || contains(r.getNote(), keyword)
                        || contains(r.getDetailText(), keyword)
                        || contains(r.getSourceNo(), keyword)
                        || contains(batchNos.get(r.getBatchId()), keyword);
            }).collect(Collectors.toCollection(ArrayList::new));
        }

        FinanceRecordVos.RecordPageVO vo = new FinanceRecordVos.RecordPageVO();
        Totals totals = totals(filtered);
        FinanceRecordVos.Totals totalsVO = new FinanceRecordVos.Totals();
        totalsVO.setIncome(totals.income());
        totalsVO.setExpenseAmount(totals.expense());
        vo.setTotals(totalsVO);

        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<FinanceRecordVos.RecordVO> list = new ArrayList<>();
        for (FinanceRecord record : filtered.subList(from, to)) {
            list.add(toVo(record, categories, subcategories, batchNos, farmMap, false));
        }
        vo.setList(list);
        vo.setTotal(filtered.size());
        vo.setPage(page);
        vo.setSize(size);
        vo.setHasMore((long) page * size < filtered.size());
        return vo;
    }

    @Override
    public FinanceRecordVos.DetailVO detail(Long id) {
        FinanceRecord record = requireRecord(id, support.farmId());
        Map<Long, FinanceCategory> categories = categoryMap(List.of(record), List.of());
        Map<Long, ExpenseSubcategory> subs = subcategoryMap(List.of(record));
        return detailVo(record, categories, subs);
    }

    // ===== E05–E07 记账、修改、作废 =====

    @Override
    @Transactional
    public FinanceRecordVos.RecordVO create(FinanceRequests.FinanceRecordRequest request, String idempotencyKey) {
        Long farmId = support.farmId();
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            FinanceRecord existing = recordMapper.selectOne(Wrappers.<FinanceRecord>lambdaQuery()
                    .eq(FinanceRecord::getFarmId, farmId)
                    .eq(FinanceRecord::getClientRequestId, key)
                    .last("LIMIT 1"));
            if (existing != null) {
                return toVo(existing, categoryMap(List.of(existing), List.of()),
                        subcategoryMap(List.of(existing)), batchNos(List.of(existing)), Map.of(), false);
            }
        }
        Validated validated = validate(request, null, farmId);
        FinanceRecord record = new FinanceRecord();
        record.setFarmId(farmId);
        record.setRecordType(validated.type());
        record.setSourceType(FinanceConst.SOURCE_MANUAL);
        record.setCategoryId(validated.category().getId());
        record.setSubcategoryId(validated.subcategory() == null ? null : validated.subcategory().getId());
        record.setAmount(FinanceSupport.scale2(request.getAmount()));
        record.setRecordDate(validated.date());
        record.setBatchId(validated.batchId());
        record.setNote(validated.type().equals(FinanceConst.INCOME) ? blankToNull(request.getNote()) : null);
        record.setVoucherCount(validated.fileIds() == null ? 0 : validated.fileIds().size());
        record.setStatus(FinanceConst.RECORD_NORMAL);
        record.setClientRequestId(key);
        record.setVersion(0);
        record.setUpdatedBy(UserContext.requireUserId());
        recordMapper.insert(record);

        if (validated.fileIds() != null && !validated.fileIds().isEmpty()) {
            bizMediaService.replace(BizType.FINANCE_RECORD, record.getId(), farmId, validated.fileIds());
        }
        onRecordInCost(record, validated.category());
        return toVo(record, categoryMap(List.of(record), List.of()), subcategoryMap(List.of(record)),
                batchNos(List.of(record)), Map.of(), false);
    }

    @Override
    @Transactional
    public FinanceRecordVos.RecordVO update(Long id, FinanceRequests.FinanceRecordRequest request) {
        Long farmId = support.farmId();
        FinanceRecord record = requireRecord(id, farmId);
        if (!FinanceConst.SOURCE_MANUAL.equals(record.getSourceType())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "自动生成，不能单独修改");
        }
        if (FinanceConst.RECORD_VOID.equals(record.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这条记录已作废，不能修改");
        }
        support.checkVersion(record.getVersion(), request.getVersion());
        Validated validated = validate(request, record, farmId);
        Map<String, Object> before = auditSnapshot(record);
        record.setCategoryId(validated.category().getId());
        record.setSubcategoryId(validated.subcategory() == null ? null : validated.subcategory().getId());
        record.setAmount(FinanceSupport.scale2(request.getAmount()));
        record.setRecordDate(validated.date());
        record.setBatchId(validated.batchId());
        record.setNote(validated.type().equals(FinanceConst.INCOME) ? blankToNull(request.getNote()) : null);
        record.setVoucherCount(validated.fileIds() == null ? record.getVoucherCount()
                : validated.fileIds().size());
        record.setUpdatedBy(UserContext.requireUserId());
        if (recordMapper.updateById(record) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        if (validated.fileIds() != null) {
            bizMediaService.replace(BizType.FINANCE_RECORD, record.getId(), farmId, validated.fileIds());
        }
        onRecordInCost(record, validated.category());
        auditChanges(record, "UPDATE", before, auditSnapshot(record));
        return toVo(record, categoryMap(List.of(record), List.of()), subcategoryMap(List.of(record)),
                batchNos(List.of(record)), Map.of(), false);
    }

    @Override
    @Transactional
    public FinanceRecordVos.RecordVO voidRecord(Long id, Integer version) {
        Long farmId = support.farmId();
        Long userId = UserContext.requireUserId();
        FinanceRecord record = requireRecord(id, farmId);
        if (!FinanceConst.SOURCE_MANUAL.equals(record.getSourceType())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "自动生成，不能单独修改");
        }
        if (FinanceConst.RECORD_VOID.equals(record.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这条记录已作废，不能修改");
        }
        support.checkVersion(record.getVersion(), version);
        Map<String, Object> before = Map.of("status", auditValue("状态", record.getStatus(),
                FinanceConst.statusText(record.getStatus())));
        record.setStatus(FinanceConst.RECORD_VOID);
        record.setVoidSource("MANUAL");
        record.setVoidedAt(BeijingTime.now());
        record.setVoidedBy(userId);
        record.setUpdatedBy(userId);
        if (recordMapper.updateById(record) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        cancelOrReverseSupplement(record);
        auditChanges(record, "VOID", before, Map.of("status", auditValue("状态", record.getStatus(),
                FinanceConst.statusText(record.getStatus()))));
        return toVo(record, categoryMap(List.of(record), List.of()), subcategoryMap(List.of(record)),
                batchNos(List.of(record)), Map.of(), false);
    }

    @Override
    public FinanceRecordVos.SubmissionVO submission(String clientRequestId) {
        Long farmId = support.farmId();
        FinanceRecordVos.SubmissionVO vo = new FinanceRecordVos.SubmissionVO();
        vo.setSaved(false);
        String key = normalizeKey(clientRequestId);
        if (key == null) {
            return vo;
        }
        FinanceRecord record = recordMapper.selectOne(Wrappers.<FinanceRecord>lambdaQuery()
                .eq(FinanceRecord::getFarmId, farmId)
                .eq(FinanceRecord::getClientRequestId, key)
                .eq(FinanceRecord::getSourceType, FinanceConst.SOURCE_MANUAL)
                .last("LIMIT 1"));
        if (record != null) {
            vo.setSaved(true);
            vo.setBizType(FinanceConst.KIND_FINANCE_RECORD);
            vo.setBizId(record.getId());
            return vo;
        }
        SaleOrder order = saleOrderMapper.selectOne(Wrappers.<SaleOrder>lambdaQuery()
                .eq(SaleOrder::getFarmId, farmId).eq(SaleOrder::getClientRequestId, key).last("LIMIT 1"));
        if (order != null) {
            vo.setSaved(true);
            vo.setBizType(FinanceConst.KIND_SALE_ORDER);
            vo.setBizId(order.getId());
            vo.setSaleOrderId(order.getId());
            return vo;
        }
        SaleReceipt receipt = saleReceiptMapper.selectOne(Wrappers.<SaleReceipt>lambdaQuery()
                .eq(SaleReceipt::getFarmId, farmId).eq(SaleReceipt::getClientRequestId, key).last("LIMIT 1"));
        if (receipt != null) {
            vo.setSaved(true);
            vo.setBizType(FinanceConst.KIND_SALE_RECEIPT);
            vo.setBizId(receipt.getId());
            vo.setSaleOrderId(receipt.getSaleOrderId());
        }
        return vo;
    }

    // ===== 内部：校验与转换 =====

    private Map<String, Object> auditSnapshot(FinanceRecord record) {
        Map<String, Object> values = new LinkedHashMap<>();
        FinanceCategory category = categoryMapper.selectById(record.getCategoryId());
        ExpenseSubcategory subcategory = record.getSubcategoryId() == null ? null
                : subcategoryMapper.selectById(record.getSubcategoryId());
        values.put("category_id", auditValue("分类", record.getCategoryId(), category == null ? null : category.getName()));
        values.put("subcategory_id", auditValue("支出子类", record.getSubcategoryId(), subcategory == null ? null : subcategory.getName()));
        values.put("amount", auditValue("金额", FinanceSupport.scale2(record.getAmount()).toPlainString(),
                FinanceSupport.moneyTextFixed(record.getAmount()) + " 元"));
        values.put("record_date", auditValue("日期", record.getRecordDate(), FinanceSupport.format(record.getRecordDate())));
        values.put("batch_id", auditValue("关联批次", record.getBatchId(), batchNo(record.getBatchId())));
        values.put("note", auditValue("说明", record.getNote(), record.getNote()));
        List<Long> files = bizMediaService.listFileIds(BizType.FINANCE_RECORD, record.getId());
        values.put("file_ids", auditValue("凭证", files, (files == null ? 0 : files.size()) + " 张"));
        return values;
    }

    private static Map<String, Object> auditValue(String name, Object value, String display) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("value", value);
        field.put("display", display == null || display.isBlank() ? "未填写" : display);
        return field;
    }

    private void auditChanges(FinanceRecord record, String action, Map<String, Object> before,
                              Map<String, Object> after) {
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        for (String field : after.keySet()) {
            if (!Objects.equals(before.get(field), after.get(field))) {
                changedBefore.put(field, before.get(field));
                Object value = after.get(field);
                if ("file_ids".equals(field) && value instanceof Map<?, ?> files) {
                    value = auditValue("凭证", files.get("value"), "已更新（" + files.get("display") + "）");
                }
                changedAfter.put(field, value);
            }
        }
        if (changedAfter.isEmpty()) {
            return;
        }
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER").operatorId(UserContext.requireUserId())
                .opType("RECORD_EDIT").opAction(action).bizType(BizType.FINANCE_RECORD.name())
                .bizId(record.getId()).farmId(record.getFarmId()).targetName("手工收支记录")
                .before(changedBefore).after(changedAfter).build());
    }

    private record Validated(String type, FinanceCategory category, ExpenseSubcategory subcategory, LocalDate date,
                             Long batchId, List<Long> fileIds) {
    }

    private Validated validate(FinanceRequests.FinanceRecordRequest request, FinanceRecord existing, Long farmId) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String type = request.getRecordType() != null ? request.getRecordType()
                : existing == null ? null : existing.getRecordType();
        FinanceErrors.require(FinanceConst.EXPENSE.equals(type) || FinanceConst.INCOME.equals(type),
                "record_type", "请选择收支类型");
        FinanceErrors.require(request.getAmount() != null, "amount", "请输入金额");
        FinanceErrors.require(request.getAmount().signum() > 0, "amount", "金额必须大于 0");
        FinanceErrors.require(request.getAmount().stripTrailingZeros().scale() <= 2, "amount", "金额最多保留 2 位小数");
        FinanceErrors.require(request.getAmount().compareTo(new BigDecimal("99999999.99")) <= 0, "amount",
                "金额不能超过 99,999,999.99");
        FinanceErrors.require(request.getCategoryId() != null, "category_id", "请选择分类");
        FinanceCategory category = categoryMapper.selectById(request.getCategoryId());
        if (category == null || !type.equals(category.getDirection())) {
            throw FinanceErrors.field("category_id", "请选择分类");
        }
        if (!"ENABLED".equals(category.getStatus())) {
            throw FinanceErrors.field("category_id", "该分类已停用，请重新选择");
        }
        if (FinanceConst.SOURCE_DRUG_RETURN.equals(category.getAutoSource())) {
            throw FinanceErrors.field("category_id", "药苗退货退款由原退货单登记，不能重复手工记账");
        }
        ExpenseSubcategory subcategory = null;
        if (request.getSubcategoryId() != null) {
            if (!FinanceConst.EXPENSE.equals(type)) {
                throw FinanceErrors.field("subcategory_id", "收入不能选择支出子类");
            }
            subcategory = subcategoryMapper.selectById(request.getSubcategoryId());
            if (subcategory == null || !farmId.equals(subcategory.getFarmId())
                    || !category.getId().equals(subcategory.getCategoryId())) {
                throw FinanceErrors.field("subcategory_id", "请选择支出子类");
            }
        }
        String note = blankToNull(request.getNote());
        if (note != null) {
            FinanceErrors.require(FinanceConst.INCOME.equals(type), "note", "只有收入可以填写说明");
            FinanceErrors.require(note.codePointCount(0, note.length()) <= 30, "note", "说明不能超过 30 字");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(request.getRecordDate(), BeijingTime.DATE);
        } catch (Exception e) {
            throw FinanceErrors.field("record_date", "请选择日期");
        }
        FinanceErrors.require(!date.isAfter(BeijingTime.today()), "record_date", "日期不能晚于今天");
        Long batchId = request.getBatchId();
        if (batchId != null) {
            support.requireBatch(batchId, farmId);
        }
        List<Long> files = request.getFileIds();
        if (files != null) {
            FinanceErrors.require(files.size() <= 6, "file_ids", "最多上传 6 张凭证");
            if (!files.isEmpty()) {
                BizRef allow = existing == null ? null : new BizRef(BizType.FINANCE_RECORD, existing.getId());
                bizMediaService.validateForBind(files, FileScene.VOUCHER, UserContext.requireUserId(), "file_ids",
                        allow);
            }
        }
        return new Validated(type, category, subcategory, date, batchId, files);
    }

    private FinanceRecord requireRecord(Long id, Long farmId) {
        FinanceRecord record = id == null ? null : recordMapper.selectById(id);
        if (record == null || !Objects.equals(farmId, record.getFarmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return record;
    }

    private FinanceRecordVos.RecordVO toVo(FinanceRecord record, Map<Long, FinanceCategory> categories,
                                          Map<Long, ExpenseSubcategory> subs, Map<Long, String> batchNos,
                                          Map<Long, FarmBriefVO> farms, boolean detail) {
        FinanceRecordVos.RecordVO vo = detail ? new FinanceRecordVos.DetailVO() : new FinanceRecordVos.RecordVO();
        FinanceCategory category = categories.get(record.getCategoryId());
        ExpenseSubcategory sub = subs.get(record.getSubcategoryId());
        vo.setId(record.getId());
        vo.setFarmId(record.getFarmId());
        vo.setCategoryId(record.getCategoryId());
        vo.setAmount(record.getAmount());
        vo.setRecordDate(FinanceSupport.format(record.getRecordDate()));
        vo.setVoucherCount(record.getVoucherCount() == null ? 0 : record.getVoucherCount());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setCategoryIcon(category == null ? null : category.getIcon());
        vo.setSourceType(FinanceConst.sourceTypeApp(record.getSourceType()));
        vo.setSourceLabel(FinanceConst.sourceLabel(record.getSourceType()));
        vo.setRecordTypeText(FinanceConst.recordTypeText(record.getRecordType()));
        vo.setStatusText(FinanceConst.statusText(record.getStatus()));
        vo.setEditable(FinanceConst.SOURCE_MANUAL.equals(record.getSourceType())
                && FinanceConst.RECORD_NORMAL.equals(record.getStatus()));
        vo.setCreatedAt(FinanceSupport.format(record.getCreatedAt()));
        vo.setRecordType(record.getRecordType());
        vo.setStatus(record.getStatus());
        vo.setSourceNo(record.getSourceNo());
        vo.setSubcategoryId(record.getSubcategoryId());
        vo.setSubcategoryName(sub == null ? null : sub.getName());
        vo.setDetailText(record.getDetailText());
        vo.setNote(record.getNote());
        vo.setBatchId(record.getBatchId());
        vo.setBatchNo(batchNos.get(record.getBatchId()));
        vo.setTitle(title(record, category, sub));
        if (farms.containsKey(record.getFarmId())) {
            vo.setFarmName(farms.get(record.getFarmId()).getName());
        }
        if (!FinanceConst.SOURCE_MANUAL.equals(record.getSourceType())) {
            FinanceRecordVos.SourceTarget target = new FinanceRecordVos.SourceTarget();
            target.setBizType(FinanceConst.sourceTargetBizType(record.getSourceType()));
            target.setBizId(FinanceConst.SOURCE_SALE.equals(record.getSourceType()) ? record.getSaleOrderId()
                    : record.getSourceId());
            vo.setSourceTarget(target);
        }
        if (detail) {
            FinanceRecordVos.DetailVO d = (FinanceRecordVos.DetailVO) vo;
            d.setVersion(record.getVersion());
            d.setVouchers(bizMediaService.list(BizType.FINANCE_RECORD, record.getId()));
            if (record.getCreatedBy() != null) {
                d.setCreatedBy(UserBriefVO.builder().userId(record.getCreatedBy()).build());
            }
        }
        return vo;
    }

    private FinanceRecordVos.DetailVO detailVo(FinanceRecord record, Map<Long, FinanceCategory> categories,
                                               Map<Long, ExpenseSubcategory> subs) {
        return (FinanceRecordVos.DetailVO) toVo(record, categories, subs, batchNos(List.of(record)), Map.of(), true);
    }

    private static String title(FinanceRecord record, FinanceCategory category, ExpenseSubcategory sub) {
        String categoryName = category == null ? "收支" : category.getName();
        if (!FinanceConst.SOURCE_MANUAL.equals(record.getSourceType())) {
            return categoryName;
        }
        if (FinanceConst.EXPENSE.equals(record.getRecordType()) && sub != null) {
            return categoryName + " · " + sub.getName();
        }
        if (FinanceConst.INCOME.equals(record.getRecordType()) && record.getNote() != null) {
            return categoryName + " · " + record.getNote();
        }
        return categoryName;
    }

    // ===== 内部：补录 =====

    private void onRecordInCost(FinanceRecord record, FinanceCategory category) {
        if (!FinanceConst.EXPENSE.equals(record.getRecordType()) || Boolean.TRUE.equals(category.getMarkOnly())) {
            return;
        }
        BatchSettlementSupplement pending = supplementMapper.selectOne(
                Wrappers.<BatchSettlementSupplement>lambdaQuery()
                        .eq(BatchSettlementSupplement::getSourceType, "FINANCE_RECORD")
                        .eq(BatchSettlementSupplement::getSourceId, record.getId())
                        .eq(BatchSettlementSupplement::getStatus, FinanceConst.SUPPLEMENT_PENDING)
                        .last("LIMIT 1"));
        if (pending != null) {
            pending.setAmount(record.getAmount());
            pending.setBizDate(record.getRecordDate());
            pending.setTitle(subcategoryTitle(record, category));
            pending.setCostGroup(costGroup(category));
            supplementMapper.updateById(pending);
            return;
        }
        BatchSettlement settlement = record.getBatchId() == null ? null : settlementMapper.selectOne(
                Wrappers.<BatchSettlement>lambdaQuery()
                        .eq(BatchSettlement::getBatchId, record.getBatchId())
                        .eq(BatchSettlement::getStatus, FinanceConst.SETTLE_LOCKED)
                        .last("LIMIT 1"));
        if (settlement == null) {
            return;
        }
        BatchSettlementSupplement supplement = new BatchSettlementSupplement();
        supplement.setFarmId(record.getFarmId());
        supplement.setBatchId(record.getBatchId());
        supplement.setSettlementId(settlement.getId());
        supplement.setSourceType("FINANCE_RECORD");
        supplement.setSourceId(record.getId());
        supplement.setChangeAction("ADD");
        supplement.setBizDate(record.getRecordDate());
        supplement.setTitle(subcategoryTitle(record, category));
        supplement.setCostGroup(costGroup(category));
        supplement.setAmount(record.getAmount());
        supplement.setStatus(FinanceConst.SUPPLEMENT_PENDING);
        supplementMapper.insert(supplement);
    }

    private void cancelOrReverseSupplement(FinanceRecord record) {
        BatchSettlementSupplement pending = supplementMapper.selectOne(
                Wrappers.<BatchSettlementSupplement>lambdaQuery()
                        .eq(BatchSettlementSupplement::getSourceType, "FINANCE_RECORD")
                        .eq(BatchSettlementSupplement::getSourceId, record.getId())
                        .eq(BatchSettlementSupplement::getStatus, FinanceConst.SUPPLEMENT_PENDING)
                        .last("LIMIT 1"));
        if (pending != null) {
            pending.setStatus(FinanceConst.SUPPLEMENT_CANCELED);
            pending.setCancelReason("来源已作废");
            supplementMapper.updateById(pending);
            return;
        }
        FinanceCategory category = categoryMapper.selectById(record.getCategoryId());
        BatchSettlement settlement = record.getBatchId() == null ? null : settlementMapper.selectOne(
                Wrappers.<BatchSettlement>lambdaQuery()
                        .eq(BatchSettlement::getBatchId, record.getBatchId())
                        .eq(BatchSettlement::getStatus, FinanceConst.SETTLE_LOCKED)
                        .last("LIMIT 1"));
        if (settlement == null || category == null || Boolean.TRUE.equals(category.getMarkOnly())) {
            return;
        }
        BatchSettlementSupplement supplement = new BatchSettlementSupplement();
        supplement.setFarmId(record.getFarmId());
        supplement.setBatchId(record.getBatchId());
        supplement.setSettlementId(settlement.getId());
        supplement.setSourceType("FINANCE_RECORD");
        supplement.setSourceId(record.getId());
        supplement.setChangeAction("VOID");
        supplement.setBizDate(record.getRecordDate());
        supplement.setTitle(subcategoryTitle(record, category));
        supplement.setCostGroup(costGroup(category));
        supplement.setAmount(FinanceSupport.nz(record.getAmount()).negate());
        supplement.setStatus(FinanceConst.SUPPLEMENT_PENDING);
        supplementMapper.insert(supplement);
    }

    private String subcategoryTitle(FinanceRecord record, FinanceCategory category) {
        if (record.getSubcategoryId() != null) {
            ExpenseSubcategory sub = subcategoryMapper.selectById(record.getSubcategoryId());
            if (sub != null) {
                return sub.getName();
            }
        }
        return category == null ? "其他费用" : category.getName();
    }

    private static String costGroup(FinanceCategory category) {
        if (category == null) {
            return FinanceConst.COST_OTHER;
        }
        return switch (category.getCode() == null ? "" : category.getCode()) {
            case "FEED" -> FinanceConst.COST_FEED;
            case "DRUG" -> FinanceConst.COST_DRUG;
            case "SEEDLING" -> FinanceConst.COST_INTRO;
            default -> FinanceConst.COST_OTHER;
        };
    }

    // ===== 内部：查询与聚合 =====

    private record ScopeFarms(List<Long> ids, List<FarmBriefVO> farms, boolean all, List<String> staffFarmNames,
                              Map<Long, FarmBriefVO> farmMap) {
    }

    private record Totals(BigDecimal income, BigDecimal expense) {
        BigDecimal balance() {
            return income.subtract(expense);
        }
    }

    private ScopeFarms scopeFarms(String scope) {
        Long current = support.farmId();
        if (!FinanceConst.SCOPE_ALL.equals(scope)) {
            return new ScopeFarms(List.of(current), List.of(), false, List.of(), Map.of());
        }
        List<FarmBriefVO> farms = farmQueryService.listFarmsOfUser(UserContext.requireUserId());
        List<FarmBriefVO> included = farms.stream()
                .filter(f -> "NORMAL".equals(f.getStatus()) || "DISABLED".equals(f.getStatus()))
                .filter(f -> !"STAFF".equals(f.getRole()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (included.stream().noneMatch(f -> current.equals(f.getFarmId()))) {
            included.add(FarmBriefVO.builder().farmId(current).role("OWNER").status("NORMAL").build());
        }
        List<String> staff = farms.stream().filter(f -> "STAFF".equals(f.getRole()))
                .map(FarmBriefVO::getName).filter(Objects::nonNull).toList();
        Map<Long, FarmBriefVO> map = included.stream()
                .collect(Collectors.toMap(FarmBriefVO::getFarmId, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        return new ScopeFarms(included.stream().map(FarmBriefVO::getFarmId).toList(), included, true, staff, map);
    }

    private List<FinanceRecord> records(Collection<Long> farmIds, LocalDate start, LocalDate end) {
        if (farmIds.isEmpty()) {
            return List.of();
        }
        return TenantSkip.call(() -> recordMapper.selectList(Wrappers.<FinanceRecord>lambdaQuery()
                .in(FinanceRecord::getFarmId, farmIds)
                .between(FinanceRecord::getRecordDate, start, end)
                .eq(FinanceRecord::getStatus, FinanceConst.RECORD_NORMAL)));
    }

    private List<FinanceRecord> recordsOfBatch(Collection<Long> farmIds, Long batchId) {
        if (farmIds.isEmpty() || batchId == null) {
            return List.of();
        }
        return TenantSkip.call(() -> recordMapper.selectList(Wrappers.<FinanceRecord>lambdaQuery()
                .in(FinanceRecord::getFarmId, farmIds)
                .eq(FinanceRecord::getBatchId, batchId)
                .eq(FinanceRecord::getStatus, FinanceConst.RECORD_NORMAL)));
    }

    private Totals totals(List<FinanceRecord> records) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (FinanceRecord record : records) {
            if (FinanceConst.INCOME.equals(record.getRecordType())) {
                income = income.add(FinanceSupport.nz(record.getAmount()));
            } else {
                expense = expense.add(FinanceSupport.nz(record.getAmount()));
            }
        }
        return new Totals(FinanceSupport.scale2(income), FinanceSupport.scale2(expense));
    }

    private Map<Long, BigDecimal> sumByCategory(List<FinanceRecord> records, String direction) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (FinanceRecord record : records) {
            if (direction.equals(record.getRecordType())) {
                result.merge(record.getCategoryId(), FinanceSupport.nz(record.getAmount()), BigDecimal::add);
            }
        }
        return result;
    }

    private Map<Long, FinanceCategory> categoryMap(List<FinanceRecord> a, List<FinanceRecord> b) {
        Set<Long> ids = new HashSet<>();
        a.forEach(r -> ids.add(r.getCategoryId()));
        b.forEach(r -> ids.add(r.getCategoryId()));
        Map<Long, FinanceCategory> map = new HashMap<>();
        if (!ids.isEmpty()) {
            categoryMapper.selectBatchIds(ids).forEach(c -> map.put(c.getId(), c));
        }
        return map;
    }

    private Map<Long, ExpenseSubcategory> subcategoryMap(List<FinanceRecord> records) {
        Set<Long> ids = records.stream().map(FinanceRecord::getSubcategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ExpenseSubcategory> map = new HashMap<>();
        if (!ids.isEmpty()) {
            subcategoryMapper.selectBatchIds(ids).forEach(s -> map.put(s.getId(), s));
        }
        return map;
    }

    private Map<Long, String> batchNos(List<FinanceRecord> records) {
        Set<Long> ids = records.stream().map(FinanceRecord::getBatchId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> map = new HashMap<>();
        for (Long id : ids) {
            map.put(id, batchNo(id));
        }
        return map;
    }

    private String batchNo(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            BatchInfo info = batchQueryService.requireBatch(batchId);
            return info == null ? null : info.batchNo();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private OverviewVos.ReceivableSummary receivableSummary(List<SaleOrder> open, LocalDate today) {
        OverviewVos.ReceivableSummary summary = new OverviewVos.ReceivableSummary();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal overdueTotal = BigDecimal.ZERO;
        int overdue = 0;
        LocalDate nearest = null;
        for (SaleOrder order : open) {
            BigDecimal unpaid = FinanceSupport.nz(order.getUnpaidAmount());
            total = total.add(unpaid);
            if (order.getEffectiveDueDate() != null && today.isAfter(order.getEffectiveDueDate())) {
                overdue++;
                overdueTotal = overdueTotal.add(unpaid);
            }
            if (order.getEffectiveDueDate() != null && !today.isAfter(order.getEffectiveDueDate())
                    && (nearest == null || order.getEffectiveDueDate().isBefore(nearest))) {
                nearest = order.getEffectiveDueDate();
            }
        }
        summary.setTotalUnpaidAmount(FinanceSupport.scale2(total));
        summary.setOrderCount(open.size());
        summary.setOverdueCount(overdue);
        summary.setOverdueUnpaidAmount(FinanceSupport.scale2(overdueTotal));
        summary.setNearestDueDate(FinanceSupport.format(nearest));
        return summary;
    }

    private static String dueSoonText(LocalDate due, LocalDate today) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, due);
        if (days == 0) {
            return "今天";
        }
        if (days == 1) {
            return "明天";
        }
        return days > 0 ? days + " 天后" : "已逾期";
    }

    private static Set<Long> parseIds(String value) {
        Set<Long> result = new HashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split(",")) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法分类 ID
            }
        }
        return result;
    }

    private static BigDecimal numeric(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }

    private static String normalizeKey(String key) {
        return key == null || key.isBlank() ? null : key.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 经营概览待收款副标题“未收 88,160 元”：千分位 */
    private static String money(BigDecimal value) {
        return FinanceSupport.moneyText(value);
    }

    private static String roleText(String role) {
        return switch (role == null ? "" : role) {
            case "OWNER" -> "所有者";
            case "MANAGER" -> "场长";
            case "STAFF" -> "员工";
            default -> role;
        };
    }
}
