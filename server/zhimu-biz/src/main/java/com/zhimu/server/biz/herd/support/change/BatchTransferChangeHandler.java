package com.zhimu.server.biz.herd.support.change;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestHandler;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.support.HerdErrors;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 转栏记录（BATCH_TRANSFER，biz_id = 转栏流水 ID，record_version = 流水 version）。
 * <p>
 * 字段与 App Mock（change_form_batch_transfer.json）一致：transfer_count 数量，1–200 头。
 * 审核通过后先撤销原转出/转入影响，再按新数量执行，同时调整两个栏舍的存栏。
 */
@Component
@RequiredArgsConstructor
public class BatchTransferChangeHandler implements ChangeRequestHandler {

    private static final String FIELD_TRANSFER_COUNT = "transfer_count";
    private static final String FIELD_NAME = "数量";
    private static final String UNIT = "头";
    private static final int MIN = 1;
    private static final int MAX = 200;
    private static final String EVENT_TYPE = "TRANSFER";

    private final HerdChangeSupport change;
    private final BatchEventMapper eventMapper;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.BATCH_TRANSFER);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.BATCH_TRANSFER);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        BatchEvent event = change.event(bizId, EVENT_TYPE);
        if (event == null) {
            return null;
        }
        Batch batch = change.batch(event.getBatchId());
        if (batch == null) {
            return null;
        }
        String fromName = change.penName(event.getPenId());
        String toName = change.penName(event.getToPenId());
        String date = change.monthDay(event.getEventDate());
        String locator = batch.getBatchNo() + " · " + date + "转栏";
        String title = "转栏记录 · " + toName;
        String subtitle = fromName + " → " + toName + " · " + date + " · "
                + change.userName(event.getCreatedBy()) + "登记";
        int version = event.getVersion() == null ? 0 : event.getVersion();
        return new ChangeRecord(BizType.BATCH_TRANSFER, event.getId(), batch.getFarmId(), batch.getId(),
                event.getPenId(), locator, title, subtitle, String.valueOf(version),
                Boolean.TRUE.equals(event.getDeleted()), "VOIDED".equalsIgnoreCase(event.getStatus()));
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        Batch batch = change.batch(record.batchId());
        BatchEvent event = change.event(record.bizId(), EVENT_TYPE);
        if (batch == null || event == null) {
            return true;
        }
        if (!"ACTIVE".equals(batch.getStatus())) {
            // 批次已结束时不接受修改申请（模块04 文档 2.9）
            throw HerdErrors.batchEnded();
        }
        return !BeijingTime.withinEditWindow(event.getEventDate(), 3);
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        BatchEvent event = change.event(record.bizId(), EVENT_TYPE);
        int quantity = event == null || event.getQuantity() == null ? 0 : event.getQuantity();
        String fromName = change.penName(event == null ? null : event.getPenId());
        String toName = change.penName(event == null ? null : event.getToPenId());
        ChangeField field = new ChangeField(FIELD_TRANSFER_COUNT, FIELD_NAME, String.valueOf(quantity), UNIT,
                ChangeField.NUMBER, List.of(), BigDecimal.valueOf(MIN), BigDecimal.valueOf(MAX),
                List.of("存栏"), null);
        return new ChangeForm(
                List.of(new ChangeCardItem("转出", fromName),
                        new ChangeCardItem("转入", toName),
                        new ChangeCardItem(FIELD_NAME, quantity + " " + UNIT)),
                List.of(field),
                List.of("修改转栏数量会同时调整两个栏舍的存栏"),
                "这条记录会影响两个栏舍的存栏，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            throw HerdErrors.stateNotAllowed("这条转栏记录暂不能作废或删除");
        }
        HerdChangeSupport.requireField(form, fieldCode);
        if (!FIELD_TRANSFER_COUNT.equals(fieldCode)) {
            throw HerdChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        BatchEvent event = change.event(record.bizId(), EVENT_TYPE);
        if (event == null) {
            throw HerdErrors.notFound();
        }
        int before = event.getQuantity() == null ? 0 : event.getQuantity();
        int after = HerdChangeSupport.requireCount(newValue, FIELD_NAME, MIN, MAX, UNIT);
        if (after == before) {
            throw HerdChangeSupport.fieldError("new_value", "新值与原值相同，不需要提交");
        }
        // 撤销原记录后转出栏舍可用在栏数 = 当前在栏数 + 原转出数量
        int available = change.penStock(record.batchId(), event.getPenId()) + before;
        if (after > available) {
            throw HerdErrors.countExceeded(event.getPenId(), change.penName(event.getPenId()), available, null);
        }
        return ChangeValidation.of(FIELD_TRANSFER_COUNT, FIELD_NAME, UNIT, String.valueOf(before),
                HerdChangeSupport.display(String.valueOf(before), UNIT), String.valueOf(after),
                HerdChangeSupport.display(String.valueOf(after), UNIT),
                List.of(ChangeImpact.STOCK, ChangeImpact.STATS), List.of("存栏"));
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        Batch batch = change.batch(record.batchId());
        BatchEvent event = change.event(record.bizId(), EVENT_TYPE);
        if (batch == null || event == null) {
            throw HerdErrors.notFound();
        }
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw HerdErrors.batchEnded();
        }
        int before = event.getQuantity() == null ? 0 : event.getQuantity();
        int after = afterCount(request);
        int available = change.penStock(batch.getId(), event.getPenId()) + before;
        if (after > available) {
            throw HerdErrors.countExceeded(event.getPenId(), change.penName(event.getPenId()), available, null);
        }
        if (event.getToPenId() != null) {
            int toStock = change.penStock(batch.getId(), event.getToPenId());
            if (toStock < before) {
                throw HerdErrors.countExceeded(event.getToPenId(), change.penName(event.getToPenId()), toStock, null);
            }
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        Batch batch = change.batch(record.batchId());
        BatchEvent event = change.event(record.bizId(), EVENT_TYPE);
        if (batch == null || event == null) {
            throw HerdErrors.notFound();
        }
        int before = event.getQuantity() == null ? 0 : event.getQuantity();
        int after = afterCount(request);
        Long fromPenId = event.getPenId();
        Long toPenId = event.getToPenId();
        String fromName = change.penName(fromPenId);
        String toName = change.penName(toPenId);
        LocalDate eventDate = event.getEventDate();
        // 1. 撤销原影响：转出栏舍加回、转入栏舍扣回
        change.addStock(batch, fromPenId, before, eventDate, fromName);
        if (toPenId != null && before > 0) {
            change.deductStock(batch, toPenId, before, eventDate, toName);
        }
        // 2. 按新值执行：转出栏舍扣减、转入栏舍增加
        change.deductStock(batch, fromPenId, after, eventDate, fromName);
        if (toPenId != null && after > 0) {
            change.addStock(batch, toPenId, after, eventDate, toName);
        }
        int eventRows = TenantSkip.call(() -> eventMapper.update(null, Wrappers.<BatchEvent>lambdaUpdate()
                .eq(BatchEvent::getId, event.getId())
                .eq(BatchEvent::getVersion, event.getVersion() == null ? 0 : event.getVersion())
                .set(BatchEvent::getQuantity, after)
                .set(BatchEvent::getVersion, (event.getVersion() == null ? 0 : event.getVersion()) + 1)
                .set(BatchEvent::getUpdatedBy, adminId)
                .set(BatchEvent::getUpdatedAt, BeijingTime.now())));
        if (eventRows == 0) {
            throw HerdErrors.versionConflict();
        }
        change.bumpBatchVersion(batch);
        change.updateTimeline(batch.getId(), BizType.BATCH_TRANSFER.name(), event.getId(), fromPenId, eventDate,
                "转栏 · " + after + " 头", fromName + " → " + toName + " · " + change.userName(event.getCreatedBy()));
        Map<String, Object> beforeValues = new LinkedHashMap<>();
        Map<String, Object> afterValues = new LinkedHashMap<>();
        beforeValues.put(FIELD_TRANSFER_COUNT, HerdChangeSupport.value(FIELD_NAME, before + " " + UNIT));
        afterValues.put(FIELD_TRANSFER_COUNT, HerdChangeSupport.value(FIELD_NAME, after + " " + UNIT));
        change.logApply(request, BizType.BATCH_TRANSFER, event.getId(), batch.getBatchNo(), batch.getFarmId(),
                batch.getBatchNo(), beforeValues, afterValues);
        return new ChangeApplyResult(2, "已重新计算转出与转入栏舍的存栏");
    }

    private static int afterCount(ChangeRequest request) {
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_TRANSFER_COUNT);
        if (value == null || value.isBlank()) {
            throw HerdChangeSupport.fieldError("new_value", "请填写" + FIELD_NAME);
        }
        return HerdChangeSupport.requireCount(value, FIELD_NAME, MIN, MAX, UNIT);
    }
}
