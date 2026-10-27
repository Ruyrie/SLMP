package com.zhimu.server.biz.herd.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.query.StockCategoryQuery;
import com.zhimu.server.biz.herd.query.StockPenQuery;
import com.zhimu.server.biz.herd.service.StockService;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.herd.vo.BatchListItemVO;
import com.zhimu.server.biz.herd.vo.CategoryStockVO;
import com.zhimu.server.biz.herd.vo.MyPenBatchVO;
import com.zhimu.server.biz.herd.vo.MyPenItemVO;
import com.zhimu.server.biz.herd.vo.MyPensVO;
import com.zhimu.server.biz.herd.vo.PenStockItemVO;
import com.zhimu.server.biz.herd.vo.ProductionOverviewVO;
import com.zhimu.server.biz.herd.vo.StockSummaryVO;
import com.zhimu.server.biz.herd.vo.TodoBriefVO;
import com.zhimu.server.biz.inspect.service.InspectionService;
import com.zhimu.server.biz.inspect.vo.InspectionSummaryVO;
import com.zhimu.server.biz.today.api.TodoQueryService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** API-L01–L05 查询实现。模块03（今日待办）、07（巡检异常）、08（休药中批次）已接入。 */
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final HerdSupport support;
    private final BatchMapper batchMapper;
    private final BatchPenMapper batchPenMapper;
    private final TodoQueryService todoQueryService;
    private final InspectionService inspectionService;

    @Override
    public ProductionOverviewVO overview() {
        FarmContext.FarmMember member = support.context();
        Farm farm = support.farm(member.farmId());
        String unit = support.farmUnit(member.farmId());

        ProductionOverviewVO vo = new ProductionOverviewVO();
        vo.setFarmId(member.farmId());
        vo.setFarmName(member.farmName());
        vo.setFarmStatus(member.farmStatus() == null ? null : member.farmStatus().name());
        vo.setToday(BeijingTime.format(BeijingTime.today()));
        boolean approved = member.farmStatus() == FarmStatus.NORMAL || member.farmStatus() == FarmStatus.DISABLED;
        if (!approved) {
            // 待审核、已驳回只返回 farm_id、farm_name、farm_status、today（App overview_pending）
            return vo;
        }
        List<Pen> pens = support.pensOfFarm(member.farmId());
        List<Batch> active = support.activeBatches(member.farmId());
        int stockTotal = active.stream().mapToInt(batch -> batch.getCurrentCount() == null ? 0 : batch.getCurrentCount()).sum();
        vo.setHasPen(!pens.isEmpty());
        vo.setStockTotal(stockTotal);

        // 模块03：今日待办数与预警类待办（生产提醒项）
        TodoQueryService.ProductionTodoSummary todoSummary = todoQueryService.productionSummary();
        // 模块07：今日巡检异常数（无异常时 0，不影响概况）
        int abnormalCount = 0;
        String abnormalPenName = null;
        InspectionSummaryVO inspection = inspectionService.summary(BeijingTime.format(BeijingTime.today()));
        if (inspection != null) {
            abnormalCount = inspection.getAbnormalCount();
            abnormalPenName = inspection.getFirstAbnormalPenName();
        }
        List<ProductionOverviewVO.ProductionReminderItemVO> reminders = new ArrayList<>();
        // 模块08：休药中批次排在最前（App L01：ZM2610-031 休药中 · 3 号育肥舍 · 10月29日可出栏 · 剩 10 天）
        Map<Long, java.time.LocalDate> releaseDates = support.withdrawalReleaseDates(member.farmId());
        int withdrawalCount = 0;
        for (Batch batch : active) {
            java.time.LocalDate release = releaseDates.get(batch.getId());
            Integer daysLeft = HerdSupport.withdrawalDaysLeft(release);
            if (daysLeft == null) {
                continue;
            }
            withdrawalCount++;
            ProductionOverviewVO.ProductionReminderItemVO item =
                    new ProductionOverviewVO.ProductionReminderItemVO();
            item.setReminderType("WITHDRAWAL");
            item.setIsWarning(true);
            item.setTitle(batch.getBatchNo() + " 休药中");
            String penName = batchPenName(batch);
            item.setSubtitle((penName == null ? "" : penName + " · ") + release.getMonthValue() + "月"
                    + release.getDayOfMonth() + "日可出栏");
            item.setTagText("剩 " + daysLeft + " 天");
            item.setBatchId(batch.getId());
            item.setRoute("D05");
            reminders.add(item);
        }
        for (TodoQueryService.WarningTodo warning : todoSummary.warnings()) {
            if ("WITHDRAWAL_END".equals(warning.todoType()) && warning.batchId() != null
                    && releaseDates.containsKey(warning.batchId())) {
                // 休药期到期待办与上面的休药提醒是同一批次，不重复显示
                continue;
            }
            ProductionOverviewVO.ProductionReminderItemVO item =
                    new ProductionOverviewVO.ProductionReminderItemVO();
            item.setReminderType(warning.todoType());
            item.setIsWarning(true);
            item.setTitle(warning.title());
            item.setSubtitle(warning.subtitle());
            item.setTagText(warning.tagText());
            item.setBatchId(warning.batchId());
            item.setTodoId(warning.todoId());
            item.setRoute(warning.route());
            reminders.add(item);
        }
        if (abnormalCount > 0) {
            ProductionOverviewVO.ProductionReminderItemVO item =
                    new ProductionOverviewVO.ProductionReminderItemVO();
            item.setReminderType("URGENT_DIAGNOSIS");
            item.setIsWarning(!Boolean.TRUE.equals(inspection.isAllDone()));
            item.setTitle("今日巡检发现异常");
            item.setSubtitle((abnormalPenName == null ? "" : abnormalPenName + " · ")
                    + abnormalCount + " 个栏舍待处理");
            item.setTagText("待处理");
            item.setRoute("I04");
            reminders.add(item);
        }
        vo.setTodayTodoCount(todoSummary.todayTodoCount());
        // 与未解除预警列表使用同一口径；巡检概况和休药提示属于生产提醒，不能额外累加。
        vo.setWarningCount(todoSummary.warnings().size());
        // 生产提醒总数 = 休药中批次 + 预警项（03/07）
        vo.setReminderTotal(reminders.size());
        vo.setReminders(reminders);
        ProductionOverviewVO.ProductionBadges badges = new ProductionOverviewVO.ProductionBadges();
        badges.setWithdrawal(withdrawalCount > 0);
        badges.setFeedLowStock(reminders.stream()
                .anyMatch(item -> "FEED_LOW_STOCK".equals(item.getReminderType())));
        vo.setBadges(badges);
        vo.setUnit(unit);
        vo.setUnitText(support.unitText(unit));
        return vo;
    }

    @Override
    public MyPensVO myPens() {
        FarmContext.FarmMember member = support.context();
        MyPensVO vo = new MyPensVO();
        vo.setFarmName(member.farmName());
        List<Pen> pens = support.pensVisibleTo(member);
        List<MyPenItemVO> items = new ArrayList<>();
        Map<Long, Batch> batches = new LinkedHashMap<>();
        for (Batch batch : support.activeBatches(member.farmId())) {
            batches.put(batch.getId(), batch);
        }
        String unit = support.farmUnit(member.farmId());
        Map<Long, TodoQueryService.PenTasks> penTasks = todoQueryService.penTasksToday();
        Map<Long, java.time.LocalDate> releaseDates = support.withdrawalReleaseDates(member.farmId());
        for (Pen pen : pens) {
            MyPenItemVO item = new MyPenItemVO();
            item.setPenId(pen.getId());
            item.setPenCode(pen.getPenCode());
            item.setPenName(pen.getPenName());
            item.setPenType(pen.getPenType());
            item.setPenTypeText(support.penTypeText(pen.getPenType()));
            item.setCapacity(pen.getCapacity());
            item.setStockCount(pen.getStockCount());
            item.setIsEmpty(pen.getStockCount() != null && pen.getStockCount() == 0);
            // 模块03：今日作业完成数、总数与未完成的作业类待办
            TodoQueryService.PenTasks tasks = penTasks == null ? null : penTasks.get(pen.getId());
            item.setTaskDone(tasks == null ? 0 : tasks.done());
            item.setTaskTotal(tasks == null ? 0 : tasks.total());
            item.setTasks(tasks == null ? List.of() : tasks.pending().stream().map(StockServiceImpl::taskBrief).toList());
            item.setUnitText(support.unitText(unit));
            List<MyPenBatchVO> penBatches = new ArrayList<>();
            for (BatchPen row : support.positivePensOfBatchByPen(member.farmId(), pen.getId())) {
                Batch batch = batches.get(row.getBatchId());
                if (batch == null) {
                    continue;
                }
                MyPenBatchVO batchVO = new MyPenBatchVO();
                batchVO.setBatchId(batch.getId());
                batchVO.setBatchNo(batch.getBatchNo());
                batchVO.setBreedTitle(support.breedTitle(batch));
                batchVO.setQtyInPen(row.getStockCount());
                batchVO.setDayAge(support.dayAge(batch));
                // 模块08：休药中与剩余天数
                Integer daysLeft = HerdSupport.withdrawalDaysLeft(releaseDates.get(batch.getId()));
                batchVO.setInWithdrawal(daysLeft != null);
                batchVO.setWithdrawalRemainingDays(daysLeft);
                batchVO.setStageName(support.stageName(batch));
                penBatches.add(batchVO);
            }
            item.setBatches(penBatches);
            items.add(item);
        }
        vo.setPenCount(items.size());
        vo.setPens(items);
        return vo;
    }

    @Override
    public StockSummaryVO summary() {
        FarmContext.FarmMember member = support.context();
        Long farmId = member.farmId();
        List<Pen> pens = support.pensOfFarm(farmId);
        List<Batch> active = support.activeBatches(farmId);
        List<Batch> all = support.batches(farmId, "ALL");
        Map<Long, CategoryInfo> categories = new LinkedHashMap<>();
        for (Batch batch : active) {
            categories.computeIfAbsent(batch.getCategoryId(), support::category);
        }

        int totalStock = active.stream().mapToInt(b -> b.getCurrentCount() == null ? 0 : b.getCurrentCount()).sum();
        int entryTotal = active.stream().mapToInt(b -> b.getEntryCount() == null ? 0 : b.getEntryCount()).sum();
        // 死淘累计：全部批次的 death_count 之和（含已结束批次的历史死淘，与 App stock_summary 的 19 对齐）
        int deathTotal = all.stream().mapToInt(b -> b.getDeathCount() == null ? 0 : b.getDeathCount()).sum();
        int capacityTotal = 0;
        int commercialPenCount = 0;
        for (Pen pen : pens) {
            if (!"ENABLED".equals(pen.getStatus())) {
                continue;
            }
            CategoryInfo category = support.category(pen.getCategoryId());
            if (category != null && Boolean.TRUE.equals(category.isBreeding())) {
                continue;
            }
            capacityTotal += pen.getCapacity() == null ? 0 : pen.getCapacity();
            commercialPenCount++;
        }

        StockSummaryVO vo = new StockSummaryVO();
        vo.setTotalStock(totalStock);
        vo.setActiveBatchCount(active.size());
        // 模块08：休药中批次
        Map<Long, java.time.LocalDate> releaseDates = support.withdrawalReleaseDates(farmId);
        vo.setWithdrawalBatchCount((int) active.stream().filter(b -> releaseDates.containsKey(b.getId())).count());
        vo.setEndedBatchCount((int) support.countBatches(farmId, "ENDED"));
        vo.setEntryTotal(entryTotal);
        vo.setDeathTotal(deathTotal);
        vo.setCapacityTotal(capacityTotal);
        vo.setCommercialPenCount(commercialPenCount);
        vo.setMortalityRate(HerdSupport.mortality(deathTotal, entryTotal));
        vo.setOccupancyRate(HerdSupport.rate(totalStock, capacityTotal));
        String unit = support.farmUnit(farmId);
        vo.setUnit(unit);
        vo.setUnitText(support.unitText(unit));

        Map<Long, StockSummaryVO.CategoryShareVO> shares = new LinkedHashMap<>();
        Map<Long, Map<String, Integer>> stageGroups = new HashMap<>();
        for (Batch batch : active) {
            CategoryInfo category = categories.get(batch.getCategoryId());
            StockSummaryVO.CategoryShareVO share = shares.computeIfAbsent(batch.getCategoryId(), key -> {
                StockSummaryVO.CategoryShareVO item = new StockSummaryVO.CategoryShareVO();
                item.setCategoryId(key);
                item.setCategoryName(category == null ? "" : category.name());
                item.setStock(0);
                item.setBatchCount(0);
                return item;
            });
            int count = batch.getCurrentCount() == null ? 0 : batch.getCurrentCount();
            share.setStock(share.getStock() + count);
            share.setBatchCount(share.getBatchCount() + 1);
            stageGroups.computeIfAbsent(batch.getCategoryId(), key -> new LinkedHashMap<>())
                    .merge(support.stageShortName(batch), count, Integer::sum);
        }
        for (StockSummaryVO.CategoryShareVO share : shares.values()) {
            List<StockSummaryVO.StageGroupItem> groups = new ArrayList<>();
            stageGroups.getOrDefault(share.getCategoryId(), Map.of()).forEach((name, stock) -> {
                StockSummaryVO.StageGroupItem group = new StockSummaryVO.StageGroupItem();
                group.setStageShortName(name);
                group.setStock(stock);
                groups.add(group);
            });
            share.setStageGroups(groups);
        }
        vo.setCategories(new ArrayList<>(shares.values()));
        return vo;
    }

    @Override
    public PageVO<PenStockItemVO> pens(StockPenQuery query) {
        FarmContext.FarmMember member = support.context();
        Long farmId = member.farmId();
        String keyword = trim(query.getKeyword());
        Map<Long, Batch> activeById = new LinkedHashMap<>();
        for (Batch batch : support.activeBatches(farmId)) {
            activeById.put(batch.getId(), batch);
        }
        Map<Long, List<BatchPen>> penStocks = positivePens(farmId, activeById.keySet());
        Map<Long, List<Batch>> batchesOfPen = new HashMap<>();
        for (BatchPen row : penStocks.values().stream().flatMap(List::stream).toList()) {
            Batch batch = activeById.get(row.getBatchId());
            if (batch != null) {
                batchesOfPen.computeIfAbsent(row.getPenId(), key -> new ArrayList<>()).add(batch);
            }
        }
        Map<Long, Batch> endedByPen = lastEndedBatches(farmId);
        Map<Long, java.time.LocalDate> releaseDates = support.withdrawalReleaseDates(farmId);

        List<PenStockItemVO> all = new ArrayList<>();
        for (Pen pen : support.pensOfFarm(farmId)) {
            List<Batch> inPen = batchesOfPen.getOrDefault(pen.getId(), List.of());
            if (keyword != null && !matchesPen(pen, keyword, inPen)) {
                continue;
            }
            PenStockItemVO item = penItem(pen, inPen, endedByPen.get(pen.getId()));
            // 模块08：栏内有休药中批次
            item.setInWithdrawal(inPen.stream().anyMatch(b -> releaseDates.containsKey(b.getId())));
            all.add(item);
        }
        boolean disabledLast = true;
        Comparator<PenStockItemVO> comparator;
        String sort = query.getSort() == null ? "CODE" : query.getSort().toUpperCase();
        switch (sort) {
            case "STOCK" -> comparator = Comparator.comparing(
                    (PenStockItemVO item) -> item.getStockCount() == null ? 0 : item.getStockCount()).reversed();
            case "OCCUPANCY" -> comparator = Comparator.comparing(
                    (PenStockItemVO item) -> item.getOccupancyRate() == null ? BigDecimal.ZERO : item.getOccupancyRate())
                    .reversed();
            default -> comparator = Comparator.comparing(PenStockItemVO::getPenCode,
                    Comparator.nullsLast(String::compareTo));
        }
        Comparator<PenStockItemVO> finalComparator = Comparator
                .comparing((PenStockItemVO item) -> disabledLast && PenStockItemVO.STATUS_DISABLED.equals(item.getDisplayStatus()))
                .thenComparing(comparator);
        all.sort(finalComparator);

        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageVO.of(new ArrayList<>(all.subList(from, to)), all.size(), page, size);
    }

    @Override
    public List<CategoryStockVO> categories(StockCategoryQuery query) {
        FarmContext.FarmMember member = support.context();
        Long farmId = member.farmId();
        String keyword = trim(query.getKeyword());
        Map<Long, Batch> activeById = new LinkedHashMap<>();
        for (Batch batch : support.activeBatches(farmId)) {
            activeById.put(batch.getId(), batch);
        }
        Map<Long, List<BatchPen>> penStocks = positivePens(farmId, activeById.keySet());
        Map<Long, List<BatchPen>> rowsByBatch = new HashMap<>();
        for (BatchPen row : penStocks.values().stream().flatMap(List::stream).toList()) {
            rowsByBatch.computeIfAbsent(row.getBatchId(), key -> new ArrayList<>()).add(row);
        }
        Map<Long, Pen> penMap = support.penMap(penStocks.values().stream().flatMap(List::stream)
                .map(BatchPen::getPenId).collect(java.util.stream.Collectors.toSet()));
        String unit = support.farmUnit(farmId);

        Map<Long, CategoryStockVO> result = new LinkedHashMap<>();
        Map<Long, List<Batch>> batchesByCategory = new LinkedHashMap<>();
        for (Batch batch : activeById.values()) {
            batchesByCategory.computeIfAbsent(batch.getCategoryId(), key -> new ArrayList<>()).add(batch);
        }
        for (Map.Entry<Long, List<Batch>> entry : batchesByCategory.entrySet()) {
            CategoryInfo category = support.category(entry.getKey());
            String categoryName = category == null ? "" : category.name();
            Set<String> penNames = new LinkedHashSet<>();
            int stock = 0;
            int death = 0;
            int entryCount = 0;
            boolean keywordHit = keyword == null || contains(categoryName, keyword);
            List<BatchListItemVO> items = new ArrayList<>();
            for (Batch batch : entry.getValue()) {
                List<BatchPen> rows = rowsByBatch.getOrDefault(batch.getId(), List.of());
                for (BatchPen row : rows) {
                    Pen pen = penMap.get(row.getPenId());
                    if (pen != null) {
                        penNames.add(pen.getPenName());
                    }
                }
                if (!keywordHit) {
                    for (BatchPen row : rows) {
                        Pen pen = penMap.get(row.getPenId());
                        if (pen != null && contains(pen.getPenName(), keyword)) {
                            keywordHit = true;
                            break;
                        }
                    }
                }
                stock += batch.getCurrentCount() == null ? 0 : batch.getCurrentCount();
                death += batch.getDeathCount() == null ? 0 : batch.getDeathCount();
                entryCount += batch.getEntryCount() == null ? 0 : batch.getEntryCount();
                items.add(support.batchItem(batch, rows, penMap));
            }
            if (!keywordHit) {
                continue;
            }
            CategoryStockVO vo = new CategoryStockVO();
            vo.setCategoryId(entry.getKey());
            vo.setCategoryName(categoryName);
            vo.setIsBreeding(category != null && Boolean.TRUE.equals(category.isBreeding()));
            vo.setStock(stock);
            vo.setBatchCount(items.size());
            vo.setMortalityRate(HerdSupport.mortality(death, entryCount));
            vo.setPenNames(new ArrayList<>(penNames));
            vo.setBatches(items);
            vo.setUnitText(support.unitText(unit));
            result.put(entry.getKey(), vo);
        }
        return new ArrayList<>(result.values());
    }

    private static TodoBriefVO taskBrief(TodoQueryService.PenTask task) {
        TodoBriefVO vo = new TodoBriefVO();
        vo.setTodoId(task.todoId());
        vo.setTodoType(task.todoType());
        vo.setTitle(task.title());
        vo.setTimeText(task.timeText());
        vo.setStatus(task.status());
        vo.setStatusText(task.statusText());
        vo.setRoute(task.route());
        vo.setTaskId(task.taskId());
        vo.setBatchId(task.batchId());
        vo.setPenId(task.penId());
        return vo;
    }

    /** 批次当前在栏的第一个栏舍名（没有在栏记录时用入栏栏舍） */
    private String batchPenName(Batch batch) {
        List<BatchPen> rows = support.positivePensOfBatch(batch.getId());
        Long penId = rows == null || rows.isEmpty() ? batch.getEntryPenId() : rows.get(0).getPenId();
        if (penId == null) {
            return null;
        }
        return support.pensByIds(List.of(penId)).stream().findFirst().map(Pen::getPenName).orElse(null);
    }

    private Map<Long, List<BatchPen>> positivePens(Long farmId, Set<Long> batchIds) {
        Map<Long, List<BatchPen>> result = new LinkedHashMap<>();
        if (batchIds.isEmpty()) {
            return result;
        }
        List<BatchPen> rows = batchPenMapper.selectList(Wrappers.<BatchPen>lambdaQuery()
                .eq(BatchPen::getFarmId, farmId)
                .in(BatchPen::getBatchId, batchIds)
                .gt(BatchPen::getStockCount, 0)
                .orderByAsc(BatchPen::getPenId));
        for (BatchPen row : rows) {
            result.computeIfAbsent(row.getPenId(), key -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private Map<Long, Batch> lastEndedBatches(Long farmId) {
        Map<Long, Batch> result = new HashMap<>();
        List<Batch> ended = batchMapper.selectList(Wrappers.<Batch>lambdaQuery()
                .eq(Batch::getFarmId, farmId)
                .eq(Batch::getStatus, "ENDED")
                .isNotNull(Batch::getEndDate)
                .orderByDesc(Batch::getEndDate)
                .orderByDesc(Batch::getId));
        for (Batch batch : ended) {
            result.putIfAbsent(batch.getEntryPenId(), batch);
        }
        return result;
    }

    private PenStockItemVO penItem(Pen pen, List<Batch> batches, Batch lastEnded) {
        CategoryInfo category = support.category(pen.getCategoryId());
        boolean breeding = category != null && Boolean.TRUE.equals(category.isBreeding());
        PenStockItemVO item = new PenStockItemVO();
        item.setPenId(pen.getId());
        item.setPenCode(pen.getPenCode());
        item.setPenName(pen.getPenName());
        item.setPenType(pen.getPenType());
        item.setPenTypeText(support.penTypeText(pen.getPenType()));
        item.setCategoryName(category == null ? "" : category.name());
        item.setCapacity(pen.getCapacity());
        item.setStockCount(pen.getStockCount());
        item.setIsBreedingPen(breeding);
        // 休药中由调用方按模块08 结果覆盖
        item.setInWithdrawal(false);
        item.setBatchCount(batches.size());
        String displayStatus = "DISABLED".equals(pen.getStatus()) ? PenStockItemVO.STATUS_DISABLED
                : (breeding ? PenStockItemVO.STATUS_IN_USE
                : (pen.getStockCount() != null && pen.getStockCount() == 0
                ? PenStockItemVO.STATUS_EMPTY : PenStockItemVO.STATUS_IN_USE));
        item.setDisplayStatus(displayStatus);
        item.setOccupancyRate(HerdSupport.rate(pen.getStockCount(), pen.getCapacity()));
        int death = 0;
        int entry = 0;
        for (Batch batch : batches) {
            death += batch.getDeathCount() == null ? 0 : batch.getDeathCount();
            entry += batch.getEntryCount() == null ? 0 : batch.getEntryCount();
        }
        item.setMortalityRate(HerdSupport.mortality(death, entry));
        item.setUnitText(support.unitText(support.farmUnit(pen.getFarmId())));
        if (!batches.isEmpty()) {
            Batch first = batches.get(0);
            PenStockItemVO.PenFirstBatchItem firstBatch = new PenStockItemVO.PenFirstBatchItem();
            firstBatch.setBatchId(first.getId());
            firstBatch.setBatchNo(first.getBatchNo());
            firstBatch.setDayAge(support.dayAge(first));
            firstBatch.setStageName(support.stageName(first));
            item.setFirstBatch(firstBatch);
        }
        if (batches.isEmpty() && !breeding && lastEnded != null) {
            PenStockItemVO.EndedBatchItem ended = new PenStockItemVO.EndedBatchItem();
            ended.setBatchNo(lastEnded.getBatchNo());
            ended.setEndDate(BeijingTime.format(lastEnded.getEndDate()));
            ended.setEndType(lastEnded.getEndType());
            item.setLastEndedBatch(ended);
        }
        return item;
    }

    private static boolean matchesPen(Pen pen, String keyword, List<Batch> batches) {
        if (contains(pen.getPenName(), keyword) || contains(pen.getPenCode(), keyword)) {
            return true;
        }
        // App 搜索框提示“输入栏舍名称或批次号”；批次号命中也返回该栏舍
        String key = keyword.toUpperCase();
        for (Batch batch : batches) {
            if (batch.getBatchNo() != null && batch.getBatchNo().toUpperCase().contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword);
    }
}
