package com.zhimu.server.biz.breeding.support.change;

import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
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
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 种畜死亡淘汰（BREEDING_EXIT，biz_id = 种畜 ID，record_version = 种畜档案 version）。
 * <p>
 * 字段与 App Mock（change_form_exit.json）一致：exit_date 日期、reason 原因（≤ 50 字），并支持 VOID 撤销登记。
 * UPDATE 直接改字段；VOID 恢复登记前状态、清空 exit 字段并撤销当前周期的 EXIT 结束。
 */
@Component
@RequiredArgsConstructor
public class BreedingExitChangeHandler implements ChangeRequestHandler {

    private static final String FIELD_EXIT_DATE = "exit_date";
    private static final String FIELD_REASON = "reason";
    private static final int REASON_MAX = 50;

    private final BreedingChangeSupport change;
    private final BreedingAnimalMapper breedingAnimalMapper;
    private final BreedingCycleMapper breedingCycleMapper;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.BREEDING_EXIT);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.BREEDING_EXIT);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        BreedingAnimal animal = change.animal(bizId);
        if (animal == null || animal.getExitType() == null) {
            return null;
        }
        String earTag = animal.getEarTag() == null ? "" : animal.getEarTag();
        String typeText = exitTypeText(animal.getExitType());
        String date = change.monthDay(animal.getExitDate());
        String penName = change.penName(animal.getPenId());
        String locator = penName + " · " + earTag + " · " + date + " " + typeText;
        String title = typeText + " · " + earTag;
        String subtitle = change.farmName(animal.getFarmId()) + " · " + date + " · "
                + change.userName(animal.getExitBy()) + "登记";
        int version = animal.getVersion() == null ? 0 : animal.getVersion();
        return new ChangeRecord(BizType.BREEDING_EXIT, animal.getId(), animal.getFarmId(), null, animal.getPenId(),
                locator, title, subtitle, String.valueOf(version), false, false);
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        return true;
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        BreedingAnimal animal = change.animal(record.bizId());
        if (animal == null) {
            throw BreedingErrors.notFound();
        }
        String typeText = exitTypeText(animal.getExitType());
        String reason = animal.getExitReason() == null ? "" : animal.getExitReason();
        List<ChangeField> fields = List.of(
                new ChangeField(FIELD_EXIT_DATE, "日期",
                        animal.getExitDate() == null ? null : BeijingTime.format(animal.getExitDate()), null,
                        ChangeField.DATE, List.of(), null, null, List.of(), null),
                new ChangeField(FIELD_REASON, "原因", reason, null, ChangeField.TEXT, List.of(), null,
                        java.math.BigDecimal.valueOf(REASON_MAX), List.of(), null));
        return new ChangeForm(
                List.of(new ChangeCardItem("类型", typeText),
                        new ChangeCardItem("原因", reason)),
                fields, List.of(),
                "死亡淘汰会影响在档种畜数和配种选择，需要管理员审核。审核期间原记录照常有效。",
                false, true,
                "审核通过后撤销这次死亡淘汰登记，种畜恢复到登记前的状态，并重新计算在档种畜数与配种选择。",
                List.of("在档种畜数", "配种选择"));
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType == ChangeType.DELETE) {
            throw BreedingErrors.stateChanged("这条记录暂不能删除");
        }
        if (changeType == ChangeType.VOID) {
            return ChangeValidation.withoutField(List.of(ChangeImpact.STATS),
                    List.of("在档种畜数", "配种选择"));
        }
        BreedingChangeSupport.requireField(form, fieldCode);
        BreedingAnimal animal = change.animal(record.bizId());
        if (animal == null) {
            throw BreedingErrors.notFound();
        }
        return switch (fieldCode) {
            case FIELD_EXIT_DATE -> {
                LocalDate before = animal.getExitDate();
                LocalDate after = BreedingChangeSupport.dateValue(newValue, "请选择日期");
                if (after.isAfter(BeijingTime.today())) {
                    throw BreedingChangeSupport.fieldError("new_value", "日期不能晚于今天");
                }
                if (animal.getFilingDate() != null && after.isBefore(animal.getFilingDate())) {
                    throw BreedingChangeSupport.fieldError("new_value", "日期不能早于建档日期 "
                            + change.monthDay(animal.getFilingDate()));
                }
                if (after.equals(before)) {
                    BreedingChangeSupport.sameValue();
                }
                yield ChangeValidation.of(FIELD_EXIT_DATE, "日期", null,
                        BeijingTime.format(before), BeijingTime.format(before), BeijingTime.format(after),
                        BeijingTime.format(after), List.of(ChangeImpact.STATS), List.of());
            }
            case FIELD_REASON -> {
                String before = animal.getExitReason() == null ? "" : animal.getExitReason();
                String after = BreedingChangeSupport.textValue(newValue, "原因", REASON_MAX);
                if (after.equals(before)) {
                    BreedingChangeSupport.sameValue();
                }
                yield ChangeValidation.of(FIELD_REASON, "原因", null, before, before, after, after,
                        List.of(ChangeImpact.STATS), List.of());
            }
            default -> throw BreedingChangeSupport.fieldError("field_code", "请选择要修改的项目");
        };
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        BreedingAnimal animal = change.animal(record.bizId());
        if (animal == null) {
            throw BreedingErrors.stateChanged("原种畜档案不存在，请驳回");
        }
        if (ChangeType.VOID.name().equals(request.getChangeType())
                && !BreedingCodes.exited(animal.getStatus())) {
            throw BreedingErrors.stateChanged(animal.getEarTag() + " 已不是在档死亡淘汰状态，请驳回");
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        BreedingAnimal animal = change.animal(record.bizId());
        if (animal == null) {
            throw BreedingErrors.notFound();
        }
        boolean voidApply = ChangeType.VOID.name().equals(request.getChangeType());
        Map<String, Object> beforeValues = new LinkedHashMap<>();
        Map<String, Object> afterValues = new LinkedHashMap<>();
        if (voidApply) {
            if (animal.getStatusBeforeExit() == null) {
                throw BreedingErrors.stateChanged("缺少登记前状态，不能撤销这次死亡淘汰");
            }
            beforeValues.put("status", BreedingChangeSupport.value("状态", exitTypeText(animal.getExitType())));
            afterValues.put("status", BreedingChangeSupport.value("状态",
                    BreedingCodes.statusText(animal.getStatusBeforeExit())));
            animal.setStatus(animal.getStatusBeforeExit());
            animal.setStatusBeforeExit(null);
            animal.setExitType(null);
            animal.setExitDate(null);
            animal.setExitReason(null);
            animal.setExitBy(null);
            animal.setExitAt(null);
            animal.setUpdatedBy(adminId);
            if (breedingAnimalMapper.updateById(animal) == 0) {
                throw BreedingErrors.versionConflict();
            }
            clearCycleExit(animal);
        } else {
            String fieldCode = request.getFieldCode();
            String value = ChangeRequestJson.afterValue(request.getAfterJson(), fieldCode);
            if (FIELD_EXIT_DATE.equals(fieldCode) && value != null) {
                LocalDate date = BreedingChangeSupport.dateValue(value, "请选择日期");
                beforeValues.put(FIELD_EXIT_DATE, BreedingChangeSupport.value("日期",
                        BeijingTime.format(animal.getExitDate())));
                afterValues.put(FIELD_EXIT_DATE, BreedingChangeSupport.value("日期", BeijingTime.format(date)));
                animal.setExitDate(date);
            } else if (FIELD_REASON.equals(fieldCode) && value != null) {
                String reason = BreedingChangeSupport.textValue(value, "原因", REASON_MAX);
                beforeValues.put(FIELD_REASON, BreedingChangeSupport.value("原因", animal.getExitReason()));
                afterValues.put(FIELD_REASON, BreedingChangeSupport.value("原因", reason));
                animal.setExitReason(reason);
            }
            animal.setUpdatedBy(adminId);
            if (breedingAnimalMapper.updateById(animal) == 0) {
                throw BreedingErrors.versionConflict();
            }
        }
        change.logApply(request, BizType.BREEDING_EXIT, animal.getId(), animal.getEarTag(), animal.getFarmId(),
                animal.getEarTag(), beforeValues, afterValues);
        return new ChangeApplyResult(2, voidApply ? "已恢复种畜在档状态与配种选择" : "已更新死亡淘汰信息");
    }

    /** 撤销登记时清空当前周期因 EXIT 产生的结束信息 */
    private void clearCycleExit(BreedingAnimal animal) {
        if (animal.getCurrentCycleId() == null) {
            return;
        }
        BreedingCycle cycle = change.cycle(animal.getCurrentCycleId());
        if (cycle == null || !"EXIT".equals(cycle.getEndReason())) {
            return;
        }
        cycle.setEndDate(null);
        cycle.setEndReason(null);
        TenantSkip.run(() -> breedingCycleMapper.updateById(cycle));
    }

    private static String exitTypeText(String exitType) {
        return BreedingCodes.exitTypeText(exitType) == null ? "死亡 / 淘汰" : BreedingCodes.exitTypeText(exitType);
    }
}
