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
import com.zhimu.server.biz.herd.mapper.BatchMapper;
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
 * 入栏记录（BATCH_ENTRY，biz_id = 入栏流水 ID，record_version = t_batch.version）。
 * <p>
 * 字段与 App Mock（change_form_batch_entry.json）一致：head_count 入栏数量，1–10000 头。
 * 已有后续记录、批次已结束或超过 3 天直接修改期的记录必须走修改申请；审核通过按差额调整批次存栏与入栏栏舍在栏数。
 */
@Component
@RequiredArgsConstructor
public class BatchEntryChangeHandler implements ChangeRequestHandler {

    private static final String FIELD_HEAD_COUNT = "head_count";
    private static final String FIELD_NAME = "入栏数量";
    private static final String UNIT = "头";
    private static final int MIN = 1;
    private static final int MAX = 10_000;
    private static final String CHANGE_TYPE = "ENTRY";

    private final HerdChangeSupport change;
    private final BatchMapper batchMapper;
    private final BatchEventMapper eventMapper;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.BATCH_ENTRY);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.BATCH_ENTRY);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        BatchEvent event = change.entryEvent(bizId);
        if (event == null) {
            return null;
        }
        Batch batch = change.batch(event.getBatchId());
        if (batch == null) {
            return null;
        }
        String penName = change.penName(event.getPenId());
        String date = change.monthDay(event.getEventDate());
        String locator = batch.getBatchNo() + " · " + date + "入栏";
        String title = "入栏记录 · " + penName;
        String subtitle = change.breedName(batch.getBreedId()) + " · " + date + " · "
                + change.userName(event.getCreatedBy()) + "登记";
        boolean deleted = Boolean.TRUE.equals(event.getDeleted()) || Boolean.TRUE.equals(batch.getDeleted());
        boolean voided = "VOIDED".equalsIgnoreCase(event.getStatus());
        int version = batch.getVersion() == null ? 0 : batch.getVersion();
        return new ChangeRecord(BizType.BATCH_ENTRY, event.getId(), batch.getFarmId(), batch.getId(),
                event.getPenId(), locator, title, subtitle, String.valueOf(version), deleted, voided);
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        Batch batch = change.batch(record.batchId());
        BatchEvent event = change.event(record.bizId(), CHANGE_TYPE);
        if (batch == null || event == null) {
            return true;
        }
        if (!"ACTIVE".equals(batch.getStatus())) {
            // 批次已结束不能直接改入栏，只能提交申请
            return true;
        }
        if (change.followUpEvents(batch.getId()) > 0) {
            return true;
        }
        return !BeijingTime.withinEditWindow(event.getEventDate(), 3);
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        Batch batch = change.batch(record.batchId());
        int count = batch == null || batch.getEntryCount() == null ? 0 : batch.getEntryCount();
        int age = batch == null || batch.getEntryAgeDays() == null ? 0 : batch.getEntryAgeDays();
        BigDecimal weight = batch == null ? null : batch.getEntryAvgWeight();
        String weightText = weight == null ? ""
                : weight.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + " 千克";
        ChangeField field = new ChangeField(FIELD_HEAD_COUNT, FIELD_NAME, String.valueOf(count), UNIT,
                ChangeField.NUMBER, List.of(), BigDecimal.valueOf(MIN), BigDecimal.valueOf(MAX),
                List.of("存栏", "成本"), null);
        return new ChangeForm(
                List.of(new ChangeCardItem(FIELD_NAME, count + " " + UNIT),
                        new ChangeCardItem("入栏日龄", age + " 天"),
                        new ChangeCardItem("平均体重", weightText)),
                List.of(field),
                List.of("修改入栏数量会重新计算存栏、死亡率与成本分摊"),
                "这条记录会影响存栏和成本，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            throw HerdErrors.stateNotAllowed("这条入栏记录暂不能作废或删除");
        }
        HerdChangeSupport.requireField(form, fieldCode);
        if (!FIELD_HEAD_COUNT.equals(fieldCode)) {
            throw HerdChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        int before = Integer.parseInt(form.fieldOf(FIELD_HEAD_COUNT).value());
        int after = HerdChangeSupport.requireCount(newValue, FIELD_NAME, MIN, MAX, UNIT);
        if (after == before) {
            throw HerdChangeSupport.fieldError("new_value", "新值与原值相同，不需要提交");
        }
        return ChangeValidation.of(FIELD_HEAD_COUNT, FIELD_NAME, UNIT, String.valueOf(before),
                HerdChangeSupport.display(String.valueOf(before), UNIT), String.valueOf(after),
                HerdChangeSupport.display(String.valueOf(after), UNIT),
                List.of(ChangeImpact.STOCK, ChangeImpact.COST, ChangeImpact.STATS),
                List.of("存栏", "成本"));
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        Batch batch = change.batch(record.batchId());
        BatchEvent event = change.event(record.bizId(), CHANGE_TYPE);
        if (batch == null || event == null) {
            throw HerdErrors.notFound();
        }
        int after = afterCount(request);
        int before = batch.getEntryCount() == null ? event.getQuantity() : batch.getEntryCount();
        int current = batch.getCurrentCount() == null ? 0 : batch.getCurrentCount();
        if (current + (after - before) < 0) {
            throw HerdErrors.countExceeded(event.getPenId(), change.penName(event.getPenId()),
                    change.penStock(batch.getId(), event.getPenId()), null);
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        Batch batch = change.batch(record.batchId());
        BatchEvent event = change.event(record.bizId(), CHANGE_TYPE);
        if (batch == null || event == null) {
            throw HerdErrors.notFound();
        }
        int before = batch.getEntryCount() == null ? 0 : batch.getEntryCount();
        int after = afterCount(request);
        int delta = after - before;
        String penName = change.penName(event.getPenId());
        LocalDate eventDate = event.getEventDate();
        if (delta != 0) {
            int current = (batch.getCurrentCount() == null ? 0 : batch.getCurrentCount()) + delta;
            if (current < 0) {
                throw HerdErrors.countExceeded(event.getPenId(), penName,
                        change.penStock(batch.getId(), event.getPenId()), null);
            }
            change.addStock(batch, event.getPenId(), delta, eventDate, penName);
            int rows = batchMapper.update(null, Wrappers.<Batch>lambdaUpdate()
                    .eq(Batch::getId, batch.getId())
                    .eq(Batch::getVersion, batch.getVersion() == null ? 0 : batch.getVersion())
                    .set(Batch::getEntryCount, after)
                    .set(Batch::getCurrentCount, current)
                    .set(Batch::getVersion, (batch.getVersion() == null ? 0 : batch.getVersion()) + 1)
                    .set(Batch::getUpdatedBy, adminId)
                    .set(Batch::getUpdatedAt, BeijingTime.now()));
            if (rows == 0) {
                throw HerdErrors.versionConflict();
            }
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
        change.updateTimeline(batch.getId(), BizType.BATCH_ENTRY.name(), event.getId(), event.getPenId(), eventDate,
                "入栏 " + after + " 头", change.breedName(batch.getBreedId()) + " · 入栏日龄 "
                        + batch.getEntryAgeDays() + " 天");
        Map<String, Object> beforeValues = new LinkedHashMap<>();
        Map<String, Object> afterValues = new LinkedHashMap<>();
        beforeValues.put(FIELD_HEAD_COUNT, HerdChangeSupport.value(FIELD_NAME, before + " " + UNIT));
        afterValues.put(FIELD_HEAD_COUNT, HerdChangeSupport.value(FIELD_NAME, after + " " + UNIT));
        change.logApply(request, BizType.BATCH_ENTRY, event.getId(), batch.getBatchNo(), batch.getFarmId(),
                batch.getBatchNo(), beforeValues, afterValues);
        return new ChangeApplyResult(3, "已重新计算批次存栏与成本");
    }

    private static int afterCount(ChangeRequest request) {
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_HEAD_COUNT);
        if (value == null || value.isBlank()) {
            throw HerdChangeSupport.fieldError("new_value", "请填写" + FIELD_NAME);
        }
        return HerdChangeSupport.requireCount(value, FIELD_NAME, MIN, MAX, UNIT);
    }
}
