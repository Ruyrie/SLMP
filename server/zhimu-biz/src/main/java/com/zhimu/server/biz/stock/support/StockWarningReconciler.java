package com.zhimu.server.biz.stock.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 库存状态与预警待办同步；物资行锁串行化同一种物资的预警周期。 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockWarningReconciler {
    private final FeedMapper feedMapper;
    private final DrugMapper drugMapper;
    private final DrugLotMapper lotMapper;
    private final DrugUsables usables;
    private final TodoMapper todoMapper;
    // 延迟获取写入端，避免“待办查询 → 库存校正 → 待办写入”的构造依赖环。
    private final ObjectProvider<TodoSourceWriteService> writerProvider;

    /** 读取首页/待办时校正历史遗留预警，同时涵盖阈值修改和药品自然过期。 */
    public void reconcileFarm(Long farmId) {
        if (farmId == null) return;
        for (Feed feed : feedMapper.selectList(Wrappers.<Feed>lambdaQuery()
                .eq(Feed::getFarmId, farmId).orderByAsc(Feed::getId))) {
            reconcileFeed(farmId, feed.getId());
        }
        for (Drug drug : drugMapper.selectList(Wrappers.<Drug>lambdaQuery()
                .eq(Drug::getFarmId, farmId).orderByAsc(Drug::getId))) {
            reconcileDrug(farmId, drug.getId());
        }
    }

    public void reconcileFeed(Long farmId, Long feedId) {
        Feed feed = feedMapper.selectOne(Wrappers.<Feed>lambdaQuery()
                .eq(Feed::getFarmId, farmId).eq(Feed::getId, feedId).last("FOR UPDATE"));
        if (feed == null) return;
        FeedStats.View view = FeedStats.of(feed);
        String detail = "当前结存 " + StockQty.plain(feed.getStockQty()) + " 千克";
        if (StockCodes.LOW_BELOW_DAYS.equals(view.reason())) {
            detail += "，可用 " + view.remainingDays() + " 天，低于预警天数 "
                    + (feed.getWarnDays() == null ? 7 : feed.getWarnDays()) + " 天";
        } else if (feed.getMinStock() != null) {
            detail += "，库存下限 " + StockQty.plain(feed.getMinStock()) + " 千克";
        }
        LocalDateTime at = reconcile(farmId, "FEED_LOW_STOCK", "FEED", feedId, feed.getName(),
                detail, view.lowStock(), Boolean.TRUE.equals(feed.getIsLowStock()), feed.getLowStockAt());
        feedMapper.update(null, Wrappers.<Feed>lambdaUpdate().eq(Feed::getId, feedId)
                .eq(Feed::getFarmId, farmId).set(Feed::getIsLowStock, view.lowStock())
                .set(Feed::getLowStockReason, view.reason()).set(Feed::getLowStockAt, at));
    }

    /** 批号记账之前先锁药品，多个批号补货也按同一药品串行核算可用结存。 */
    public void lockDrug(Long farmId, Long drugId) {
        drugMapper.selectOne(Wrappers.<Drug>lambdaQuery().eq(Drug::getFarmId, farmId)
                .eq(Drug::getId, drugId).last("FOR UPDATE"));
    }

    public void reconcileDrug(Long farmId, Long drugId) {
        Drug drug = drugMapper.selectOne(Wrappers.<Drug>lambdaQuery().eq(Drug::getFarmId, farmId)
                .eq(Drug::getId, drugId).last("FOR UPDATE"));
        if (drug == null) return;
        List<DrugLot> lots = lotMapper.selectList(Wrappers.<DrugLot>lambdaQuery()
                .eq(DrugLot::getFarmId, farmId).eq(DrugLot::getDrugId, drugId)
                .orderByAsc(DrugLot::getId).last("FOR UPDATE"));
        BigDecimal usable = usables.usableOf(lots);
        boolean low = drug.getMinStock() != null && usable.compareTo(drug.getMinStock()) < 0;
        String unit = StockCodes.unitText(drug.getUnit());
        String detail = "可用结存 " + StockQty.plain(usable) + " " + unit
                + (drug.getMinStock() == null ? "" : "，库存下限 " + StockQty.plain(drug.getMinStock()) + " " + unit);
        LocalDateTime at = reconcile(farmId, "DRUG_LOW_STOCK", "DRUG", drugId, drug.getName(),
                detail, low, Boolean.TRUE.equals(drug.getIsLowStock()), drug.getLowStockAt());
        drugMapper.update(null, Wrappers.<Drug>lambdaUpdate().eq(Drug::getId, drugId)
                .eq(Drug::getFarmId, farmId).set(Drug::getIsLowStock, low).set(Drug::getLowStockAt, at));
    }

    private LocalDateTime reconcile(Long farmId, String type, String bizType, Long id, String name,
                                    String detail, boolean low, boolean wasLow, LocalDateTime previousAt) {
        List<Todo> existing = todoMapper.selectList(Wrappers.<Todo>lambdaQuery()
                .eq(Todo::getFarmId, farmId).eq(Todo::getTodoType, type).eq(Todo::getBizId, id)
                .orderByDesc(Todo::getId).last("FOR UPDATE"));
        TodoSourceWriteService writer = writerProvider.getObject();
        List<Todo> pending = existing.stream().filter(t -> "PENDING".equals(t.getStatus())).toList();
        if (!low) {
            for (Todo todo : pending) {
                writer.completeFromSource(farmId, type, todo.getDedupKey(), null, null, 0L);
            }
            return null;
        }
        LocalDateTime at = previousAt == null ? BeijingTime.now() : previousAt;
        // 本次低库存周期被用户忽略后不反复生成；恢复正常再降到阈值以下会开始新周期。
        if (pending.isEmpty() && wasLow && previousAt != null && !existing.isEmpty()
                && "IGNORED".equals(existing.get(0).getStatus())) return at;
        // 旧数据若有重复未解除预警，只保留同一物资最新的一条。
        for (int i = 1; i < pending.size(); i++) {
            writer.completeFromSource(farmId, type, pending.get(i).getDedupKey(), null, null, 0L);
        }
        String key = pending.isEmpty() ? "STOCK:" + id + ":" + UUID.randomUUID() : pending.get(0).getDedupKey();
        writer.upsertFromSource(farmId, List.of(TodoSource.TodoDraft.builder()
                .todoType(type).category("WARNING").bizType(bizType).bizId(id).dedupKey(key)
                .title(name + "库存不足").contentText(detail).statusNote("库存不足")
                .detailLabel("库存情况").detailValue(detail).receiverScope("MANAGERS")
                .dueDate(pending.isEmpty() || pending.get(0).getDueDate() == null
                        ? at.toLocalDate() : pending.get(0).getDueDate())
                .timeType("ALL_DAY").build()));
        return at;
    }
}
