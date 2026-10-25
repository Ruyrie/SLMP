package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.health.entity.BatchWithdrawal;
import com.zhimu.server.biz.health.mapper.BatchWithdrawalMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 休药结束待办：按 t_batch_withdrawal 的 release_date 生成，日期随真实用药记录重算自动刷新。
 * 休药期已结束的待办由来源自动忽略，不再产生新的提醒。
 */
@Service
@RequiredArgsConstructor
public class WithdrawalTodoSource implements TodoSource {

    public static final String WITHDRAWAL_END = "WITHDRAWAL_END";
    private static final String ENDED_REASON = "休药期已结束";

    private final BatchWithdrawalMapper withdrawalMapper;
    private final BatchQueryService batchQueryService;
    private final HealthReferenceMapper referenceMapper;
    private final TodoSourceWriteService todoWriter;

    @Override
    public String moduleName() {
        return "health";
    }

    @Override
    public List<Long> generate(TodoSourceContext context) {
        if (context == null || context.getFarmId() == null) {
            return List.of();
        }
        Long farmId = context.getFarmId();
        return TenantSkip.call(() -> {
            LocalDate today = BeijingTime.today();
            Map<Long, BatchInfo> batches = new HashMap<>();
            for (BatchInfo batch : batchQueryService.listActive(farmId)) {
                batches.put(batch.batchId(), batch);
            }
            List<Long> result = new ArrayList<>();
            for (BatchWithdrawal row : withdrawalMapper.selectList(Wrappers.<BatchWithdrawal>lambdaQuery()
                    .eq(BatchWithdrawal::getFarmId, farmId))) {
                String key = key(row.getBatchId());
                if (row.getReleaseDate() == null || row.getReleaseDate().isBefore(today)) {
                    todoWriter.ignoreFromSource(farmId, WITHDRAWAL_END, key, ENDED_REASON);
                    continue;
                }
                BatchInfo batch = batches.get(row.getBatchId());
                if (batch == null) {
                    continue;
                }
                result.addAll(todoWriter.upsertFromSource(farmId, List.of(draft(row, batch))));
            }
            return result;
        });
    }

    private TodoDraft draft(BatchWithdrawal row, BatchInfo batch) {
        Long penId = batch.entryPenId();
        if ((penId == null || penId == 0L) && batch.penIds() != null && !batch.penIds().isEmpty()) {
            penId = batch.penIds().get(0);
        }
        String penName = penId == null ? null : referenceMapper.selectPenName(penId);
        LocalDate release = row.getReleaseDate();
        LocalTime due = LocalTime.of(9, 0);
        String drug = row.getDecisiveDrugName() == null ? "最近用药" : row.getDecisiveDrugName();
        return TodoDraft.builder().todoType(WITHDRAWAL_END).category("WORK")
                .penId(penId).penName(penName).batchId(batch.batchId()).batchNo(batch.batchNo())
                .bizType("BATCH").bizId(batch.batchId()).dedupKey(key(batch.batchId()))
                .title(batch.batchNo() + " · 休药期结束")
                .contentText(drug + " 休药至 " + release)
                .detailLabel("可出栏日期").detailValue(release.toString())
                .receiverScope("PEN_ALL").dueDate(release).timeType("BEFORE_TIME").dueTime(due)
                .overdueAt(release.atTime(due)).build();
    }

    public static String key(Long batchId) {
        return "WITHDRAWAL:" + batchId;
    }
}
