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

/** API-L01–L05 查询实现。模块03/05–10 的指标先返回 0 或空，注释标明待接入模块。 */
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final HerdSupport support;
    private final BatchMapper batchMapper;
    private final BatchPenMapper batchPenMapper;

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
        // 待模块03接入：今日生产类待办数
        vo.setTodayTodoCount(0);
        // 待模块03/07/08接入：巡检异常、紧急诊断、库存偏低等预警数
        vo.setWarningCount(0);
        // 待模块08接入：休药中批次；当前没有用药数据，生产提醒为空
        vo.setReminderTotal(0);
        vo.setReminders(List.of());
        ProductionOverviewVO.ProductionBadges badges = new ProductionOverviewVO.ProductionBadges();
        badges.setWithdrawal(false);
        badges.setFeedLowStock(false);
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
            // 待模块03接入：今日作业完成数与作业列表
            item.setTaskDone(0);
            item.setTaskTotal(0);
            item.setTasks(List.of());
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
                // 待模块08接入：休药中与剩余天数
                batchVO.setInWithdrawal(false);
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
        // 待模块08接入：休药中批次
        vo.setWithdrawalBatchCount(0);
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

        List<PenStockItemVO> all = new ArrayList<>();
        for (Pen pen : support.pensOfFarm(farmId)) {
            List<Batch> inPen = batchesOfPen.getOrDefault(pen.getId(), List.of());
            if (keyword != null && !matchesPen(pen, keyword, inPen)) {
                continue;
            }
            all.add(penItem(pen, inPen, endedByPen.get(pen.getId())));
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
                if (!keywordHit && contains(batch.getBatchNo(), keyword)) {
                    keywordHit = true;
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
        // 待模块08接入：休药中
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
        return batches.stream().anyMatch(batch -> contains(batch.getBatchNo(), keyword));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword);
    }
}
