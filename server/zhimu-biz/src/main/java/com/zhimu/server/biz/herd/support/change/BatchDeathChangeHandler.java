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
 * 死淘记录（BATCH_DEATH，biz_id = 死淘流水 ID，record_version = 流水 version）。
 * <p>
 * 字段与 App Mock（change_form_batch_death.json）一致：death_count 数量，1–200 头。
 * 超过 3 天直接修改期的记录走修改申请；审核通过先恢复原扣减，再按新数量扣减并重算存栏与死亡率。
 */
@Component
@RequiredArgsConstructor
public class BatchDeathChangeHandler implements ChangeRequestHandler {

    private static final String FIELD_DEATH_COUNT = "death_count";
    private static final String FIELD_NAME = "数量";
    private static final String UNIT = "头";
    private static final int MIN = 1;
    private static final int MAX = 200;
    private static final String EVENT_TYPE = "DEATH";

    private final HerdChangeSupport change;
    private final BatchEventMapper eventMapper;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.BATCH_DEATH);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.BATCH_DEATH);
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
        String penName = change.penName(event.getPenId());
        String reason = HerdChangeSupport.deathReasonText(event.getReasonCode());
        String date = change.monthDay(event.getEventDate());
        String locator = batch.getBatchNo() + " · " + date + "死淘";
        String title = "死淘记录 · " + penName;
        String executor = event.getExecutorUserId() == null
                ? change.userName(event.getCreatedBy()) : change.userName(event.getExecutorUserId());
        String subtitle = reason + " " + event.getQuantity() + " " + UNIT + " · " + date + " · " + executor + "登记";
        int version = event.getVersion() == null ? 0 : event.getVersion();
        return new ChangeRecord(BizType.BATCH_DEATH, event.getId(), batch.getFarmId(), batch.getId(),
                event.getPenId(), locator, title, subtitle, String.valueOf(version),
                Boolean.TRUE.equals(event.getDeleted()), "VOIDED".equalsIgnoreCase(event.getStatus()));
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        BatchEvent event = change.event(record.bizId(), EVENT_TYPE);
        if (event == null) {
            return true;
        }
        return !BeijingTime.withinEditWindow(event.getEventDate(), 3);
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        BatchEvent event = change.event(record.bizId(), EVENT_TYPE);
        int quantity = event == null || event.getQuantity() == null ? 0 : event.getQuantity();
        String reason = HerdChangeSupport.deathReasonText(event == null ? null : event.getReasonCode());
        ChangeField field = new ChangeField(FIELD_DEATH_COUNT, FIELD_NAME, String.valueOf(quantity), UNIT,
                ChangeField.NUMBER, List.of(), BigDecimal.valueOf(MIN), BigDecimal.valueOf(MAX),
                List.of("存栏", "死亡率"), null);
        return new ChangeForm(
                List.of(new ChangeCardItem("原因", reason),
                        new ChangeCardItem(FIELD_NAME, quantity + " " + UNIT)),
                List.of(field),
                List.of("修改死淘数量会重新计算存栏与死亡率"),
                "这条记录会影响存栏和死亡率，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            throw HerdErrors.stateNotAllowed("这条死淘记录暂不能作废或删除");
        }
        HerdChangeSupport.requireField(form, fieldCode);
        if (!FIELD_DEATH_COUNT.equals(fieldCode)) {
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
        int available = change.penStock(record.batchId(), event.getPenId()) + before;
        if (after > available) {
            throw HerdErrors.countExceeded(event.getPenId(), change.penName(event.getPenId()), available, null);
        }
        return ChangeValidation.of(FIELD_DEATH_COUNT, FIELD_NAME, UNIT, String.valueOf(before),
                HerdChangeSupport.display(String.valueOf(before), UNIT), String.valueOf(after),
                HerdChangeSupport.display(String.valueOf(after), UNIT),
                List.of(ChangeImpact.STOCK, ChangeImpact.COST, ChangeImpact.STATS),
                List.of("存栏", "死亡率"));
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        Batch batch = change.batch(record.batchId());
        BatchEvent event = change.event(record.bizId(), EVENT_TYPE);
        if (batch == null || event == null) {
            throw HerdErrors.notFound();
        }
        int before = event.getQuantity() == null ? 0 : event.getQuantity();
        int after = afterCount(request);
        int available = change.penStock(batch.getId(), event.getPenId()) + before;
        if (after > available) {
            throw HerdErrors.countExceeded(event.getPenId(), change.penName(event.getPenId()), available, null);
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
        Long penId = event.getPenId();
        String penName = change.penName(penId);
        LocalDate eventDate = event.getEventDate();
        // 1. 撤销原扣减
        change.addStock(batch, penId, before, eventDate, penName);
        int current = (batch.getCurrentCount() == null ? 0 : batch.getCurrentCount()) + before;
        int death = Math.max(0, (batch.getDeathCount() == null ? 0 : batch.getDeathCount()) - before);
        // 2. 按新值扣减
        change.deductStock(batch, penId, after, eventDate, penName);
        change.updateBatchCounters(batch, current - after, death + after, eventDate);
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
        String reason = HerdChangeSupport.deathReasonText(event.getReasonCode());
        change.updateTimeline(batch.getId(), BizType.BATCH_DEATH.name(), event.getId(), penId, eventDate,
                "死淘 · " + change.countText(batch, after) + " · " + reason, penName + " · "
                        + change.userName(event.getExecutorUserId()));
        Map<String, Object> beforeValues = new LinkedHashMap<>();
        Map<String, Object> afterValues = new LinkedHashMap<>();
        beforeValues.put(FIELD_DEATH_COUNT, HerdChangeSupport.value(FIELD_NAME, before + " " + UNIT));
        afterValues.put(FIELD_DEATH_COUNT, HerdChangeSupport.value(FIELD_NAME, after + " " + UNIT));
        change.logApply(request, BizType.BATCH_DEATH, event.getId(), batch.getBatchNo(), batch.getFarmId(),
                batch.getBatchNo(), beforeValues, afterValues);
        return new ChangeApplyResult(3, "已重新计算批次存栏与死亡率");
    }

    private static int afterCount(ChangeRequest request) {
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_DEATH_COUNT);
        if (value == null || value.isBlank()) {
            throw HerdChangeSupport.fieldError("new_value", "请填写" + FIELD_NAME);
        }
        return HerdChangeSupport.requireCount(value, FIELD_NAME, MIN, MAX, UNIT);
    }
}
