package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.job.mapper.InspectionJobMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.support.InspectRules;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 巡检任务（每栏舍每天一行的占位行，技术准备文档 2.3.2）生成器：
 * <ul>
 *     <li>为有在栏批次的栏舍生成当天 PENDING 占位行；已有任何状态的行（含已登记、已取消）都跳过；</li>
 *     <li>今天以前仍未登记的占位行标记为 MISSED（漏巡，可在补录窗口内补录）；</li>
 *     <li>停机跨日时补齐最近一次有巡检行之后漏掉的天数（最多到补录窗口），这些天直接生成 MISSED 行。</li>
 * </ul>
 * 用户新建的养殖场、栏舍、批次同样生成；唯一索引 (farm_id, pen_id, inspect_date) 保证不会重复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InspectionTaskGenerator {

    private final InspectionMapper inspectionMapper;
    private final InspectionJobMapper jobMapper;

    public List<Long> activeFarmIds() {
        List<Long> ids = TenantSkip.call(jobMapper::selectActiveFarmIds);
        return ids == null ? List.of() : ids;
    }

    /** 今天以前仍未登记的占位行标记为漏巡；返回条数 */
    public int markMissed(Long farmId, LocalDate today) {
        return TenantSkip.call(() -> jobMapper.markMissed(farmId, today, BeijingTime.now()));
    }

    /** 补齐指定日期的占位行：今天及以后为 PENDING，以前为 MISSED；只算当天已入栏的批次所在栏舍。返回新建行数 */
    public int ensureDay(Long farmId, LocalDate date, LocalDate today) {
        return TenantSkip.call(() -> {
            Map<Long, List<StockedPenRow>> pens = new LinkedHashMap<>();
            List<StockedPenRow> rows = jobMapper.selectStockedPens(farmId);
            for (StockedPenRow row : rows == null ? List.<StockedPenRow>of() : rows) {
                if (row.getEntryDate() == null || !row.getEntryDate().isAfter(date)) {
                    pens.computeIfAbsent(row.getPenId(), id -> new ArrayList<>()).add(row);
                }
            }
            if (pens.isEmpty()) {
                return 0;
            }
            Set<Long> existing = new HashSet<>();
            List<Inspection> day = inspectionMapper.selectDay(farmId, date);
            if (day != null) {
                for (Inspection inspection : day) {
                    existing.add(inspection.getPenId());
                }
            }
            int created = 0;
            for (Map.Entry<Long, List<StockedPenRow>> entry : pens.entrySet()) {
                if (existing.contains(entry.getKey())) {
                    continue;
                }
                List<StockedPenRow> batches = entry.getValue();
                Inspection inspection = placeholder(farmId, entry.getKey(),
                        batches.size() == 1 ? batches.get(0).getBatchId() : null, date,
                        date.isBefore(today) ? InspectRules.MISSED : InspectRules.PENDING);
                try {
                    inspectionMapper.insert(inspection);
                    created++;
                } catch (DuplicateKeyException ignored) {
                    // 同一时刻用户已登记或其他实例已生成：唯一索引兜底
                }
            }
            return created;
        });
    }

    /**
     * 启动补齐：从最近一次有巡检行的日期之后到今天，最多补到补录窗口（前 3 天）；
     * 从未生成过巡检行的养殖场只生成今天。返回新建行数。
     */
    public int backfill(Long farmId, LocalDate today) {
        LocalDate last = TenantSkip.call(() -> jobMapper.selectLastInspectDate(farmId, today));
        LocalDate from = today;
        if (last != null) {
            LocalDate windowStart = today.minusDays(InspectRules.BACKFILL_DAYS);
            from = last.plusDays(1).isAfter(windowStart) ? last.plusDays(1) : windowStart;
        }
        int created = 0;
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            created += ensureDay(farmId, day, today);
        }
        return created;
    }

    static Inspection placeholder(Long farmId, Long penId, Long batchId, LocalDate date, String status) {
        Inspection inspection = new Inspection();
        inspection.setFarmId(farmId);
        inspection.setPenId(penId);
        inspection.setBatchId(batchId);
        inspection.setInspectDate(date);
        inspection.setInspectStatus(status);
        inspection.setIsOverdue(false);
        inspection.setIsBackfill(false);
        inspection.setIsAbnormal(false);
        inspection.setIsManualMarked(false);
        inspection.setVersion(0);
        inspection.setDeleted(false);
        inspection.setCreatedBy(0L);
        return inspection;
    }
}
