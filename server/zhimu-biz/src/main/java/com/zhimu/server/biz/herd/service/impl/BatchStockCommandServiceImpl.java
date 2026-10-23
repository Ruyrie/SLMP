package com.zhimu.server.biz.herd.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.herd.api.BatchStockCommandService;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.BatchTimeline;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.BatchTimelineMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.support.HerdErrors;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 出栏存栏端口实现（模块10 调用）：批次 current_count / sold_count、t_batch_pen.stock_count、t_pen.stock_count
 * 三处同步扣减；出栏单作废时按流水回滚。写入 SALE 流水与时间线，之后该批次的入栏记录按“已有后续记录”处理。
 * <p>
 * 幂等键：（来源业务类型、来源业务 ID、批次）。同一出栏单重复提交不会重复扣减；作废回滚同样幂等。
 * 调用方需在请求上下文内（FarmContext 已按 X-Farm-Id 建立）。
 */
@Service
@RequiredArgsConstructor
public class BatchStockCommandServiceImpl implements BatchStockCommandService {

    private static final JsonMapper JSON = ZhimuJackson.newPlainMapper();
    private static final String EVENT_SALE = "SALE";
    private static final String STATUS_EFFECTIVE = "EFFECTIVE";
    private static final String STATUS_VOIDED = "VOIDED";

    private final BatchMapper batchMapper;
    private final BatchEventMapper eventMapper;
    private final BatchPenMapper batchPenMapper;
    private final PenMapper penMapper;
    private final BatchTimelineMapper timelineMapper;
    private final HerdSupport support;

    @Override
    @Transactional
    public BatchSaleResult sale(SaleStockCommand command) {
        validate(command);
        Batch batch = loadBatch(command.batchId());
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw HerdErrors.batchEnded();
        }
        LocalDate saleDate = command.saleDate() == null ? BeijingTime.today() : command.saleDate();
        if (saleDate.isAfter(BeijingTime.today())) {
            throw HerdErrors.fieldInvalid("sale_date", "出栏日期不能晚于今天");
        }
        if (batch.getEntryDate() != null && saleDate.isBefore(batch.getEntryDate())) {
            throw HerdErrors.fieldInvalid("sale_date",
                    "出栏日期不能早于入栏日期 " + BeijingTime.format(batch.getEntryDate()));
        }
        List<BatchEvent> applied = activeSaleEvents(batch.getId(), command.bizType(), command.bizId());
        if (!applied.isEmpty()) {
            // 同一出栏单 + 批次已扣减：直接返回上次结果，不重复扣存栏
            return resultOf(batch, applied);
        }

        List<BatchPen> rows = support.positivePensOfBatch(batch.getId());
        if (rows.isEmpty()) {
            throw HerdErrors.countExceeded(null, batch.getBatchNo(), 0, null);
        }
        List<Allocation> allocations = allocate(command, rows);
        int total = allocations.stream().mapToInt(Allocation::quantity).sum();
        int current = value(batch.getCurrentCount());
        if (total > current) {
            Allocation first = allocations.get(0);
            throw HerdErrors.countExceeded(first.penId(), first.penName(), current, null);
        }
        Long operatorId = command.operatorUserId() != null
                ? command.operatorUserId() : com.zhimu.server.common.context.UserContext.userId();
        LocalDateTime now = BeijingTime.now();
        List<BatchPenStock> penStocks = new ArrayList<>();
        for (Allocation allocation : allocations) {
            if (batchPenMapper.deduct(batch.getId(), allocation.penId(), allocation.quantity(), saleDate, now) == 0) {
                throw HerdErrors.countExceeded(allocation.penId(), allocation.penName(),
                        latestBatchPenStock(batch.getId(), allocation.penId()), null);
            }
            if (penMapper.addStock(allocation.penId(), -allocation.quantity(), now) == 0) {
                throw HerdErrors.countExceeded(allocation.penId(), allocation.penName(),
                        penStock(allocation.penId()), null);
            }
            penStocks.add(new BatchPenStock(allocation.penId(), allocation.penName(), allocation.quantity(),
                    allocation.stock() - allocation.quantity()));
        }

        boolean ended = current - total == 0;
        updateBatchCounters(batch, total, ended, saleDate, now, allocations.get(0));
        List<Long> eventIds = new ArrayList<>();
        for (Allocation allocation : allocations) {
            BatchEvent event = new BatchEvent();
            event.setFarmId(batch.getFarmId());
            event.setBatchId(batch.getId());
            event.setEventType(EVENT_SALE);
            event.setEventDate(saleDate);
            event.setPenId(allocation.penId());
            event.setQuantity(allocation.quantity());
            event.setExecutorUserId(operatorId);
            event.setSourceBizType(command.bizType().name());
            event.setSourceBizId(command.bizId());
            event.setCapacityConfirmed(false);
            event.setOverCapacityCount(0);
            event.setStatus(STATUS_EFFECTIVE);
            event.setClientRequestId(null);
            event.setVersion(0);
            event.setDeleted(false);
            eventMapper.insert(event);
            eventIds.add(event.getId());
        }
        writeTimeline(batch, allocations, total, saleDate, command, operatorId, now);
        return new BatchSaleResult(batch.getId(), ended ? "ENDED" : "ACTIVE", total, current - total,
                value(batch.getSoldCount()), ended, eventIds, penStocks);
    }

    @Override
    @Transactional
    public void restoreSale(BizType bizType, Long bizId) {
        if (bizType == null || bizId == null) {
            return;
        }
        List<BatchEvent> events = TenantSkip.call(() -> eventMapper.selectList(Wrappers.<BatchEvent>lambdaQuery()
                .eq(BatchEvent::getEventType, EVENT_SALE)
                .eq(BatchEvent::getSourceBizType, bizType.name())
                .eq(BatchEvent::getSourceBizId, bizId)
                .eq(BatchEvent::getStatus, STATUS_EFFECTIVE)
                .orderByAsc(BatchEvent::getId)));
        if (events == null || events.isEmpty()) {
            return;
        }
        LocalDateTime now = BeijingTime.now();
        Map<Long, Integer> quantityByBatch = new LinkedHashMap<>();
        Set<Long> touchedBatches = new LinkedHashSet<>();
        for (BatchEvent event : events) {
            if (event.getBatchId() == null || event.getPenId() == null || value(event.getQuantity()) <= 0) {
                continue;
            }
            int quantity = value(event.getQuantity());
            LocalDate eventDate = event.getEventDate() == null ? BeijingTime.today() : event.getEventDate();
            if (batchPenMapper.addStock(event.getBatchId(), event.getPenId(), quantity, eventDate, now) == 0) {
                insertBatchPen(event, quantity, eventDate, now);
            }
            penMapper.addStock(event.getPenId(), quantity, now);
            quantityByBatch.merge(event.getBatchId(), quantity, Integer::sum);
            touchedBatches.add(event.getBatchId());
        }
        for (Map.Entry<Long, Integer> entry : quantityByBatch.entrySet()) {
            restoreBatch(entry.getKey(), entry.getValue(), now);
        }
        for (Long batchId : touchedBatches) {
            timelineMapper.delete(Wrappers.<BatchTimeline>lambdaQuery()
                    .eq(BatchTimeline::getBatchId, batchId)
                    .eq(BatchTimeline::getBizType, bizType.name())
                    .eq(BatchTimeline::getBizId, bizId)
                    .eq(BatchTimeline::getRecordType, EVENT_SALE));
        }
        for (BatchEvent event : events) {
            eventMapper.update(null, Wrappers.<BatchEvent>lambdaUpdate()
                    .eq(BatchEvent::getId, event.getId())
                    .eq(BatchEvent::getStatus, STATUS_EFFECTIVE)
                    .set(BatchEvent::getStatus, STATUS_VOIDED)
                    .set(BatchEvent::getVersion, value(event.getVersion()) + 1)
                    .set(BatchEvent::getUpdatedAt, now));
        }
    }

    // ===================== 内部 =====================

    private void validate(SaleStockCommand command) {
        if (command == null || command.batchId() == null) {
            throw HerdErrors.fieldInvalid("batch_id", "请选择批次");
        }
        if (command.bizType() == null || command.bizId() == null) {
            throw HerdErrors.fieldInvalid("biz_id", "出栏单不能为空");
        }
    }

    private Batch loadBatch(Long batchId) {
        Batch batch = TenantSkip.call(() -> batchMapper.selectByIdIncludingDeleted(batchId));
        if (batch == null) {
            throw HerdErrors.notFound();
        }
        if (Boolean.TRUE.equals(batch.getDeleted())) {
            throw HerdErrors.deletedBatch();
        }
        return batch;
    }

    private List<BatchEvent> activeSaleEvents(Long batchId, BizType bizType, Long bizId) {
        return eventMapper.selectList(Wrappers.<BatchEvent>lambdaQuery()
                .eq(BatchEvent::getBatchId, batchId)
                .eq(BatchEvent::getEventType, EVENT_SALE)
                .eq(BatchEvent::getSourceBizType, bizType.name())
                .eq(BatchEvent::getSourceBizId, bizId)
                .eq(BatchEvent::getStatus, STATUS_EFFECTIVE)
                .orderByAsc(BatchEvent::getId));
    }

    /** 按栏舍分摊：指定了 items 按 items；未指定时按在栏栏舍顺序自动分摊 */
    private List<Allocation> allocate(SaleStockCommand command, List<BatchPen> rows) {
        Map<Long, BatchPen> byPen = new LinkedHashMap<>();
        for (BatchPen row : rows) {
            if (row.getPenId() != null) {
                byPen.put(row.getPenId(), row);
            }
        }
        Map<Long, String> penNames = penNames(byPen.keySet());
        List<Allocation> allocations = new ArrayList<>();
        if (command.items() != null && !command.items().isEmpty()) {
            Set<Long> seen = new LinkedHashSet<>();
            for (SaleStockItem item : command.items()) {
                if (item == null || item.penId() == null) {
                    throw HerdErrors.fieldInvalid("items", "请选择出栏栏舍");
                }
                if (item.quantity() <= 0) {
                    throw HerdErrors.fieldInvalid("quantity", "数量需为大于 0 的整数");
                }
                if (!seen.add(item.penId())) {
                    throw HerdErrors.fieldInvalid("items", "同一栏舍只能填写一次");
                }
                BatchPen row = byPen.get(item.penId());
                int stock = row == null ? 0 : value(row.getStockCount());
                if (item.quantity() > stock) {
                    throw HerdErrors.countExceeded(item.penId(), penNames.get(item.penId()), stock, null);
                }
                allocations.add(new Allocation(item.penId(), penNames.get(item.penId()),
                        item.quantity(), stock));
            }
            return allocations;
        }
        int remaining = command.quantity() == null ? 0 : command.quantity();
        if (remaining <= 0) {
            throw HerdErrors.fieldInvalid("quantity", "出栏数量需为大于 0 的整数");
        }
        int available = rows.stream().mapToInt(row -> value(row.getStockCount())).sum();
        if (remaining > available) {
            BatchPen first = rows.get(0);
            throw HerdErrors.countExceeded(first.getPenId(), penNames.get(first.getPenId()), available, null);
        }
        for (BatchPen row : rows) {
            if (remaining <= 0) {
                break;
            }
            int quantity = Math.min(remaining, value(row.getStockCount()));
            if (quantity <= 0) {
                continue;
            }
            allocations.add(new Allocation(row.getPenId(), penNames.get(row.getPenId()), quantity,
                    value(row.getStockCount())));
            remaining -= quantity;
        }
        if (allocations.isEmpty()) {
            throw HerdErrors.countExceeded(null, "批次", 0, null);
        }
        return allocations;
    }

    private Map<Long, String> penNames(Set<Long> penIds) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Pen pen : support.pensByIds(penIds)) {
            names.put(pen.getId(), pen.getPenName());
        }
        return names;
    }

    private void updateBatchCounters(Batch batch, int quantity, boolean ended, LocalDate saleDate,
                                     LocalDateTime now, Allocation first) {
        int version = value(batch.getVersion());
        var update = Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, batch.getId())
                .eq(Batch::getStatus, "ACTIVE")
                .ge(Batch::getCurrentCount, quantity)
                .setSql("current_count = current_count - " + quantity)
                .setSql("sold_count = sold_count + " + quantity)
                .set(Batch::getVersion, version + 1)
                .set(Batch::getUpdatedAt, now);
        if (ended) {
            update.set(Batch::getStatus, "ENDED")
                    .set(Batch::getEndType, "ALL_SOLD")
                    .set(Batch::getEndDate, saleDate)
                    .set(Batch::getEndedAt, now);
        }
        if (batchMapper.update(null, update) == 0) {
            Batch latest = loadBatch(batch.getId());
            if (!"ACTIVE".equals(latest.getStatus())) {
                throw HerdErrors.batchEnded();
            }
            throw HerdErrors.countExceeded(first.penId(), first.penName(),
                    value(latest.getCurrentCount()), null);
        }
        batch.setCurrentCount(value(batch.getCurrentCount()) - quantity);
        batch.setSoldCount(value(batch.getSoldCount()) + quantity);
        batch.setVersion(version + 1);
        if (ended) {
            batch.setStatus("ENDED");
            batch.setEndType("ALL_SOLD");
            batch.setEndDate(saleDate);
        }
    }

    private void restoreBatch(Long batchId, int quantity, LocalDateTime now) {
        Batch batch = TenantSkip.call(() -> batchMapper.selectByIdIncludingDeleted(batchId));
        if (batch == null || Boolean.TRUE.equals(batch.getDeleted())) {
            return;
        }
        int version = value(batch.getVersion());
        boolean reactivate = "ENDED".equals(batch.getStatus()) && "ALL_SOLD".equals(batch.getEndType());
        var update = Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, batchId)
                .eq(Batch::getVersion, version)
                .setSql("current_count = current_count + " + quantity)
                .setSql("sold_count = GREATEST(sold_count - " + quantity + ", 0)")
                .set(Batch::getVersion, version + 1)
                .set(Batch::getUpdatedAt, now);
        if (reactivate) {
            update.set(Batch::getStatus, "ACTIVE")
                    .set(Batch::getEndType, null)
                    .set(Batch::getEndDate, null)
                    .set(Batch::getEndedAt, null);
        }
        if (batchMapper.update(null, update) == 0) {
            throw HerdErrors.versionConflict();
        }
    }

    private void insertBatchPen(BatchEvent event, int quantity, LocalDate eventDate, LocalDateTime now) {
        Batch batch = TenantSkip.call(() -> batchMapper.selectByIdIncludingDeleted(event.getBatchId()));
        if (batch == null) {
            return;
        }
        BatchPen row = new BatchPen();
        row.setFarmId(batch.getFarmId());
        row.setBatchId(event.getBatchId());
        row.setPenId(event.getPenId());
        row.setStockCount(quantity);
        row.setFirstInDate(eventDate);
        batchPenMapper.insert(row);
    }

    private void writeTimeline(Batch batch, List<Allocation> allocations, int total, LocalDate saleDate,
                               SaleStockCommand command, Long operatorId, LocalDateTime now) {
        List<String> penNames = new ArrayList<>();
        for (Allocation allocation : allocations) {
            if (allocation.penName() != null && !penNames.contains(allocation.penName())) {
                penNames.add(allocation.penName());
            }
        }
        BatchTimeline row = new BatchTimeline();
        row.setFarmId(batch.getFarmId());
        row.setBatchId(batch.getId());
        row.setPenId(allocations.get(0).penId());
        row.setRecordType(EVENT_SALE);
        row.setBizType(command.bizType().name());
        row.setBizId(command.bizId());
        row.setBizDate(saleDate);
        row.setOccurredAt(now);
        row.setTitle("出栏 " + total + " 头");
        row.setSubtitle(String.join("、", penNames) + " · " + support.userName(operatorId));
        row.setDetailJson(toJson(detail("登记人", support.userName(operatorId),
                "出栏日期", BeijingTime.format(saleDate),
                "出栏栏舍", String.join("、", penNames),
                "数量", total + " 头")));
        row.setOperatorUserId(operatorId);
        row.setDeleted(false);
        timelineMapper.insert(row);
    }

    private BatchSaleResult resultOf(Batch batch, List<BatchEvent> events) {
        int total = events.stream().mapToInt(event -> value(event.getQuantity())).sum();
        List<BatchPenStock> pens = new ArrayList<>();
        Map<Long, String> names = penNames(events.stream().map(BatchEvent::getPenId)
                .filter(id -> id != null).collect(java.util.stream.Collectors.toSet()));
        for (BatchEvent event : events) {
            pens.add(new BatchPenStock(event.getPenId(), names.get(event.getPenId()), value(event.getQuantity()),
                    latestBatchPenStock(batch.getId(), event.getPenId())));
        }
        return new BatchSaleResult(batch.getId(), batch.getStatus(), total, value(batch.getCurrentCount()),
                value(batch.getSoldCount()), "ENDED".equals(batch.getStatus()),
                events.stream().map(BatchEvent::getId).toList(), pens);
    }

    private int latestBatchPenStock(Long batchId, Long penId) {
        List<BatchPen> rows = batchPenMapper.selectList(Wrappers.<BatchPen>lambdaQuery()
                .eq(BatchPen::getBatchId, batchId)
                .eq(BatchPen::getPenId, penId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? 0 : value(rows.get(0).getStockCount());
    }

    private int penStock(Long penId) {
        return support.pensByIds(List.of(penId)).stream()
                .findFirst()
                .map(pen -> value(pen.getStockCount()))
                .orElse(0);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String toJson(List<Map<String, String>> detail) {
        try {
            return JSON.writeValueAsString(detail);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<Map<String, String>> detail(String... values) {
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] == null) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key", values[i]);
            item.put("value", values[i + 1]);
            result.add(item);
        }
        return result;
    }

    private record Allocation(Long penId, String penName, int quantity, int stock) {
    }
}
