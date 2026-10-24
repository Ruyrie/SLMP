package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.audit.service.AuditPendingService;
import com.zhimu.server.admin.audit.support.AuditWaitCalculator;
import com.zhimu.server.admin.audit.vo.PendingCountVO;
import com.zhimu.server.admin.audit.vo.QueueCountVO;
import com.zhimu.server.admin.system.mapper.readonly.AdminOverviewQueryMapper;
import com.zhimu.server.admin.system.query.ActiveStatRow;
import com.zhimu.server.admin.system.query.CategoryDistRow;
import com.zhimu.server.admin.system.query.DailyStatRow;
import com.zhimu.server.admin.system.query.FarmMapQuery;
import com.zhimu.server.admin.system.query.FarmMapRow;
import com.zhimu.server.admin.system.query.FarmStatRow;
import com.zhimu.server.admin.system.query.MonthAuditRow;
import com.zhimu.server.admin.system.query.PendingFarmRow;
import com.zhimu.server.admin.system.query.RegionCountRow;
import com.zhimu.server.admin.system.query.TrendQuery;
import com.zhimu.server.admin.system.query.UserStatRow;
import com.zhimu.server.admin.system.service.OverviewService;
import com.zhimu.server.admin.system.service.OperationLogAdminService;
import com.zhimu.server.admin.system.query.OperationLogQuery;
import com.zhimu.server.admin.system.support.AdminLogTexts;
import com.zhimu.server.admin.system.vo.FarmMapVO;
import com.zhimu.server.admin.system.vo.OperationLogItemVO;
import com.zhimu.server.admin.system.vo.OverviewVO;
import com.zhimu.server.admin.system.vo.RegionDetailVO;
import com.zhimu.server.admin.system.vo.TrendVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ADM-S06–S09：各统计块独立容错，单块失败只写入 failed_blocks。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private static final Map<String, String> TYPE_TEXTS = Map.of(
            "FARM_AUDIT", "养殖场注册审核",
            "FARM_CHANGE", "资料变更审核",
            "CHANGE_REQUEST", "修改申请审核",
            "ACCOUNT_RECOVERY", "账号找回"
    );
    private static final Map<String, String> PAGE_CODES = Map.of(
            "FARM_AUDIT", "WA-R01",
            "FARM_CHANGE", "WA-R02",
            "CHANGE_REQUEST", "WA-R03",
            "ACCOUNT_RECOVERY", "WA-R04"
    );
    private static final Map<String, String> PERMISSIONS = Map.of(
            "FARM_AUDIT", "WA-R01:VIEW",
            "FARM_CHANGE", "WA-R02:VIEW",
            "CHANGE_REQUEST", "WA-R03:VIEW",
            "ACCOUNT_RECOVERY", "WA-R04:VIEW"
    );

    private final AdminOverviewQueryMapper queryMapper;
    private final AuditPendingService auditPendingService;
    private final OperationLogAdminService operationLogAdminService;

    @Override
    public OverviewVO overview() {
        LocalDateTime now = BeijingTime.now();
        LocalDate today = BeijingTime.today();
        LocalDate yesterday = today.minusDays(1);
        LocalDate activeStart = yesterday.minusDays(6);
        List<String> failedBlocks = new ArrayList<>();
        OverviewVO.OverviewVOBuilder builder = OverviewVO.builder()
                .asOf(now)
                .failedBlocks(failedBlocks);

        try {
            PendingCountVO counts = auditPendingService.overviewPendingCounts();
            builder.pending(pending(counts));
        } catch (RuntimeException e) {
            log.warn("平台概览待处理计算失败", e);
            failedBlocks.add("pending");
        }

        try {
            FarmStatRow row = queryMapper.selectFarmStats();
            builder.farmStat(OverviewVO.FarmStat.builder()
                    .total(intValue(row == null ? null : row.getTotal()))
                    .approved(intValue(row == null ? null : row.getApproved()))
                    .normal(intValue(row == null ? null : row.getNormal()))
                    .disabled(intValue(row == null ? null : row.getDisabled()))
                    .frozen(intValue(row == null ? null : row.getFrozen()))
                    .pending(intValue(row == null ? null : row.getPending()))
                    .rejected(intValue(row == null ? null : row.getRejected()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("平台概览养殖场统计失败", e);
            failedBlocks.add("farm_stat");
        }

        try {
            UserStatRow row = queryMapper.selectUserStats(today.withDayOfMonth(1));
            builder.userStat(OverviewVO.UserStat.builder()
                    .total(intValue(row == null ? null : row.getTotal()))
                    .normal(intValue(row == null ? null : row.getNormal()))
                    .disabled(intValue(row == null ? null : row.getDisabled()))
                    .newThisMonth(intValue(row == null ? null : row.getNewThisMonth()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("平台概览账号统计失败", e);
            failedBlocks.add("user_stat");
        }

        FarmStatRow farmStat = null;
        UserStatRow userStat = null;
        try {
            farmStat = queryMapper.selectFarmStats();
            userStat = queryMapper.selectUserStats(today.withDayOfMonth(1));
            ActiveStatRow row = queryMapper.selectActiveStats(activeStart, yesterday, today);
            builder.activeStat(OverviewVO.ActiveStat.builder()
                    .rangeStart(activeStart)
                    .rangeEnd(yesterday)
                    .users7d(intValue(row == null ? null : row.getUsers7d()))
                    .users7dRatio(ratio(row == null ? null : row.getUsers7d(),
                            userStat == null ? null : userStat.getNormal()))
                    .usersToday(intValue(row == null ? null : row.getUsersToday()))
                    .farms7d(intValue(row == null ? null : row.getFarms7d()))
                    .farms7dRatio(ratio(row == null ? null : row.getFarms7d(),
                            farmStat == null ? null : farmStat.getNormal()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("平台概览活跃统计失败", e);
            failedBlocks.add("active_stat");
        }

        try {
            List<CategoryDistRow> rows = queryMapper.selectCategoryDistribution();
            long total = rows.stream().mapToLong(r -> r.getCount() == null ? 0 : r.getCount()).sum();
            List<OverviewVO.CategoryDist> distribution = new ArrayList<>();
            for (CategoryDistRow row : rows) {
                distribution.add(OverviewVO.CategoryDist.builder()
                        .species(row.getSpecies())
                        .speciesText(speciesText(row.getSpecies()))
                        .count(intValue(row.getCount()))
                        .ratio(ratio(row.getCount(), total))
                        .build());
            }
            builder.categoryDist(distribution);
        } catch (RuntimeException e) {
            log.warn("平台概览品类分布失败", e);
            failedBlocks.add("category_dist");
        }

        try {
            List<MonthAuditRow> rows = queryMapper.selectMonthAudit(
                    today.withDayOfMonth(1).atStartOfDay(), now);
            Map<String, MonthAuditRow> byType = new LinkedHashMap<>();
            for (MonthAuditRow row : rows) {
                byType.put(row.getType(), row);
            }
            List<OverviewVO.MonthAuditItem> items = new ArrayList<>();
            for (String type : List.of("FARM_AUDIT", "FARM_CHANGE", "CHANGE_REQUEST", "ACCOUNT_RECOVERY")) {
                MonthAuditRow row = byType.get(type);
                items.add(OverviewVO.MonthAuditItem.builder()
                        .type(type)
                        .typeText(monthAuditText(type))
                        .approved(intValue(row == null ? null : row.getApproved()))
                        .rejected(intValue(row == null ? null : row.getRejected()))
                        .avgHandleHours(row == null ? null : row.getAvgHandleHours())
                        .build());
            }
            builder.monthAudit(OverviewVO.MonthAudit.builder()
                    .rangeStart(today.withDayOfMonth(1))
                    .rangeEnd(today)
                    .items(items)
                    .build());
        } catch (RuntimeException e) {
            log.warn("平台概览本月审核失败", e);
            failedBlocks.add("month_audit");
        }

        try {
            OperationLogQuery query = new OperationLogQuery();
            query.setDateFrom(today.minusDays(6));
            query.setDateTo(today);
            query.setPage(1);
            query.setSize(4);
            List<OverviewVO.RecentOp> recent = new ArrayList<>();
            for (OperationLogItemVO item : operationLogAdminService.list(query).getList()) {
                recent.add(OverviewVO.RecentOp.builder()
                        .logId(item.getLogId())
                        .operatorName(item.getOperatorName())
                        .opTypeText(item.getOpTypeText())
                        .opActionText(item.getOpActionText())
                        .targetName(item.getTargetName())
                        .reason(item.getReason())
                        .createdAt(item.getCreatedAt())
                        .build());
            }
            builder.recentOps(recent);
        } catch (RuntimeException e) {
            log.warn("平台概览最近操作失败", e);
            failedBlocks.add("recent_ops");
        }
        return builder.build();
    }

    @Override
    public FarmMapVO farmMap(FarmMapQuery query) {
        String level = query.levelOrDefault();
        String parentCode = validateParent(level, query.getParentCode());
        List<FarmMapRow> rows = queryMapper.selectFarmMap(level, parentCode, query.getCategoryGroup());
        List<FarmMapVO.Cluster> clusters = new ArrayList<>();
        List<FarmMapVO.Point> points = new ArrayList<>();
        int approved = 0;
        int pending = 0;
        int frozen = 0;
        for (FarmMapRow row : rows) {
            approved += intValue(row.getApproved());
            pending += intValue(row.getPending());
            frozen += intValue(row.getFrozen());
            if ("POINT".equals(level)) {
                points.add(FarmMapVO.Point.builder()
                        .farmId(row.getFarmId())
                        .name(row.getRegionName())
                        .lng(row.getLng())
                        .lat(row.getLat())
                        .farmStatus(row.getFarmStatus())
                        .build());
            } else {
                clusters.add(FarmMapVO.Cluster.builder()
                        .regionCode(row.getRegionCode())
                        .regionName(row.getRegionName())
                        .lng(row.getLng())
                        .lat(row.getLat())
                        .approved(intValue(row.getApproved()))
                        .pending(intValue(row.getPending()))
                        .frozen(intValue(row.getFrozen()))
                        .total(intValue(row.getApproved()) + intValue(row.getPending()) + intValue(row.getFrozen()))
                        .build());
            }
        }
        return FarmMapVO.builder()
                .level(level)
                .parentCode(parentCode)
                .legend(FarmMapVO.Legend.builder()
                        .approved(approved)
                        .pending(pending)
                        .frozen(frozen)
                        .build())
                .clusters(clusters.stream()
                        .sorted(Comparator.comparing(FarmMapVO.Cluster::getTotal,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList())
                .points(points)
                .build();
    }

    @Override
    public RegionDetailVO regionDetail(String regionCode) {
        String prefix = validateRegion(regionCode);
        RegionCountRow counts = queryMapper.selectRegionCounts(prefix);
        String regionName = queryMapper.selectRegionName(prefix);
        List<RegionDetailVO.PendingFarm> pendingFarms = new ArrayList<>();
        LocalDateTime now = BeijingTime.now();
        for (PendingFarmRow row : queryMapper.selectPendingFarms(prefix)) {
            AuditWaitCalculator.Wait wait = AuditWaitCalculator.of(row.getSubmittedAt(), now);
            pendingFarms.add(RegionDetailVO.PendingFarm.builder()
                    .farmId(row.getFarmId())
                    .auditId(row.getAuditId())
                    .farmName(row.getFarmName())
                    .categoryName(row.getCategoryName())
                    .waitText(wait.text())
                    .overdue(wait.overdue())
                    .submitNo(row.getSubmitNo())
                    .build());
        }
        return RegionDetailVO.builder()
                .regionCode(prefix)
                .regionName(regionName)
                .total(intValue(counts == null ? null : counts.getTotal()))
                .approved(intValue(counts == null ? null : counts.getApproved()))
                .pending(intValue(counts == null ? null : counts.getPending()))
                .frozen(intValue(counts == null ? null : counts.getFrozen()))
                .pendingFarms(pendingFarms)
                .build();
    }

    @Override
    public TrendVO trend(TrendQuery query) {
        String metric = query.getMetric();
        LocalDate yesterday = BeijingTime.today().minusDays(1);
        LocalDate start = yesterday.minusDays(29);
        List<DailyStatRow> rows = queryMapper.selectPlatformDailyStats(metric, start, yesterday);
        if (rows == null || rows.isEmpty()) {
            rows = switch (metric) {
                case "ACTIVE_USER" -> queryMapper.selectActiveDailyStats(start, yesterday);
                case "NEW_USER" -> queryMapper.selectNewUserDailyStats(start, yesterday);
                default -> queryMapper.selectProductionDailyStats(start, yesterday);
            };
        }
        Map<LocalDate, Integer> values = new HashMap<>();
        if (rows != null) {
            for (DailyStatRow row : rows) {
                values.put(row.getStatDate(), intValue(row.getValue()));
            }
        }
        List<TrendVO.Point> points = new ArrayList<>();
        List<Integer> sequence = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(yesterday); date = date.plusDays(1)) {
            int value = values.getOrDefault(date, 0);
            sequence.add(value);
            int from = Math.max(0, sequence.size() - 7);
            int sum = 0;
            for (int i = from; i < sequence.size(); i++) {
                sum += sequence.get(i);
            }
            int count = sequence.size() - from;
            points.add(TrendVO.Point.builder()
                    .date(date)
                    .value(value)
                    .avg7(BigDecimal.valueOf(sum)
                            .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP))
                    .build());
        }
        int total = sequence.stream().mapToInt(Integer::intValue).sum();
        int maxValue = sequence.stream().mapToInt(Integer::intValue).max().orElse(0);
        LocalDate maxDate = null;
        for (TrendVO.Point point : points) {
            if (point.getValue() == maxValue) {
                maxDate = point.getDate();
                break;
            }
        }
        boolean production = "PRODUCTION_RECORD".equals(metric);
        return TrendVO.builder()
                .metric(metric)
                .metricText(switch (metric) {
                    case "ACTIVE_USER" -> "活跃账号";
                    case "NEW_USER" -> "新注册账号";
                    default -> "生产登记次数";
                })
                .unit(production ? "次" : "个")
                .rangeStart(start)
                .rangeEnd(yesterday)
                .points(points)
                .summary(TrendVO.Summary.builder()
                        .dailyAvg(BigDecimal.valueOf(total)
                                .divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP))
                        .maxDate(maxDate)
                        .maxValue(maxValue)
                        .build())
                .build();
    }

    private OverviewVO.Pending pending(PendingCountVO counts) {
        AdminContext.LoginAdmin admin = AdminContext.get();
        List<OverviewVO.PendingItem> items = new ArrayList<>();
        for (QueueCountVO item : counts.getItems()) {
            boolean canHandle = admin != null && admin.hasPermission(PERMISSIONS.get(item.getQueue()));
            items.add(OverviewVO.PendingItem.builder()
                    .type(item.getQueue())
                    .typeText(TYPE_TEXTS.get(item.getQueue()))
                    .count(item.getCount())
                    .earliestAt(item.getEarliestAt())
                    .overdueCount(item.getOverdueCount())
                    .sellableDateCount("CHANGE_REQUEST".equals(item.getQueue())
                            ? counts.getSellableDateCount() : null)
                    .pageCode(PAGE_CODES.get(item.getQueue()))
                    .canHandle(canHandle)
                    .build());
        }
        return OverviewVO.Pending.builder()
                .total(items.stream().mapToInt(i -> i.getCount() == null ? 0 : i.getCount()).sum())
                .overdueCount(counts.getOverdueTotal())
                .items(items)
                .build();
    }

    private static String validateParent(String level, String parentCode) {
        if ("PROVINCE".equals(level)) {
            return null;
        }
        int length = switch (level) {
            case "CITY" -> 2;
            case "DISTRICT" -> 4;
            default -> 6;
        };
        if (parentCode == null || !parentCode.matches("\\d{" + length + "}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "区划编码不正确");
        }
        return parentCode;
    }

    private static String validateRegion(String regionCode) {
        if (regionCode == null || !regionCode.matches("\\d{2}|\\d{4}|\\d{6}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "区划编码不正确");
        }
        return regionCode;
    }

    private static BigDecimal ratio(Long numerator, Long denominator) {
        if (numerator == null || denominator == null || denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static int intValue(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static String speciesText(String species) {
        return switch (species == null ? "" : species) {
            case "PIG" -> "猪";
            case "POULTRY" -> "禽";
            case "CATTLE" -> "牛";
            case "SHEEP" -> "羊";
            default -> species;
        };
    }

    private static String monthAuditText(String type) {
        return switch (type) {
            case "FARM_AUDIT" -> "养殖场注册";
            case "FARM_CHANGE" -> "资料变更";
            case "CHANGE_REQUEST" -> "修改申请";
            default -> "账号找回";
        };
    }
}
