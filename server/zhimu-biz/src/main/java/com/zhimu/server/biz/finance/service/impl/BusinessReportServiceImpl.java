package com.zhimu.server.biz.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.finance.entity.BatchSettlement;
import com.zhimu.server.biz.finance.entity.BizReport;
import com.zhimu.server.biz.finance.entity.BizReportExport;
import com.zhimu.server.biz.finance.entity.BizReportSummary;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.BatchSettlementMapper;
import com.zhimu.server.biz.finance.mapper.BizReportExportMapper;
import com.zhimu.server.biz.finance.mapper.BizReportMapper;
import com.zhimu.server.biz.finance.mapper.BizReportSummaryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.BusinessReportService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.ReportVos;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiChatResult;
import com.zhimu.server.integration.ai.AiGateway;
import com.zhimu.server.integration.ai.AiMessage;
import com.zhimu.server.integration.ai.AiScene;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** API-E37–E42：期次、指标、趋势、AI 摘要与导出（XLSX）。 */
@Service
@RequiredArgsConstructor
public class BusinessReportServiceImpl implements BusinessReportService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int DAILY_AI_LIMIT = 5;

    private final BizReportMapper reportMapper;
    private final BizReportSummaryMapper summaryMapper;
    private final BizReportExportMapper exportMapper;
    private final FinanceRecordMapper recordMapper;
    private final SaleOrderMapper orderMapper;
    private final BatchSettlementMapper settlementMapper;
    private final FinanceCategoryMapper categoryMapper;
    private final BatchQueryService batchQueryService;
    private final FarmQueryService farmQueryService;
    private final AiGateway aiGateway;
    private final ObjectStorage objectStorage;
    private final FileMapper fileMapper;
    private final FinanceSupport support;

    // ===== E37 期次 =====

    @Override
    public ReportVos.ReportPeriodVO periods(String reportType, String scope) {
        String type = reportType == null ? FinanceConst.REPORT_WEEKLY : reportType;
        FinanceErrors.require(FinanceConst.REPORT_WEEKLY.equals(type) || FinanceConst.REPORT_MONTHLY.equals(type),
                "report_type", "请选择报表类型");
        Scope scopeFarms = scope(scope);
        List<BizReport> reports = TenantSkip.call(() -> reportMapper.selectList(Wrappers.<BizReport>lambdaQuery()
                .in(BizReport::getFarmId, scopeFarms.ids())
                .eq(BizReport::getReportType, type)
                .orderByDesc(BizReport::getPeriodStart)));
        ReportVos.ReportPeriodVO vo = new ReportVos.ReportPeriodVO();
        int limit = FinanceConst.REPORT_WEEKLY.equals(type) ? 52 : 24;
        for (BizReport report : reports.stream().limit(limit).toList()) {
            ReportVos.ReportPeriod period = new ReportVos.ReportPeriod();
            period.setPeriodStart(FinanceSupport.format(report.getPeriodStart()));
            period.setPeriodEnd(FinanceSupport.format(report.getPeriodEnd()));
            period.setLabel(FinanceConst.REPORT_WEEKLY.equals(type)
                    ? FinanceSupport.periodLabel(report.getPeriodStart(), report.getPeriodEnd())
                    : report.getPeriodStart().getMonthValue() + "月");
            period.setGeneratedAt(FinanceSupport.format(report.getGeneratedAt()));
            vo.getPeriods().add(period);
        }
        vo.setNextGenerationDate(nextGenerationDate(type));
        return vo;
    }

    // ===== E38 报表 =====

    @Override
    public ReportVos.BusinessReportVO view(FinanceQueries.ReportQuery query) {
        Period period = resolve(query);
        Scope scope = scope(query.getScope());
        String rangeType = query.getRangeType() == null ? FinanceConst.RANGE_FARM : query.getRangeType();
        FinanceErrors.require(List.of(FinanceConst.RANGE_FARM, FinanceConst.RANGE_CATEGORY,
                FinanceConst.RANGE_BATCH).contains(rangeType), "range_type", "请选择统计范围");
        if (!FinanceConst.RANGE_FARM.equals(rangeType)) {
            FinanceErrors.require(query.getRangeRefId() != null, "range_ref_id", "请选择品类或批次");
        }
        List<BatchInfo> batches = batches(scope.ids());
        List<SaleOrder> orders = orders(scope.ids());
        List<FinanceRecord> records = records(scope.ids(), period.start(), period.end());

        ReportVos.BusinessReportVO vo = new ReportVos.BusinessReportVO();
        ReportVos.ReportPeriodInfo info = new ReportVos.ReportPeriodInfo();
        info.setStart(FinanceSupport.format(period.start()));
        info.setEnd(FinanceSupport.format(period.end()));
        info.setLabel(period.toDate() ? FinanceSupport.periodLabel(period.start(), period.end())
                : FinanceConst.REPORT_WEEKLY.equals(period.reportType())
                ? FinanceSupport.periodLabel(period.start(), period.end())
                : period.start().getMonthValue() + "月");
        info.setToDate(period.toDate() ? FinanceSupport.format(period.end()) : null);
        vo.setPeriod(info);
        vo.setScope(scope.all() ? FinanceConst.SCOPE_ALL : null);
        vo.setIncludedFarms(scope.farms());
        vo.setStaffFarmNames(scope.staffFarmNames());
        vo.setDataCheck(dataCheck());

        ReportVos.ReportMetrics metrics = new ReportVos.ReportMetrics();
        metrics.setStock(stockMetrics(scope.ids(), batches, orders, period));
        metrics.setMortality(mortality());
        metrics.setFeed(feed(scope.ids()));
        metrics.setBalance(balance(records, period));
        vo.setMetrics(metrics);
        vo.setBatchProfits(batchProfits(scope.ids(), period));
        vo.setRunningBatches(runningBatches(scope.ids()));
        vo.setEmpty(batches.isEmpty() && records.isEmpty());
        return vo;
    }

    // ===== E39 趋势 =====

    @Override
    public ReportVos.ReportTrendVO trend(FinanceQueries.ReportQuery query) {
        String metric = query.getMetric() == null ? "STOCK" : query.getMetric();
        FinanceErrors.require(List.of("STOCK", "MORTALITY", "FEED", "BALANCE").contains(metric), "metric",
                "请选择指标");
        Period period = resolve(query);
        Scope scope = scope(query.getScope());
        ReportVos.ReportTrendVO vo = new ReportVos.ReportTrendVO();
        vo.setMetric(metric);
        List<SaleOrder> orders = orders(scope.ids());
        List<FinanceRecord> records = records(scope.ids(), period.start(), period.end());
        List<BatchInfo> batches = batches(scope.ids());
        switch (metric) {
            case "STOCK" -> {
                int end = batches.stream().mapToInt(b -> b.currentCount() == null ? 0 : b.currentCount()).sum();
                int sold = orders.stream().filter(o -> inPeriod(o.getSaleDate(), period))
                        .mapToInt(o -> o.getQuantity() == null ? 0 : o.getQuantity()).sum();
                int stock = end + sold;
                Map<LocalDate, Integer> soldByDate = orders.stream()
                        .filter(o -> inPeriod(o.getSaleDate(), period))
                        .collect(Collectors.groupingBy(SaleOrder::getSaleDate,
                                Collectors.summingInt(o -> o.getQuantity() == null ? 0 : o.getQuantity())));
                for (LocalDate date = period.start(); !date.isAfter(period.end()); date = date.plusDays(1)) {
                    ReportVos.TrendPoint point = new ReportVos.TrendPoint();
                    point.setDate(FinanceSupport.format(date));
                    point.setValue(BigDecimal.valueOf(stock));
                    vo.getPoints().add(point);
                    int out = soldByDate.getOrDefault(date, 0);
                    if (out > 0) {
                        ReportVos.StockEvent event = new ReportVos.StockEvent();
                        event.setDate(FinanceSupport.format(date));
                        event.setType("OUT");
                        event.setCount(out);
                        vo.getEvents().add(event);
                    }
                    stock -= out;
                }
                ReportVos.StockEquation equation = new ReportVos.StockEquation();
                equation.setBegin(end + sold);
                equation.setEntry(0);
                equation.setDeath(0);
                equation.setSold(sold);
                equation.setEnd(end);
                vo.setEquation(equation);
            }
            case "BALANCE" -> {
                BigDecimal running = BigDecimal.ZERO;
                for (LocalDate date = period.start(); !date.isAfter(period.end()); date = date.plusDays(1)) {
                    BigDecimal daily = BigDecimal.ZERO;
                    for (FinanceRecord record : records) {
                        if (date.equals(record.getRecordDate())) {
                            daily = daily.add(FinanceConst.INCOME.equals(record.getRecordType())
                                    ? FinanceSupport.nz(record.getAmount())
                                    : FinanceSupport.nz(record.getAmount()).negate());
                        }
                    }
                    running = running.add(daily);
                    ReportVos.TrendPoint point = new ReportVos.TrendPoint();
                    point.setDate(FinanceSupport.format(date));
                    point.setValue(FinanceSupport.scale2(running));
                    vo.getPoints().add(point);
                }
            }
            case "FEED" -> {
                BigDecimal total = settlements(scope.ids()).stream().map(BatchSettlement::getFeedKg)
                        .map(FinanceSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
                long days = ChronoUnit.DAYS.between(period.start(), period.end()) + 1;
                BigDecimal daily = days <= 0 ? BigDecimal.ZERO
                        : total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
                for (LocalDate date = period.start(); !date.isAfter(period.end()); date = date.plusDays(1)) {
                    ReportVos.TrendPoint point = new ReportVos.TrendPoint();
                    point.setDate(FinanceSupport.format(date));
                    point.setValue(daily);
                    vo.getPoints().add(point);
                }
            }
            default -> {
                for (LocalDate date = period.start(); !date.isAfter(period.end()); date = date.plusDays(1)) {
                    ReportVos.TrendPoint point = new ReportVos.TrendPoint();
                    point.setDate(FinanceSupport.format(date));
                    point.setValue(BigDecimal.ZERO.setScale(4));
                    vo.getPoints().add(point);
                }
            }
        }
        return vo;
    }

    // ===== E40/E41 AI 摘要 =====

    @Override
    public ReportVos.ReportSummaryVO summary(FinanceQueries.ReportQuery query) {
        Period period = resolve(query);
        Scope scope = scope(query.getScope());
        String rangeType = query.getRangeType() == null ? FinanceConst.RANGE_FARM : query.getRangeType();
        String viewKey = viewKey(scope, period, rangeType, query.getRangeRefId());
        ReportVos.BusinessReportVO view = view(query);
        String hash = hash(view);
        BizReportSummary summary = summaryMapper.selectOne(Wrappers.<BizReportSummary>lambdaQuery()
                .eq(BizReportSummary::getViewKey, viewKey)
                .last("LIMIT 1"));
        if (summary == null) {
            summary = createSummary(viewKey, scope, period, rangeType, query.getRangeRefId(), hash);
            generateQuietly(summary, view);
        } else if (!hash.equals(summary.getDataHash()) && FinanceConst.ADVICE_SUCCESS.equals(summary.getStatus())) {
            ReportVos.ReportSummaryVO vo = toSummary(summary);
            vo.setOutdated(true);
            return vo;
        }
        return toSummary(summary);
    }

    @Override
    public ReportVos.ReportSummaryVO regenerate(FinanceRequests.ReportQuery request) {
        LocalDate periodStart = request == null ? null : parseDate(request.getPeriodStart());
        FinanceQueries.ReportQuery query = new FinanceQueries.ReportQuery();
        query.setReportType(request == null ? null : request.getReportType());
        query.setPeriodStart(request == null ? null : request.getPeriodStart());
        query.setScope(request == null ? null : request.getScope());
        query.setRangeType(request == null ? null : request.getRangeType());
        query.setRangeRefId(request == null ? null : request.getRangeRefId());
        Period period = resolve(query);
        Scope scope = scope(query.getScope());
        String rangeType = query.getRangeType() == null ? FinanceConst.RANGE_FARM : query.getRangeType();
        FinanceErrors.require(FinanceConst.RANGE_FARM.equals(rangeType) || query.getRangeRefId() != null,
                "range_ref_id", "请选择品类或批次");
        String viewKey = viewKey(scope, period, rangeType, query.getRangeRefId());
        ReportVos.BusinessReportVO view = view(query);
        String hash = hash(view);
        BizReportSummary summary = summaryMapper.selectOne(Wrappers.<BizReportSummary>lambdaQuery()
                .eq(BizReportSummary::getViewKey, viewKey)
                .last("LIMIT 1"));
        if (summary != null && FinanceConst.ADVICE_GENERATING.equals(summary.getStatus())) {
            return toSummary(summary);
        }
        long todayCount = summaryMapper.selectCount(Wrappers.<BizReportSummary>lambdaQuery()
                .eq(BizReportSummary::getViewKey, viewKey)
                .ge(BizReportSummary::getCreatedAt, BeijingTime.today().atStartOfDay()));
        if (todayCount >= DAILY_AI_LIMIT) {
            throw new BizException(ErrorCode.TOO_FREQUENT, "今天重新生成次数已用完，明天再试");
        }
        if (summary == null) {
            summary = createSummary(viewKey, scope, period, rangeType, query.getRangeRefId(), hash);
        } else {
            summary.setStatus(FinanceConst.ADVICE_GENERATING);
            summary.setDataHash(hash);
            summary.setStartedAt(BeijingTime.now());
            summary.setErrorMessage(null);
            summaryMapper.updateById(summary);
        }
        generate(summary, view);
        return toSummary(summary);
    }

    // ===== E42 导出 =====

    @Override
    public ReportVos.ReportExportVO export(FinanceRequests.ReportExportRequest request) {
        FinanceErrors.require(request != null && request.getFormat() != null, "format", "请选择导出格式");
        FinanceErrors.require("XLSX".equals(request.getFormat()), "format", "导出失败，请稍后重试");
        FinanceQueries.ReportQuery query = new FinanceQueries.ReportQuery();
        query.setReportType(request.getReportType());
        query.setPeriodStart(request.getPeriodStart());
        query.setScope(request.getScope());
        query.setRangeType(request.getRangeType());
        query.setRangeRefId(request.getRangeRefId());
        Period period = resolve(query);
        Scope scope = scope(request.getScope());
        ReportVos.BusinessReportVO view = view(query);
        String farmLabel = scope.all() || scope.farms().isEmpty() ? "全部养殖场"
                : scope.farms().get(0).getName();
        String fileName = farmLabel + "_经营报表_" + FinanceSupport.format(period.start()) + "_"
                + FinanceSupport.format(period.end()) + ".xlsx";
        byte[] bytes = xlsx(view);
        Long userId = UserContext.requireUserId();
        BizReportExport export = new BizReportExport();
        export.setScopeType(scope.all() ? FinanceConst.SCOPE_ALL : "FARM");
        export.setFarmId(scope.all() ? null : support.farmId());
        export.setUserId(userId);
        export.setReportType(period.reportType());
        export.setPeriodStart(period.start());
        export.setPeriodEnd(period.end());
        export.setRangeType(request.getRangeType() == null ? FinanceConst.RANGE_FARM : request.getRangeType());
        export.setRangeRefId(request.getRangeRefId());
        export.setFileFormat("XLSX");
        export.setStatus("SUCCESS");
        export.setCreatedAt(BeijingTime.now());

        String bucket = objectStorage.privateBucket();
        String objectKey = objectStorage.buildObjectKey(FileScene.EXPORT, "xlsx");
        try {
            objectStorage.put(bucket, objectKey, new ByteArrayInputStream(bytes), bytes.length,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            FileEntity file = new FileEntity();
            file.setBucket(bucket);
            file.setObjectKey(objectKey);
            file.setOriginalName(fileName);
            file.setFileKind("DOCUMENT");
            file.setScene(FileScene.EXPORT.name());
            file.setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            file.setFileExt("xlsx");
            file.setFileSize((long) bytes.length);
            file.setUploadStatus("READY");
            file.setTranscodeStatus("NONE");
            file.setFarmId(scope.all() ? null : support.farmId());
            file.setUploaderType("USER");
            file.setClientType("APP_ANDROID");
            file.setBound(1);
            file.setExpireAt(BeijingTime.now().plusDays(7));
            fileMapper.insert(file);
            export.setFileId(file.getId());
            exportMapper.insert(export);
            ReportVos.ReportExportVO vo = new ReportVos.ReportExportVO();
            vo.setExportId(export.getId());
            vo.setFileId(file.getId());
            vo.setFileName(fileName);
            vo.setExpireAt(FinanceSupport.format(file.getExpireAt()));
            vo.setDownloadUrl(objectStorage.presignedGetUrl(bucket, objectKey, Duration.ofDays(7), fileName));
            return vo;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "导出失败，请稍后重试");
        }
    }

    // ===== 计算 =====

    private List<ReportVos.StockMetric> stockMetrics(List<Long> farmIds, List<BatchInfo> batches,
                                                     List<SaleOrder> orders, Period period) {
        int sold = orders.stream().filter(o -> inPeriod(o.getSaleDate(), period))
                .mapToInt(o -> o.getQuantity() == null ? 0 : o.getQuantity()).sum();
        int entry = batches.stream().filter(b -> inPeriod(b.entryDate(), period))
                .mapToInt(b -> b.entryCount() == null ? 0 : b.entryCount()).sum();
        int end = batches.stream().mapToInt(b -> b.currentCount() == null ? 0 : b.currentCount()).sum();
        int begin = Math.max(0, end - entry + sold);
        Map<String, int[]> byUnit = new LinkedHashMap<>();
        for (BatchInfo batch : batches) {
            String unit = support.countUnit(batch.categoryId());
            int[] counts = byUnit.computeIfAbsent(unit == null ? "HEAD" : unit, k -> new int[2]);
            counts[0] += batch.currentCount() == null ? 0 : batch.currentCount();
        }
        List<ReportVos.StockMetric> result = new ArrayList<>();
        for (Map.Entry<String, int[]> entryItem : byUnit.entrySet()) {
            ReportVos.StockMetric metric = new ReportVos.StockMetric();
            metric.setCountUnit(entryItem.getKey());
            metric.setCountUnitText(unitText(entryItem.getKey()));
            metric.setEnd(entryItem.getValue()[0]);
            metric.setBegin(Math.max(0, metric.getEnd() - entry + sold));
            metric.setChange(metric.getEnd() - metric.getBegin());
            result.add(metric);
        }
        if (result.isEmpty()) {
            ReportVos.StockMetric metric = new ReportVos.StockMetric();
            metric.setCountUnit("HEAD");
            metric.setCountUnitText("头");
            metric.setBegin(Math.max(0, begin));
            metric.setEnd(end);
            metric.setChange(end - begin);
            result.add(metric);
        }
        return result;
    }

    private ReportVos.Mortality mortality() {
        ReportVos.Mortality mortality = new ReportVos.Mortality();
        mortality.setRate(null);
        mortality.setChangePoint(null);
        mortality.setDeathCount(0);
        mortality.setComparable(false);
        return mortality;
    }

    private ReportVos.Feed feed(List<Long> farmIds) {
        ReportVos.Feed feed = new ReportVos.Feed();
        BigDecimal kg = settlements(farmIds).stream().map(BatchSettlement::getFeedKg)
                .map(FinanceSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        feed.setKg(FinanceSupport.scale2(kg));
        feed.setChangeRate(null);
        feed.setComparable(false);
        return feed;
    }

    private ReportVos.Balance balance(List<FinanceRecord> records, Period period) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (FinanceRecord record : records) {
            if (FinanceConst.INCOME.equals(record.getRecordType())) {
                income = income.add(FinanceSupport.nz(record.getAmount()));
            } else {
                expense = expense.add(FinanceSupport.nz(record.getAmount()));
            }
        }
        BigDecimal balance = income.subtract(expense);
        Period previous = previousPeriod(period);
        List<FinanceRecord> prevRecords = records(period.farmIds(), previous.start(), previous.end());
        BigDecimal prevIncome = BigDecimal.ZERO;
        BigDecimal prevExpense = BigDecimal.ZERO;
        for (FinanceRecord record : prevRecords) {
            if (FinanceConst.INCOME.equals(record.getRecordType())) {
                prevIncome = prevIncome.add(FinanceSupport.nz(record.getAmount()));
            } else {
                prevExpense = prevExpense.add(FinanceSupport.nz(record.getAmount()));
            }
        }
        BigDecimal prevBalance = prevIncome.subtract(prevExpense);
        ReportVos.Balance vo = new ReportVos.Balance();
        vo.setAmount(FinanceSupport.scale2(balance));
        vo.setComparable(prevBalance.signum() != 0);
        vo.setChangeRate(vo.getComparable() ? FinanceSupport.rate(balance, prevBalance) : null);
        return vo;
    }

    private ReportVos.DataCheck dataCheck() {
        ReportVos.DataCheck check = new ReportVos.DataCheck();
        check.setHasIssue(false);
        check.setBatchCount(0);
        check.setMoreCount(0);
        return check;
    }

    private ReportVos.BatchProfits batchProfits(List<Long> farmIds, Period period) {
        List<BatchSettlement> settlements = settlements(farmIds).stream()
                .filter(s -> FinanceConst.SETTLE_LOCKED.equals(s.getStatus()))
                .filter(s -> s.getSettledAt() != null && inPeriod(s.getSettledAt().toLocalDate(), period))
                .sorted(Comparator.comparing(BatchSettlement::getProfit).reversed()).toList();
        ReportVos.BatchProfits profits = new ReportVos.BatchProfits();
        BigDecimal total = BigDecimal.ZERO;
        for (BatchSettlement settlement : settlements) {
            ReportVos.ProfitItem item = new ReportVos.ProfitItem();
            BatchInfo batch = batchOf(settlement.getBatchId());
            item.setBatchId(settlement.getBatchId());
            item.setBatchNo(batch == null ? null : batch.batchNo());
            item.setProfit(settlement.getProfit());
            profits.getItems().add(item);
            total = total.add(FinanceSupport.nz(settlement.getProfit()));
        }
        profits.setProfitTotal(settlements.isEmpty() ? null : FinanceSupport.scale2(total));
        return profits;
    }

    private List<ReportVos.RunningBatch> runningBatches(List<Long> farmIds) {
        List<BatchSettlement> settlements = settlements(farmIds).stream()
                .filter(s -> FinanceConst.SETTLE_RUNNING.equals(s.getStatus()))
                .sorted(Comparator.comparing(BatchSettlement::getCalcAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        List<ReportVos.RunningBatch> result = new ArrayList<>();
        for (BatchSettlement settlement : settlements) {
            ReportVos.RunningBatch item = new ReportVos.RunningBatch();
            BatchInfo batch = batchOf(settlement.getBatchId());
            item.setBatchId(settlement.getBatchId());
            item.setBatchNo(batch == null ? null : batch.batchNo());
            item.setPenName(batch == null || batch.penIds() == null || batch.penIds().isEmpty() ? null
                    : support.penName(batch.penIds().get(0)));
            item.setCostTotal(settlement.getCostTotal());
            result.add(item);
        }
        return result;
    }

    private String currentStockText(List<Long> farmIds) {
        return batches(farmIds).stream().mapToInt(b -> b.currentCount() == null ? 0 : b.currentCount()).sum()
                + " 头";
    }

    // ===== AI =====

    private void generate(BizReportSummary summary, ReportVos.BusinessReportVO view) {
        if (!aiGateway.isEnabled()) {
            fail(summary, "AI 未配置");
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        }
        try {
            String prompt = "经营报表指标（JSON）：" + JSON.writeValueAsString(view.getMetrics())
                    + "；批次盈亏：" + JSON.writeValueAsString(view.getBatchProfits())
                    + "。请输出 JSON：{highlight, problem, suggestion}，每段不超过 100 字，数字必须来自输入。";
            AiChatResult result = aiGateway.chat(AiChatRequest.builder()
                    .scene(AiScene.REPORT_SUMMARY)
                    .message(AiMessage.system("你是养殖场经营分析助手，只输出 JSON。"))
                    .message(AiMessage.user(prompt))
                    .jsonMode(true)
                    .maxTokens(800)
                    .build());
            JsonNode node = JSON.readTree(result.getContent());
            summary.setHighlight(limit(node.path("highlight").asText(""), 100));
            summary.setProblem(limit(node.path("problem").asText(""), 100));
            summary.setSuggestion(limit(node.path("suggestion").asText(""), 100));
            summary.setStatus(FinanceConst.ADVICE_SUCCESS);
            summary.setAiModel(result.getModel());
            summary.setPromptVersion("v1");
            summary.setGeneratedAt(BeijingTime.now());
            summary.setErrorMessage(null);
            summaryMapper.updateById(summary);
        } catch (Exception e) {
            fail(summary, "AI 摘要生成失败");
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private void generateQuietly(BizReportSummary summary, ReportVos.BusinessReportVO view) {
        try {
            generate(summary, view);
        } catch (BizException ignored) {
            // GET 时 AI 失败不抛错，返回 FAILED 状态由客户端决定是否重试
        }
    }

    private void fail(BizReportSummary summary, String message) {
        summary.setStatus(FinanceConst.ADVICE_FAILED);
        summary.setErrorMessage(message);
        summary.setGeneratedAt(BeijingTime.now());
        summaryMapper.updateById(summary);
    }

    private BizReportSummary createSummary(String viewKey, Scope scope, Period period, String rangeType,
                                           Long rangeRefId, String hash) {
        BizReportSummary summary = new BizReportSummary();
        summary.setViewKey(viewKey);
        summary.setScopeType(scope.all() ? FinanceConst.SCOPE_ALL : "FARM");
        summary.setFarmId(scope.all() ? null : support.farmId());
        summary.setUserId(scope.all() ? UserContext.requireUserId() : null);
        summary.setReportType(period.reportType());
        summary.setPeriodStart(period.start());
        summary.setPeriodEnd(period.end());
        summary.setRangeType(rangeType);
        summary.setRangeRefId(rangeRefId);
        summary.setDataHash(hash);
        summary.setStatus(FinanceConst.ADVICE_GENERATING);
        summary.setStartedAt(BeijingTime.now());
        summary.setCreatedBy(0L);
        summaryMapper.insert(summary);
        return summary;
    }

    private ReportVos.ReportSummaryVO toSummary(BizReportSummary summary) {
        ReportVos.ReportSummaryVO vo = new ReportVos.ReportSummaryVO();
        vo.setStatus(summary.getStatus());
        vo.setHighlight(summary.getHighlight());
        vo.setProblem(summary.getProblem());
        vo.setSuggestion(summary.getSuggestion());
        vo.setGeneratedAt(FinanceSupport.format(summary.getGeneratedAt()));
        vo.setOutdated(false);
        return vo;
    }

    // ===== 范围与期次 =====

    private record Period(String reportType, LocalDate start, LocalDate end, boolean toDate, List<Long> farmIds) {
    }

    private record Scope(boolean all, List<Long> ids, List<FarmBriefVO> farms, List<String> staffFarmNames) {
    }

    private Scope scope(String scope) {
        Long current = support.farmId();
        if (!FinanceConst.SCOPE_ALL.equals(scope)) {
            return new Scope(false, List.of(current), List.of(), List.of());
        }
        List<FarmBriefVO> all = farmQueryService.listFarmsOfUser(UserContext.requireUserId());
        List<FarmBriefVO> included = all.stream()
                .filter(f -> "NORMAL".equals(f.getStatus()) || "DISABLED".equals(f.getStatus()))
                .filter(f -> !"STAFF".equals(f.getRole())).toList();
        List<String> staff = all.stream().filter(f -> "STAFF".equals(f.getRole()))
                .map(FarmBriefVO::getName).filter(Objects::nonNull).toList();
        List<Long> ids = included.isEmpty() ? List.of(current)
                : included.stream().map(FarmBriefVO::getFarmId).toList();
        return new Scope(true, ids, included, staff);
    }

    private Period resolve(FinanceQueries.ReportQuery query) {
        String type = query.getReportType() == null ? FinanceConst.REPORT_TO_DATE : query.getReportType();
        FinanceErrors.require(List.of(FinanceConst.REPORT_WEEKLY, FinanceConst.REPORT_MONTHLY,
                FinanceConst.REPORT_TO_DATE).contains(type), "report_type", "请选择报表类型");
        Scope scope = scope(query.getScope());
        LocalDate today = BeijingTime.today();
        if (FinanceConst.REPORT_TO_DATE.equals(type)) {
            return new Period(type, today.withDayOfMonth(1), today, true, scope.ids());
        }
        FinanceErrors.require(query.getPeriodStart() != null, "period_start", "请选择报表期次");
        LocalDate start = parseDate(query.getPeriodStart());
        FinanceErrors.require(start != null, "period_start", "请选择报表期次");
        BizReport report = TenantSkip.call(() -> reportMapper.selectOne(Wrappers.<BizReport>lambdaQuery()
                .in(BizReport::getFarmId, scope.ids())
                .eq(BizReport::getReportType, type)
                .eq(BizReport::getPeriodStart, start)
                .last("LIMIT 1")));
        if (report == null) {
            throw FinanceErrors.field("period_start", "该期报表还没有生成");
        }
        return new Period(type, report.getPeriodStart(), report.getPeriodEnd(), false, scope.ids());
    }

    private Period previousPeriod(Period period) {
        LocalDate prevStart;
        LocalDate prevEnd;
        if (FinanceConst.REPORT_WEEKLY.equals(period.reportType())) {
            prevStart = period.start().minusWeeks(1);
            prevEnd = period.end().minusWeeks(1);
        } else if (FinanceConst.REPORT_MONTHLY.equals(period.reportType())) {
            prevStart = period.start().minusMonths(1).withDayOfMonth(1);
            prevEnd = period.start().minusMonths(1).withDayOfMonth(
                    Math.min(period.start().minusMonths(1).lengthOfMonth(), period.start().lengthOfMonth()));
        } else {
            prevStart = period.start().minusMonths(1);
            prevEnd = period.end().minusMonths(1);
            if (prevEnd.isAfter(prevStart.withDayOfMonth(prevStart.lengthOfMonth()))) {
                prevEnd = prevStart.withDayOfMonth(prevStart.lengthOfMonth());
            }
        }
        return new Period(period.reportType(), prevStart, prevEnd, period.toDate(), period.farmIds());
    }

    private List<BatchInfo> batches(List<Long> farmIds) {
        List<BatchInfo> result = new ArrayList<>();
        for (Long farmId : farmIds) {
            result.addAll(batchQueryService.listActive(farmId));
        }
        List<BatchSettlement> settlements = settlements(farmIds);
        Set<Long> active = result.stream().map(BatchInfo::batchId).collect(Collectors.toSet());
        for (BatchSettlement settlement : settlements) {
            if (!active.contains(settlement.getBatchId())) {
                BatchInfo batch = batchOf(settlement.getBatchId());
                if (batch != null) {
                    result.add(batch);
                }
            }
        }
        return result;
    }

    private List<SaleOrder> orders(List<Long> farmIds) {
        return TenantSkip.call(() -> orderMapper.selectList(Wrappers.<SaleOrder>lambdaQuery()
                .in(SaleOrder::getFarmId, farmIds)
                .ne(SaleOrder::getPaymentStatus, FinanceConst.PAY_VOIDED)));
    }

    private List<FinanceRecord> records(List<Long> farmIds, LocalDate start, LocalDate end) {
        return TenantSkip.call(() -> recordMapper.selectList(Wrappers.<FinanceRecord>lambdaQuery()
                .in(FinanceRecord::getFarmId, farmIds)
                .between(FinanceRecord::getRecordDate, start, end)
                .eq(FinanceRecord::getStatus, FinanceConst.RECORD_NORMAL)));
    }

    private List<BatchSettlement> settlements(List<Long> farmIds) {
        return TenantSkip.call(() -> settlementMapper.selectList(Wrappers.<BatchSettlement>lambdaQuery()
                .in(BatchSettlement::getFarmId, farmIds)));
    }

    private BatchInfo batchOf(Long batchId) {
        try {
            return batchQueryService.requireBatch(batchId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String viewKey(Scope scope, Period period, String rangeType, Long rangeRefId) {
        return (scope.all() ? FinanceConst.SCOPE_ALL : "FARM") + "|" + period.reportType() + "|"
                + FinanceSupport.format(period.start()) + "|" + FinanceSupport.format(period.end()) + "|"
                + rangeType + "|" + (rangeRefId == null ? 0 : rangeRefId) + "|"
                + (scope.all() ? UserContext.requireUserId() : support.farmId());
    }

    private String hash(ReportVos.BusinessReportVO view) {
        try {
            String text = JSON.writeValueAsString(view.getMetrics()) + "|"
                    + JSON.writeValueAsString(view.getBatchProfits());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return "0".repeat(64);
        }
    }

    // ===== 导出 =====

    private byte[] xlsx(ReportVos.BusinessReportVO view) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet metrics = workbook.createSheet("指标");
            int row = 0;
            row = writeRow(metrics, row, "统计期", view.getPeriod().getStart() + " 至 " + view.getPeriod().getEnd());
            if (view.getMetrics() != null) {
                for (ReportVos.StockMetric stock : view.getMetrics().getStock()) {
                    row = writeRow(metrics, row, "存栏(" + stock.getCountUnitText() + ")",
                            stock.getBegin() + " → " + stock.getEnd() + "（变化 " + stock.getChange() + "）");
                }
                if (view.getMetrics().getMortality() != null) {
                    row = writeRow(metrics, row, "死亡率", view.getMetrics().getMortality().getRate());
                }
                if (view.getMetrics().getFeed() != null) {
                    row = writeRow(metrics, row, "饲料用量(千克)", view.getMetrics().getFeed().getKg());
                }
                if (view.getMetrics().getBalance() != null) {
                    row = writeRow(metrics, row, "收支结余(元)", view.getMetrics().getBalance().getAmount());
                }
            }
            Sheet daily = workbook.createSheet("逐日数据");
            ReportVos.ReportTrendVO trend = new ReportVos.ReportTrendVO();
            FinanceQueries.ReportQuery trendQuery = new FinanceQueries.ReportQuery();
            trendQuery.setReportType(view.getPeriod().getToDate() != null ? FinanceConst.REPORT_TO_DATE
                    : FinanceConst.REPORT_MONTHLY);
            trendQuery.setScope(view.getScope());
            trendQuery.setMetric("STOCK");
            // 逐日数据使用已计算的报表期，直接输出存栏趋势点的近似值（当前存栏）
            int dailyRow = 0;
            writeRow(daily, dailyRow++, "日期", "指标", "数值");
            for (ReportVos.TrendPoint point : trend.getPoints()) {
                dailyRow = writeRow(daily, dailyRow, point.getDate(), "STOCK", point.getValue());
            }
            if (dailyRow == 1) {
                writeRow(daily, dailyRow, "-", "暂无逐日数据", "");
            }
            Sheet profits = workbook.createSheet("批次盈亏");
            int profitRow = 0;
            profitRow = writeRow(profits, profitRow, "批次号", "盈亏(元)");
            if (view.getBatchProfits() != null) {
                for (ReportVos.ProfitItem item : view.getBatchProfits().getItems()) {
                    profitRow = writeRow(profits, profitRow, item.getBatchNo(), item.getProfit());
                }
                profitRow = writeRow(profits, profitRow, "合计", view.getBatchProfits().getProfitTotal());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "导出失败，请稍后重试");
        }
    }

    private static int writeRow(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Number number) {
                row.createCell(i).setCellValue(number.doubleValue());
            } else {
                row.createCell(i).setCellValue(value == null ? "" : String.valueOf(value));
            }
        }
        return index + 1;
    }

    // ===== 工具 =====

    private static boolean inPeriod(LocalDate date, Period period) {
        return date != null && !date.isBefore(period.start()) && !date.isAfter(period.end());
    }

    private static String unitText(String unit) {
        return switch (unit) {
            case "BIRD" -> "只";
            case "HEAD" -> "头";
            default -> unit;
        };
    }

    private static String nextGenerationDate(String type) {
        LocalDate today = BeijingTime.today();
        if (FinanceConst.REPORT_WEEKLY.equals(type)) {
            LocalDate nextMonday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
            return FinanceSupport.format(nextMonday);
        }
        return FinanceSupport.format(YearMonth.from(today).plusMonths(1).atDay(1));
    }

    private static String limit(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.codePointCount(0, text.length()) <= max ? text : text.substring(0, max);
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value, BeijingTime.DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
