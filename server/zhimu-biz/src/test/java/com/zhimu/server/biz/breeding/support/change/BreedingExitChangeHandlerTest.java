package com.zhimu.server.biz.breeding.support.change;

import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 种畜死亡淘汰修改申请 handler：字段与 App Mock（change_form_exit.json）一致。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreedingExitChangeHandlerTest {

    @Mock private BreedingChangeSupport change;
    @Mock private BreedingAnimalMapper breedingAnimalMapper;
    @Mock private BreedingCycleMapper breedingCycleMapper;

    private BreedingExitChangeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BreedingExitChangeHandler(change, breedingAnimalMapper, breedingCycleMapper);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(change.animal(4102L)).thenReturn(animal());
        when(change.penName(2006L)).thenReturn("母猪舍");
        when(change.farmName(1001L)).thenReturn("东山猪场");
        when(change.userName(10001L)).thenReturn("王建国");
        when(change.monthDay(LocalDate.of(2026, 9, 15))).thenReturn("9月15日");
        when(change.monthDay(LocalDate.of(2024, 6, 1))).thenReturn("6月1日");
        when(change.cycle(5161L)).thenReturn(cycle());
        when(breedingAnimalMapper.updateById(any(BreedingAnimal.class))).thenReturn(1);
        when(breedingCycleMapper.updateById(any(BreedingCycle.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsExitTypeAndBreedingModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.BREEDING_EXIT);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.BREEDING);
    }

    @Test
    void loadRecordOnlyForExitedAnimal() {
        ChangeRecord record = handler.loadRecord(BizType.BREEDING_EXIT, 4102L);

        assertThat(record).isNotNull();
        assertThat(record.bizId()).isEqualTo(4102L);
        assertThat(record.farmId()).isEqualTo(1001L);
        assertThat(record.penId()).isEqualTo(2006L);
        assertThat(record.recordLocator()).isEqualTo("母猪舍 · 0102 · 9月15日 淘汰");
        assertThat(record.recordTitle()).isEqualTo("淘汰 · 0102");
        assertThat(record.recordSubtitle()).isEqualTo("东山猪场 · 9月15日 · 王建国登记");
        assertThat(record.recordVersion()).isEqualTo("12");

        BreedingAnimal alive = animal();
        alive.setExitType(null);
        when(change.animal(4109L)).thenReturn(alive);
        assertThat(handler.loadRecord(BizType.BREEDING_EXIT, 4109L)).isNull();
        assertThat(handler.loadRecord(BizType.BREEDING_EXIT, 9999L)).isNull();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.BREEDING_EXIT, 4102L));

        assertThat(form.recordCard()).extracting(ChangeCardItem::label).containsExactly("类型", "原因");
        assertThat(form.recordCard()).extracting(ChangeCardItem::value)
                .containsExactly("淘汰", "连续两胎产仔少，繁殖性能下降");
        ChangeField date = form.fieldOf("exit_date");
        assertThat(date.fieldName()).isEqualTo("日期");
        assertThat(date.inputType()).isEqualTo(ChangeField.DATE);
        assertThat(date.value()).isEqualTo("2026-09-15");
        ChangeField reason = form.fieldOf("reason");
        assertThat(reason.fieldName()).isEqualTo("原因");
        assertThat(reason.inputType()).isEqualTo(ChangeField.TEXT);
        assertThat(reason.value()).isEqualTo("连续两胎产仔少，繁殖性能下降");
        assertThat(reason.max()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(form.voidAllowed()).isTrue();
        assertThat(form.deleteAllowed()).isFalse();
        assertThat(form.voidNotice()).isNotBlank();
        assertThat(form.voidRecalcTags()).containsExactly("在档种畜数", "配种选择");
    }

    @Test
    void requiresRequestAlwaysTrue() {
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.BREEDING_EXIT, 4102L))).isTrue();
    }

    @Test
    void validateChangeSupportsDateReasonAndVoid() {
        ChangeRecord record = handler.loadRecord(BizType.BREEDING_EXIT, 4102L);
        ChangeForm form = handler.describeForm(record);

        ChangeValidation date = handler.validateChange(record, form, ChangeType.UPDATE, "exit_date", "2026-09-18");
        assertThat(date.afterValue()).isEqualTo("2026-09-18");

        ChangeValidation reason = handler.validateChange(record, form, ChangeType.UPDATE, "reason", "难产");
        assertThat(reason.afterValue()).isEqualTo("难产");

        ChangeValidation voided = handler.validateChange(record, form, ChangeType.VOID, null, null);
        assertThat(voided.hasField()).isFalse();
        assertThat(voided.recalcTags()).containsExactly("在档种畜数", "配种选择");

        assertThat(messageOf(record, form, ChangeType.UPDATE, "exit_date", "2026-10-25"))
                .contains("日期不能晚于今天");
        assertThat(messageOf(record, form, ChangeType.UPDATE, "exit_date", "2024-05-01"))
                .contains("日期不能早于建档日期");
        assertThat(messageOf(record, form, ChangeType.UPDATE, "reason", ""))
                .contains("请填写原因");
        assertThat(messageOf(record, form, ChangeType.UPDATE, "reason", "原".repeat(51)))
                .contains("最多 50 字");
    }

    @Test
    void applyUpdateWritesRecordAndLog() {
        ChangeRecord record = handler.loadRecord(BizType.BREEDING_EXIT, 4102L);

        ChangeApplyResult result = handler.apply(request("UPDATE", "reason", "难产"), record, 9001L);

        assertThat(result.recalcSummary()).contains("死亡淘汰");
        ArgumentCaptor<BreedingAnimal> captor = ArgumentCaptor.forClass(BreedingAnimal.class);
        verify(breedingAnimalMapper).updateById(captor.capture());
        assertThat(captor.getValue().getExitReason()).isEqualTo("难产");
        assertThat(captor.getValue().getStatus()).isEqualTo("CULLED");
        verify(change).logApply(any(), eq(BizType.BREEDING_EXIT), eq(4102L), anyString(), eq(1001L),
                any(), any(), any());
    }

    @Test
    void applyVoidRestoresStatusAndClearsCycleExit() {
        ChangeRecord record = handler.loadRecord(BizType.BREEDING_EXIT, 4102L);

        ChangeApplyResult result = handler.apply(request("VOID", null, null), record, 9001L);

        assertThat(result.recalcSummary()).contains("恢复");
        ArgumentCaptor<BreedingAnimal> captor = ArgumentCaptor.forClass(BreedingAnimal.class);
        verify(breedingAnimalMapper).updateById(captor.capture());
        BreedingAnimal saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("EMPTY");
        assertThat(saved.getExitType()).isNull();
        assertThat(saved.getExitDate()).isNull();
        assertThat(saved.getExitReason()).isNull();
        assertThat(saved.getStatusBeforeExit()).isNull();
        ArgumentCaptor<BreedingCycle> cycleCaptor = ArgumentCaptor.forClass(BreedingCycle.class);
        verify(breedingCycleMapper).updateById(cycleCaptor.capture());
        assertThat(cycleCaptor.getValue().getEndDate()).isNull();
        assertThat(cycleCaptor.getValue().getEndReason()).isNull();
    }

    @Test
    void validateForApproveRejectsVoidWhenNoLongerExited() {
        BreedingAnimal alive = animal();
        alive.setStatus("EMPTY");
        when(change.animal(4102L)).thenReturn(alive);

        BizException error = (BizException) catchException(
                () -> handler.validateForApprove(request("VOID", null, null),
                        handler.loadRecord(BizType.BREEDING_EXIT, 4102L)));

        assertThat(error.getCode()).isEqualTo(40903);
    }

    @Test
    void deleteIsRejected() {
        ChangeRecord record = handler.loadRecord(BizType.BREEDING_EXIT, 4102L);
        ChangeForm form = handler.describeForm(record);

        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.DELETE, null, null));

        assertThat(error.getCode()).isEqualTo(40903);
        verify(breedingAnimalMapper, never()).deleteById(anyLong());
    }

    private String messageOf(ChangeRecord record, ChangeForm form, ChangeType type, String field, String value) {
        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, type, field, value));
        assertThat(error.getCode()).isEqualTo(40001);
        return error.getMessage();
    }

    private static ChangeRequest request(String changeType, String fieldCode, String value) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9001L);
        request.setChangeType(changeType);
        request.setFieldCode(fieldCode);
        request.setFarmId(1001L);
        request.setSubmittedBy(5001L);
        request.setSubmitterRole("OWNER");
        request.setReason("更正死亡淘汰登记");
        request.setBeforeJson("{\"before\":{}}");
        if (fieldCode == null) {
            request.setAfterJson("{\"after\":{}}");
        } else {
            request.setAfterJson("{\"after\":{\"" + fieldCode + "\":{\"name\":\"字段\",\"value\":\"" + value
                    + "\",\"display\":\"" + value + "\",\"unit\":null}}}");
        }
        return request;
    }

    private static BreedingAnimal animal() {
        BreedingAnimal animal = new BreedingAnimal();
        animal.setId(4102L);
        animal.setFarmId(1001L);
        animal.setEarTag("0102");
        animal.setBreedId(1201L);
        animal.setBirthDate(LocalDate.of(2023, 8, 15));
        animal.setFilingDate(LocalDate.of(2024, 6, 1));
        animal.setPenId(2006L);
        animal.setParity(6);
        animal.setStatus("CULLED");
        animal.setCurrentCycleId(5161L);
        animal.setStatusBeforeExit("EMPTY");
        animal.setExitType("CULL");
        animal.setExitDate(LocalDate.of(2026, 9, 15));
        animal.setExitReason("连续两胎产仔少，繁殖性能下降");
        animal.setExitBy(10001L);
        animal.setExitAt(LocalDateTime.of(2026, 9, 15, 10, 20));
        animal.setVersion(12);
        return animal;
    }

    private static BreedingCycle cycle() {
        BreedingCycle cycle = new BreedingCycle();
        cycle.setId(5161L);
        cycle.setFarmId(1001L);
        cycle.setAnimalId(4102L);
        cycle.setFirstMatingDate(LocalDate.of(2026, 1, 1));
        cycle.setEndDate(LocalDate.of(2026, 9, 15));
        cycle.setEndReason("EXIT");
        cycle.setStatus("FAILED");
        return cycle;
    }
}
