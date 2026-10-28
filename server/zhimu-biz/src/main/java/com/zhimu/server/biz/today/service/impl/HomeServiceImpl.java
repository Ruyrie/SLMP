package com.zhimu.server.biz.today.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.today.api.HomeMetricProvider;
import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.MessageMapper;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.service.HomeService;
import com.zhimu.server.biz.today.support.TodoBusinessDay;
import com.zhimu.server.biz.today.support.TodayText;
import com.zhimu.server.biz.today.support.TodoLinks;
import com.zhimu.server.biz.today.support.TodoScope;
import com.zhimu.server.biz.today.vo.HomeVO;
import com.zhimu.server.biz.today.vo.MyPenVO;
import com.zhimu.server.biz.today.vo.OwnerMetricsVO;
import com.zhimu.server.biz.today.vo.StaffMetricsVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.TodoStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首页聚合（API-T01、2.6.1）：基本信息 + 待办统计 + 角色块。
 * <p>
 * 存栏、批次、栏舍数与占用、我负责的栏舍、休药等由各模块 {@link HomeMetricProvider} 提供；
 * warning_count 统计未处理的 WARNING 类待办（库存预警、巡检异常上报 / 待处理诊断），与 T09 预警列表同口径；
 * receivable_* 统计未关闭的 RECEIVABLE 待办（出栏单未收款，模块10 写入）。
 */
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final FarmQueryService farmQueryService;
    private final AccountQueryService accountQueryService;
    private final TodoMapper todoMapper;
    private final com.zhimu.server.biz.stock.support.StockWarningReconciler stockWarnings;
    private final MessageMapper messageMapper;
    private final TodoScope todoScope;
    private final TodoLinks todoLinks;
    private final List<HomeMetricProvider> metricProviders;
    private final com.zhimu.server.biz.feeding.api.FeedTaskReconciliationService feedTaskReconciliation;

    @Override
    public HomeVO getHome(Long userId) {
        FarmContext.FarmMember member = FarmContext.require();
        LocalDate today = BeijingTime.today();
        LocalDateTime now = BeijingTime.now();
        AccountBrief account = accountQueryService.getBrief(userId);
        FarmBriefVO farm = findFarm(userId, member);

        HomeVO vo = new HomeVO();
        vo.setServerTime(TodayText.dateTime(now));
        vo.setGreeting(TodayText.greeting(now));
        vo.setUserName(account == null ? null : account.name());
        vo.setAvatarText(account == null ? null : initial(account.name()));
        vo.setRole(member.role().name());
        vo.setRoleText(roleText(member));
        vo.setFarmStatus(member.farmStatus() == null ? null : member.farmStatus().name());
        vo.setFarmStatusText(member.farmStatus() == null ? null : member.farmStatus().getText());
        vo.setFarm(farm);
        vo.setUnreadCount(unreadCount(userId));

        // 待审核 / 已驳回：只返回基本信息（客户端显示“审核通过后才能登记生产记录”）
        if (member.farmStatus() == FarmStatus.PENDING || member.farmStatus() == FarmStatus.REJECTED) {
            vo.setHasPen(false);
            return vo;
        }

        stockWarnings.reconcileFarm(member.farmId());
        feedTaskReconciliation.reconcileFarm(member.farmId());
        List<Todo> pending = pendingOfFarm(member.farmId());
        List<Todo> visiblePending = new ArrayList<>();
        for (Todo todo : pending) {
            if (todoScope.canView(todo, member)) {
                visiblePending.add(todo);
            }
        }
        vo.setHasPen(member.isStaff() ? !member.penIds().isEmpty() : hasPen(member.farmId()));

        if (member.isStaff()) {
            vo.setStaffMetrics(staffMetrics(member, visiblePending, today, now));
            vo.setMyPens(myPens(member, visiblePending, today, now));
        } else {
            // 指标只计入未完成待办（PENDING），直接复用已过滤的 visiblePending
            vo.setOwnerMetrics(ownerMetrics(member, visiblePending, today, now));
            vo.setAiHint(aiHint(visiblePending, today, now));
        }
        return vo;
    }

    // ===================== 养殖户块 =====================

    private OwnerMetricsVO ownerMetrics(FarmContext.FarmMember member, List<Todo> todos, LocalDate today,
                                        LocalDateTime now) {
        OwnerMetricsVO vo = new OwnerMetricsVO();
        int todayCount = 0;
        int overdueCount = 0;
        int warningCount = 0;
        int receivableCount = 0;
        int maxOverdueDays = 0;
        BigDecimal receivableAmount = BigDecimal.ZERO;
        List<String> warningNames = new ArrayList<>();
        for (Todo todo : todos) {
            if (TodoBusinessDay.isOpenToday(todo, today)) {
                todayCount++;
            }
            boolean open = TodoStatus.PENDING.name().equals(todo.getStatus());
            if (!open) {
                continue;
            }
            boolean overdue = isDateOverdue(todo, today);
            if (overdue) {
                overdueCount++;
            }
            if ("WARNING".equals(todo.getCategory())) {
                warningCount++;
                if (warningNames.size() < 3) {
                    warningNames.add(todo.getTitle());
                }
            }
            if ("RECEIVABLE".equals(todo.getTodoType())) {
                if (overdue) {
                    receivableCount++;
                    maxOverdueDays = Math.max(maxOverdueDays, overdueDays(todo, today));
                }
                if (todo.getAmount() != null) {
                    receivableAmount = receivableAmount.add(todo.getAmount());
                }
            }
        }
        vo.setTodayTodoCount(todayCount);
        vo.setOverdueCount(overdueCount);
        vo.setWarningCount(warningCount);
        vo.setWarningNames(warningNames);
        vo.setReceivableOverdueCount(receivableCount);
        vo.setReceivableMaxOverdueDays(maxOverdueDays);
        vo.setReceivableAmount(receivableAmount.setScale(2, RoundingMode.HALF_UP));

        // 休药批次与最近可出栏日：由 health 模块的 provider 提供
        int withdrawalBatches = 0;
        LocalDate nearestRelease = null;
        for (HomeMetricProvider provider : metricProviders) {
            withdrawalBatches = Math.max(withdrawalBatches, provider.withdrawalBatchCount(member.farmId()));
            LocalDate release = provider.nearestWithdrawalReleaseDate(member.farmId());
            if (release != null && (nearestRelease == null || release.isBefore(nearestRelease))) {
                nearestRelease = release;
            }
        }
        vo.setWithdrawalBatchCount(withdrawalBatches);
        vo.setWithdrawalReleaseDate(nearestRelease);
        vo.setWithdrawalDaysLeft(nearestRelease == null ? null
                : Math.toIntExact(ChronoUnit.DAYS.between(today, nearestRelease)));

        // 存栏数、批次数、栏舍占用率由模块04 provider 提供；没有 provider 时返回 0，字段保持齐全
        vo.setStockCount(stockCount(member.farmId()));
        vo.setBatchCount(batchCount(member.farmId()));
        BigDecimal occupancy = penOccupancyRate(member.farmId());
        vo.setPenOccupancyRate(occupancy == null ? BigDecimal.ZERO : occupancy);
        String unit = countUnit(member.farmId());
        String unitText = countUnitText(member.farmId());
        if (unit == null) {
            boolean poultry = "禽".equals(farmSpecies(member));
            unit = poultry ? "BIRD" : "HEAD";
            unitText = poultry ? "只" : "头";
        }
        vo.setCountUnit(unit);
        vo.setCountUnitText(unitText);
        return vo;
    }

    // ===================== 员工块 =====================

    private StaffMetricsVO staffMetrics(FarmContext.FarmMember member, List<Todo> pending, LocalDate today,
                                        LocalDateTime now) {
        StaffMetricsVO vo = new StaffMetricsVO();
        Set<String> penNames = new LinkedHashSet<>();
        int todayCount = 0;
        int overdueCount = 0;
        String overdueFirst = null;
        for (Todo todo : pending) {
            if (todo.getPenName() != null) {
                penNames.add(todo.getPenName());
            }
            if (TodoBusinessDay.isOpenToday(todo, today)) {
                todayCount++;
            }
            if (TodoStatus.PENDING.name().equals(todo.getStatus()) && isDateOverdue(todo, today)) {
                overdueCount++;
                if (overdueFirst == null) {
                    overdueFirst = todo.getTitle();
                }
            }
        }
        List<Todo> done = doneToday(member, today);
        List<String> doneNames = done.stream().map(Todo::getTitle).toList();
        vo.setPenCount(member.penIds().size());
        vo.setPenNames(new ArrayList<>(penNames));
        vo.setTodayTodoCount(todayCount);
        vo.setOverdueCount(overdueCount);
        vo.setTodayDoneCount(done.size());
        vo.setTodayDoneNames(doneNames);
        vo.setOverdueFirstText(overdueFirst);
        return vo;
    }

    private List<MyPenVO> myPens(FarmContext.FarmMember member, List<Todo> pending, LocalDate today,
                                 LocalDateTime now) {
        List<MyPenVO> pens = new ArrayList<>();
        Map<Long, HomeMetricProvider.PenMetric> extra = new LinkedHashMap<>();
        Map<Long, Integer> withdrawalDays = new LinkedHashMap<>();
        for (HomeMetricProvider provider : metricProviders) {
            for (HomeMetricProvider.PenMetric pen : provider.pens(member)) {
                extra.putIfAbsent(pen.penId(), pen);
            }
            provider.withdrawalDaysByPen(member).forEach((penId, days) -> {
                if (days != null && days > 0) {
                    withdrawalDays.merge(penId, days, Math::max);
                }
            });
        }
        for (Long penId : member.penIds()) {
            HomeMetricProvider.PenMetric metric = extra.get(penId);
            MyPenVO vo = new MyPenVO();
            vo.setPenId(penId);
            vo.setPenName(metric == null ? penNameOf(pending, penId) : metric.penName());
            vo.setPenTypeText(metric == null ? null : metric.penTypeText());
            vo.setCapacity(metric == null ? 0 : metric.capacity());
            List<String> todoNames = new ArrayList<>();
            int todoCount = 0;
            int total = 0;
            int done = 0;
            for (Todo todo : pending) {
                if (!penId.equals(todo.getPenId())) {
                    continue;
                }
                if (isTodayList(todo, today, now) || isOverdue(todo, now)) {
                    todoCount++;
                    total++;
                    if (todoNames.size() < 5) {
                        todoNames.add(todo.getTitle());
                    }
                }
            }
            for (Todo todo : doneToday(member, today)) {
                if (penId.equals(todo.getPenId())) {
                    done++;
                    total++;
                }
            }
            vo.setTodoCount(todoCount);
            vo.setTodayTaskDone(done);
            vo.setTodayTaskTotal(total);
            vo.setTodoNames(todoNames);
            vo.setBatches(batches(metric));
            Integer daysLeft = withdrawalDays.get(penId);
            vo.setInWithdrawal(daysLeft != null);
            vo.setWithdrawalDaysLeft(daysLeft);
            vo.setLink(penLink(member, penId, metric));
            pens.add(vo);
        }
        return pens;
    }

    private static String penNameOf(List<Todo> todos, Long penId) {
        for (Todo todo : todos) {
            if (penId.equals(todo.getPenId()) && todo.getPenName() != null) {
                return todo.getPenName();
            }
        }
        return null;
    }

    private List<MyPenVO.Batch> batches(HomeMetricProvider.PenMetric metric) {
        if (metric == null || metric.batches() == null) {
            return List.of();
        }
        List<MyPenVO.Batch> batches = new ArrayList<>();
        for (HomeMetricProvider.BatchMetric batch : metric.batches()) {
            MyPenVO.Batch vo = new MyPenVO.Batch();
            vo.setBatchId(batch.batchId());
            vo.setBatchNo(batch.batchNo());
            vo.setBreedText(batch.breedText());
            vo.setDayAge(batch.dayAge());
            vo.setHeadCount(batch.headCount());
            vo.setPenName(batch.penName());
            vo.setStageText(batch.stageText());
            batches.add(vo);
        }
        return batches;
    }

    private com.zhimu.server.biz.today.vo.LinkVO penLink(FarmContext.FarmMember member, Long penId,
                                                         HomeMetricProvider.PenMetric metric) {
        Map<String, String> params = new LinkedHashMap<>();
        boolean hasBatch = metric != null && metric.batches() != null && !metric.batches().isEmpty();
        if (hasBatch) {
            // L06 批次档案以 batch_id 打开；没有在栏批次时进 L02 我的栏舍（pen_id）
            params.put("batch_id", String.valueOf(metric.batches().get(0).batchId()));
        } else {
            params.put("pen_id", String.valueOf(penId));
        }
        String route = hasBatch ? "L06" : "L02";
        return TodoLinks.link(route, member.farmId(), member.farmName(), params);
    }

    // ===================== 公共 =====================

    private FarmBriefVO findFarm(Long userId, FarmContext.FarmMember member) {
        for (FarmBriefVO farm : farmQueryService.listFarmsOfUser(userId)) {
            if (member.farmId().equals(farm.getFarmId())) {
                return farm;
            }
        }
        return FarmBriefVO.builder()
                .farmId(member.farmId())
                .name(member.farmName())
                .status(member.farmStatus() == null ? null : member.farmStatus().name())
                .statusText(member.farmStatus() == null ? null : member.farmStatus().getText())
                .role(member.role().name())
                .build();
    }

    private String farmSpecies(FarmContext.FarmMember member) {
        for (FarmBriefVO farm : farmQueryService.listFarmsOfUser(member.userId())) {
            if (member.farmId().equals(farm.getFarmId())) {
                return farm.getSpeciesText();
            }
        }
        return null;
    }

    private String roleText(FarmContext.FarmMember member) {
        return switch (member.role()) {
            case OWNER -> "你是本场所有者";
            case MANAGER -> "你是本场场长";
            case STAFF -> (member.farmName() == null ? "" : member.farmName()) + "员工";
        };
    }

    private int unreadCount(Long userId) {
        Long count = messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getUserId, userId)
                .isNull(Message::getReadAt)
                .ge(Message::getSortTime, BeijingTime.now().minusDays(180))
                // 撤回的平台通知仍保留在消息中心，与 API-T12/T13 的未读口径一致
                .and(q -> q.eq(Message::getHidden, false)
                        .or().eq(Message::getHiddenReason, "NOTICE_REVOKED")));
        return count == null ? 0 : count.intValue();
    }

    private List<Todo> pendingOfFarm(Long farmId) {
        return todoMapper.selectList(Wrappers.<Todo>lambdaQuery()
                .eq(Todo::getFarmId, farmId)
                .eq(Todo::getStatus, TodoStatus.PENDING.name()));
    }


    private static boolean isDateOverdue(Todo todo, LocalDate today) {
        return TodoBusinessDay.isOpenOverdue(todo, today);
    }

    private List<Todo> doneToday(FarmContext.FarmMember member, LocalDate today) {
        List<Todo> done = todoMapper.selectList(Wrappers.<Todo>lambdaQuery()
                .eq(Todo::getFarmId, member.farmId())
                .eq(Todo::getStatus, TodoStatus.DONE.name())
                .eq(Todo::getDueDate, today));
        List<Todo> visible = new ArrayList<>();
        for (Todo todo : done) {
            if (todoScope.canView(todo, member)) {
                visible.add(todo);
            }
        }
        visible.sort(Comparator.comparing(Todo::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return visible;
    }

    private boolean isTodayList(Todo todo, LocalDate today, LocalDateTime now) {
        if (!TodoStatus.PENDING.name().equals(todo.getStatus())) {
            return false;
        }
        if (isOverdue(todo, now)) {
            return true;
        }
        if (today.equals(todo.getDueDate())) {
            return true;
        }
        if ("WARNING".equals(todo.getCategory())) {
            return true;
        }
        return "WITHDRAWAL_END".equals(todo.getTodoType()) && todo.getDueDate() != null
                && todo.getDueDate().isBefore(today);
    }

    private boolean isOverdue(Todo todo, LocalDateTime now) {
        return TodoStatus.PENDING.name().equals(todo.getStatus()) && todo.getOverdueAt() != null
                && now.isAfter(todo.getOverdueAt());
    }

    private static int overdueDays(Todo todo, LocalDate today) {
        if (todo.getDueDate() == null) {
            return 0;
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(todo.getDueDate(), today));
    }

    private String aiHint(List<Todo> pending, LocalDate today, LocalDateTime now) {
        // 按最早的逾期作业类待办生成推荐问
        return pending.stream()
                .filter(todo -> "WORK".equals(todo.getCategory()))
                .filter(todo -> isOverdue(todo, now))
                .min(Comparator.comparing(Todo::getSortTime))
                .map(todo -> todo.getTitle() + "已逾期，补做时要注意什么？")
                .orElse(null);
    }

    private static String initial(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name.substring(0, name.offsetByCodePoints(0, 1));
    }

    // ===================== HomeMetricProvider 聚合 =====================

    private int stockCount(Long farmId) {
        int value = 0;
        for (HomeMetricProvider provider : metricProviders) {
            value = Math.max(value, provider.stockCount(farmId));
        }
        return value;
    }

    private int batchCount(Long farmId) {
        int value = 0;
        for (HomeMetricProvider provider : metricProviders) {
            value = Math.max(value, provider.batchCount(farmId));
        }
        return value;
    }

    private BigDecimal penOccupancyRate(Long farmId) {
        for (HomeMetricProvider provider : metricProviders) {
            BigDecimal value = provider.penOccupancyRate(farmId);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** 所有者 / 场长：按本场真实栏舍数（含停用）判断；没有 provider 时按有栏舍处理 */
    private boolean hasPen(Long farmId) {
        for (HomeMetricProvider provider : metricProviders) {
            Integer count = provider.penCount(farmId);
            if (count != null) {
                return count > 0;
            }
        }
        return true;
    }

    private String countUnit(Long farmId) {
        for (HomeMetricProvider provider : metricProviders) {
            String value = provider.countUnit(farmId);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String countUnitText(Long farmId) {
        for (HomeMetricProvider provider : metricProviders) {
            String value = provider.countUnitText(farmId);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
