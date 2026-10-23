package com.zhimu.server.biz.herd.service.impl;

import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.today.api.HomeMetricProvider;
import com.zhimu.server.common.context.FarmContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块04 向模块03 首页提供的聚合指标（API-T01）：存栏、在养批次数、栏舍占用率、计数单位，
 * 以及员工首页“我负责的栏舍”的容量/类型/在栏批次。模块04 未合入时首页按 0/空兜底，合入后自动生效。
 */
@Service
@RequiredArgsConstructor
public class HerdHomeMetricProvider implements HomeMetricProvider {

    private final HerdSupport support;

    @Override
    public String moduleName() {
        return "herd";
    }

    @Override
    public int stockCount(Long farmId) {
        int total = 0;
        for (Batch batch : support.activeBatches(farmId)) {
            total += batch.getCurrentCount() == null ? 0 : batch.getCurrentCount();
        }
        return total;
    }

    @Override
    public int batchCount(Long farmId) {
        return support.activeBatches(farmId).size();
    }

    @Override
    public BigDecimal penOccupancyRate(Long farmId) {
        int stock = 0;
        int capacity = 0;
        for (Pen pen : support.pensOfFarm(farmId)) {
            if (!"ENABLED".equals(pen.getStatus())) {
                continue;
            }
            CategoryInfo category = support.category(pen.getCategoryId());
            if (category != null && Boolean.TRUE.equals(category.isBreeding())) {
                continue;
            }
            stock += pen.getStockCount() == null ? 0 : pen.getStockCount();
            capacity += pen.getCapacity() == null ? 0 : pen.getCapacity();
        }
        return HerdSupport.rate(stock, capacity);
    }

    @Override
    public String countUnit(Long farmId) {
        return support.farmUnit(farmId);
    }

    @Override
    public String countUnitText(Long farmId) {
        return support.unitText(support.farmUnit(farmId));
    }

    @Override
    public List<PenMetric> pens(FarmContext.FarmMember member) {
        List<Pen> pens = support.pensVisibleTo(member);
        Map<Long, String> penNames = new HashMap<>();
        for (Pen pen : support.pensOfFarm(member.farmId())) {
            penNames.put(pen.getId(), pen.getPenName());
        }
        Map<Long, List<BatchMetric>> batchesByPen = batchesOfPens(member.farmId(), penNames);
        List<PenMetric> result = new ArrayList<>();
        for (Pen pen : pens) {
            result.add(new PenMetric(pen.getId(), pen.getPenName(), support.penTypeText(pen.getPenType()),
                    pen.getCapacity(), batchesByPen.getOrDefault(pen.getId(), List.of())));
        }
        return result;
    }

    /** 在栏批次按栏舍分布；head_count 取该批次在该栏舍的在栏数 */
    private Map<Long, List<BatchMetric>> batchesOfPens(Long farmId, Map<Long, String> penNames) {
        Map<Long, List<BatchMetric>> result = new HashMap<>();
        for (Batch batch : support.activeBatches(farmId)) {
            for (BatchPen row : support.positivePensOfBatch(batch.getId())) {
                if (row.getPenId() == null) {
                    continue;
                }
                BatchMetric metric = new BatchMetric(batch.getId(), batch.getBatchNo(),
                        support.breedTitle(batch), support.dayAge(batch), row.getStockCount(),
                        penNames.get(row.getPenId()), support.stageName(batch));
                result.computeIfAbsent(row.getPenId(), key -> new ArrayList<>()).add(metric);
            }
        }
        return result;
    }
}
