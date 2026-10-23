package com.zhimu.server.biz.breeding.support.change;

import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.mapper.IncubationMapper;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingErrors;
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
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.SelfBredBatchService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 孵化记录（INCUBATION，biz_id = 孵化记录 ID，record_version = 孵化记录 version）。
 * <p>
 * 字段与 App Mock（change_form_incubation.json）一致：hatched_count 出雏数、healthy_count 健雏数，
 * 约束「出雏 ≤ 受精蛋」「健雏 ≤ 出雏」，weak_count 随出雏 − 健雏重算。
 * 已出雏且健雏已转为新批次的记录走修改申请；审核通过按健雏差额调整自繁批次存栏。
 */
@Component
@RequiredArgsConstructor
public class IncubationChangeHandler implements ChangeRequestHandler {

    private static final String FIELD_HATCHED_COUNT = "hatched_count";
    private static final String FIELD_HEALTHY_COUNT = "healthy_count";
    private static final String UNIT_CHICK = "只";
    private static final String UNIT_EGG = "枚";

    private final BreedingChangeSupport change;
    private final IncubationMapper incubationMapper;
    private final SelfBredBatchService selfBredBatchService;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.INCUBATION);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.INCUBATION);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        Incubation record = change.incubation(bizId);
        if (record == null) {
            return null;
        }
        String penName = change.penName(record.getPenId());
        String batchNo = change.batchNo(record.getBreedingBatchId());
        String batchText = batchNo == null ? "" : batchNo;
        String date = change.monthDay(record.getSetDate());
        String locator = (penName.isEmpty() ? "孵化" : penName) + " · " + batchText + " · " + date + "入孵";
        String title = "孵化 · " + penName + " · " + batchText;
        String subtitle = change.farmName(record.getFarmId()) + " · " + date + "入孵 · "
                + change.userName(record.getCreatedBy()) + "登记";
        int version = record.getVersion() == null ? 0 : record.getVersion();
        return new ChangeRecord(BizType.INCUBATION, record.getId(), record.getFarmId(),
                record.getBreedingBatchId(), record.getPenId(), locator, title, subtitle,
                String.valueOf(version), false, false);
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        Incubation fresh = change.incubation(record.bizId());
        return fresh != null && BreedingCodes.INCUBATION_HATCHED.equals(fresh.getStatus())
                && fresh.getBatchId() != null;
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        Incubation fresh = change.incubation(record.bizId());
        if (fresh == null) {
            throw BreedingErrors.notFound();
        }
        int setCount = BreedingChangeSupport.intOf(fresh.getSetCount());
        int fertile = BreedingChangeSupport.intOf(fresh.getFertileCount());
        int hatched = BreedingChangeSupport.intOf(fresh.getHatchedCount());
        int healthy = BreedingChangeSupport.intOf(fresh.getHealthyCount());
        List<ChangeCardItem> card = new ArrayList<>();
        card.add(new ChangeCardItem("入孵蛋数", format(setCount) + " " + UNIT_EGG));
        card.add(new ChangeCardItem("受精蛋数", format(fertile) + " " + UNIT_EGG));
        card.add(new ChangeCardItem("出雏数", format(hatched) + " " + UNIT_CHICK));
        card.add(new ChangeCardItem("健雏数", format(healthy) + " " + UNIT_CHICK));
        String batchNo = change.batchNo(fresh.getBatchId());
        if (batchNo != null) {
            card.add(new ChangeCardItem("新批次", batchNo));
        }
        int hatchedMax = fresh.getFertileCount() != null ? fertile : Math.max(setCount, hatched);
        int healthyMax = Math.max(hatched, healthy);
        List<ChangeField> fields = List.of(
                new ChangeField(FIELD_HATCHED_COUNT, "出雏数", String.valueOf(hatched), UNIT_CHICK,
                        ChangeField.NUMBER, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(hatchedMax),
                        List.of(), null),
                new ChangeField(FIELD_HEALTHY_COUNT, "健雏数", String.valueOf(healthy), UNIT_CHICK,
                        ChangeField.NUMBER, List.of(), BigDecimal.ZERO, BigDecimal.valueOf(healthyMax),
                        List.of("存栏", "孵化统计"), null));
        return new ChangeForm(card, fields, List.of(),
                "健雏已转为新批次，修改会影响存栏和统计，需要管理员审核。审核期间原记录照常有效。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            throw BreedingErrors.stateChanged("这条孵化记录暂不能作废或删除");
        }
        BreedingChangeSupport.requireField(form, fieldCode);
        Incubation fresh = change.incubation(record.bizId());
        if (fresh == null) {
            throw BreedingErrors.notFound();
        }
        int hatched = BreedingChangeSupport.intOf(fresh.getHatchedCount());
        int healthy = BreedingChangeSupport.intOf(fresh.getHealthyCount());
        int fertile = BreedingChangeSupport.intOf(fresh.getFertileCount());
        int setCount = BreedingChangeSupport.intOf(fresh.getSetCount());
        return switch (fieldCode) {
            case FIELD_HATCHED_COUNT -> {
                int max = fresh.getFertileCount() != null ? fertile : setCount;
                int after = BreedingChangeSupport.intValue(newValue, "出雏数", 0, 999_999);
                if (after == hatched) {
                    BreedingChangeSupport.sameValue();
                }
                if (after > max) {
                    throw BreedingChangeSupport.fieldError("new_value",
                            "出雏数不能多于受精蛋数 " + format(max) + " 枚");
                }
                if (after < healthy) {
                    throw BreedingChangeSupport.fieldError("new_value", "出雏数不能少于健雏数 " + healthy + " 只");
                }
                yield countValidation(FIELD_HATCHED_COUNT, "出雏数", hatched, after,
                        List.of(ChangeImpact.STATS), List.of());
            }
            case FIELD_HEALTHY_COUNT -> {
                int after = BreedingChangeSupport.intValue(newValue, "健雏数", 0, 999_999);
                if (after == healthy) {
                    BreedingChangeSupport.sameValue();
                }
                if (after > hatched) {
                    throw BreedingChangeSupport.fieldError("new_value", "健雏数不能多于出雏数 " + hatched + " 只");
                }
                yield countValidation(FIELD_HEALTHY_COUNT, "健雏数", healthy, after,
                        List.of(ChangeImpact.STOCK, ChangeImpact.STATS), List.of("存栏", "孵化统计"));
            }
            default -> throw BreedingChangeSupport.fieldError("field_code", "请选择要修改的项目");
        };
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        Incubation fresh = change.incubation(record.bizId());
        if (fresh == null) {
            throw BreedingErrors.stateChanged("原孵化记录已不存在，请驳回");
        }
        if (!BreedingCodes.INCUBATION_HATCHED.equals(fresh.getStatus())) {
            throw BreedingErrors.stateChanged("本次孵化已不是已出雏状态，请驳回");
        }
        Values values = valuesOf(fresh, request);
        if (values.healthyDelta() < 0) {
            BatchInfo batch = change.batchInfo(fresh.getBatchId());
            int current = batch == null || batch.currentCount() == null ? 0 : batch.currentCount();
            if (current + values.healthyDelta() < 0) {
                throw new BizException(ErrorCode.COUNT_EXCEEDED, "在栏数不足，无法按申请修改");
            }
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        Incubation fresh = change.incubation(record.bizId());
        if (fresh == null) {
            throw BreedingErrors.notFound();
        }
        Values values = valuesOf(fresh, request);
        fresh.setHatchedCount(values.hatchedCount());
        fresh.setHealthyCount(values.healthyCount());
        fresh.setWeakCount(values.hatchedCount() - values.healthyCount());
        fresh.setUpdatedBy(adminId);
        if (incubationMapper.updateById(fresh) == 0) {
            throw BreedingErrors.versionConflict();
        }
        if (fresh.getBatchId() != null && values.healthyDelta() != 0) {
            selfBredBatchService.adjustEntry(fresh.getBatchId(), values.healthyDelta(), null);
        }
        Map<String, Object> beforeValues = FarrowingChangeHandler.displays(request.getBeforeJson(), "before");
        Map<String, Object> afterValues = FarrowingChangeHandler.displays(request.getAfterJson(), "after");
        change.logApply(request, BizType.INCUBATION, fresh.getId(),
                change.batchNo(fresh.getBatchId()), fresh.getFarmId(),
                change.batchNo(fresh.getBatchId()), beforeValues, afterValues);
        String summary = fresh.getBatchId() == null
                ? "已重新计算孵化统计" : "已重新计算批次存栏与孵化统计";
        return new ChangeApplyResult(fresh.getBatchId() == null ? 2 : 3, summary);
    }

    private static ChangeValidation countValidation(String fieldCode, String fieldName, int before, int after,
                                                    List<ChangeImpact> impacts, List<String> tags) {
        return ChangeValidation.of(fieldCode, fieldName, UNIT_CHICK, String.valueOf(before),
                BreedingChangeSupport.display(String.valueOf(before), UNIT_CHICK), String.valueOf(after),
                BreedingChangeSupport.display(String.valueOf(after), UNIT_CHICK), impacts, tags);
    }

    /** 按申请字段换算新的出雏数 / 健雏数与健雏差额 */
    private Values valuesOf(Incubation fresh, ChangeRequest request) {
        int hatched = BreedingChangeSupport.intOf(fresh.getHatchedCount());
        int healthy = BreedingChangeSupport.intOf(fresh.getHealthyCount());
        String fieldCode = request.getFieldCode();
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), fieldCode);
        if (fieldCode != null && value != null) {
            if (FIELD_HATCHED_COUNT.equals(fieldCode)) {
                hatched = BreedingChangeSupport.intValue(value, "出雏数", 0, 999_999);
            } else if (FIELD_HEALTHY_COUNT.equals(fieldCode)) {
                healthy = BreedingChangeSupport.intValue(value, "健雏数", 0, 999_999);
            }
        }
        if (healthy > hatched) {
            throw BreedingChangeSupport.fieldError("new_value", "健雏数不能多于出雏数 " + hatched + " 只");
        }
        return new Values(hatched, healthy, healthy - BreedingChangeSupport.intOf(fresh.getHealthyCount()));
    }

    private static String format(int value) {
        return String.format(java.util.Locale.CHINA, "%,d", value);
    }

    private record Values(int hatchedCount, int healthyCount, int healthyDelta) {
    }
}
