package com.zhimu.server.biz.breeding.support.change;

import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.mapper.FarrowingRecordMapper;
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
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产仔记录（FARROWING，biz_id = 产仔记录 ID，record_version = 产仔记录 version）。
 * <p>
 * 字段与 App Mock（change_form_farrowing.json）一致：farrow_date 产仔日期、total_born 总产仔数、
 * live_born 活仔数、stillborn 死胎数；活仔 + 死胎 = 总产仔，改一项时联动另一项。
 * 已转为新批次或已断奶的记录走修改申请；审核通过按活仔差额调整自繁批次存栏，改日期时同步批次入栏日期。
 */
@Component
@RequiredArgsConstructor
public class FarrowingChangeHandler implements ChangeRequestHandler {

    private static final String FIELD_FARROW_DATE = "farrow_date";
    private static final String FIELD_TOTAL_BORN = "total_born";
    private static final String FIELD_LIVE_BORN = "live_born";
    private static final String FIELD_STILLBORN = "stillborn";
    private static final int MAX_BORN = 50;

    private final BreedingChangeSupport change;
    private final FarrowingRecordMapper farrowingRecordMapper;
    private final SelfBredBatchService selfBredBatchService;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.FARROWING);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.FARROWING);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        FarrowingRecord record = change.farrowing(bizId);
        if (record == null) {
            return null;
        }
        BreedingAnimal animal = change.animal(record.getAnimalId());
        String earTag = animal == null || animal.getEarTag() == null ? "" : animal.getEarTag();
        String penName = change.penName(record.getTargetPenId());
        String date = change.monthDay(record.getFarrowDate());
        String locator = (penName.isEmpty() ? "产房" : penName) + " · " + earTag + " · " + date + " 产仔";
        String title = "产仔 · " + earTag;
        String subtitle = change.farmName(record.getFarmId()) + " · " + date + " · "
                + change.userName(record.getCreatedBy()) + "登记";
        int version = record.getVersion() == null ? 0 : record.getVersion();
        return new ChangeRecord(BizType.FARROWING, record.getId(), record.getFarmId(), record.getBatchId(),
                record.getTargetPenId(), locator, title, subtitle, String.valueOf(version), false, false);
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        FarrowingRecord fresh = change.farrowing(record.bizId());
        if (fresh == null) {
            return true;
        }
        if (fresh.getBatchId() != null) {
            return true;
        }
        BreedingCycle cycle = change.cycle(fresh.getCycleId());
        return cycle != null && cycle.getWeaningId() != null;
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        FarrowingRecord fresh = change.farrowing(record.bizId());
        if (fresh == null) {
            throw BreedingErrors.notFound();
        }
        BreedingAnimal animal = change.animal(fresh.getAnimalId());
        String earTag = animal == null || animal.getEarTag() == null ? "" : animal.getEarTag();
        String parity = fresh.getParityNo() == null ? "0" : String.valueOf(fresh.getParityNo());
        List<ChangeCardItem> card = new ArrayList<>();
        card.add(new ChangeCardItem("母猪", earTag + " · "
                + change.breedName(animal == null ? null : animal.getBreedId()) + " · 第 " + parity + " 胎"));
        card.add(new ChangeCardItem("总产仔", BreedingChangeSupport.intOf(fresh.getTotalBorn()) + " 头"));
        card.add(new ChangeCardItem("活仔", BreedingChangeSupport.intOf(fresh.getLiveBorn()) + " 头"));
        card.add(new ChangeCardItem("死胎", BreedingChangeSupport.intOf(fresh.getStillborn()) + " 头"));
        String batchNo = change.batchNo(fresh.getBatchId());
        if (batchNo != null) {
            card.add(new ChangeCardItem("新批次", batchNo));
        }
        List<ChangeField> fields = List.of(
                new ChangeField(FIELD_FARROW_DATE, "产仔日期",
                        fresh.getFarrowDate() == null ? null : BeijingTime.format(fresh.getFarrowDate()), null,
                        ChangeField.DATE, List.of(), null, null,
                        List.of("日龄", "饲喂与免疫计划"), null),
                new ChangeField(FIELD_TOTAL_BORN, "总产仔数",
                        String.valueOf(BreedingChangeSupport.intOf(fresh.getTotalBorn())), "头", ChangeField.NUMBER,
                        List.of(), BigDecimal.ONE, BigDecimal.valueOf(MAX_BORN), List.of(), null),
                new ChangeField(FIELD_LIVE_BORN, "活仔数",
                        String.valueOf(BreedingChangeSupport.intOf(fresh.getLiveBorn())), "头", ChangeField.NUMBER,
                        List.of(), BigDecimal.ZERO, BigDecimal.valueOf(MAX_BORN), List.of("存栏", "繁育统计"),
                        "活仔 + 死胎须等于总产仔"),
                new ChangeField(FIELD_STILLBORN, "死胎数",
                        String.valueOf(BreedingChangeSupport.intOf(fresh.getStillborn())), "头", ChangeField.NUMBER,
                        List.of(), BigDecimal.ZERO, BigDecimal.valueOf(MAX_BORN), List.of(), null));
        return new ChangeForm(card, fields, List.of(),
                "这条产仔记录已转为新批次，修改会影响存栏和统计，需要管理员审核。审核期间原记录照常有效。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            throw BreedingErrors.stateChanged("这条产仔记录暂不能作废或删除");
        }
        BreedingChangeSupport.requireField(form, fieldCode);
        FarrowingRecord fresh = change.farrowing(record.bizId());
        if (fresh == null) {
            throw BreedingErrors.notFound();
        }
        int total = BreedingChangeSupport.intOf(fresh.getTotalBorn());
        int live = BreedingChangeSupport.intOf(fresh.getLiveBorn());
        int still = BreedingChangeSupport.intOf(fresh.getStillborn());
        return switch (fieldCode) {
            case FIELD_FARROW_DATE -> {
                LocalDate before = fresh.getFarrowDate();
                LocalDate after = BreedingChangeSupport.dateValue(newValue, "请选择产仔日期");
                if (after.isAfter(BeijingTime.today())) {
                    throw BreedingChangeSupport.fieldError("new_value", "产仔日期不能晚于今天");
                }
                BreedingCycle cycle = change.cycle(fresh.getCycleId());
                if (cycle != null && cycle.getFirstMatingDate() != null
                        && after.isBefore(cycle.getFirstMatingDate())) {
                    throw BreedingChangeSupport.fieldError("new_value", "产仔日期不能早于配种日期 "
                            + change.monthDay(cycle.getFirstMatingDate()));
                }
                if (after.equals(before)) {
                    BreedingChangeSupport.sameValue();
                }
                yield ChangeValidation.of(FIELD_FARROW_DATE, "产仔日期", null,
                        BeijingTime.format(before), BeijingTime.format(before), BeijingTime.format(after),
                        BeijingTime.format(after), List.of(ChangeImpact.STATS),
                        List.of("日龄", "饲喂与免疫计划"));
            }
            case FIELD_TOTAL_BORN -> {
                int after = BreedingChangeSupport.intValue(newValue, "总产仔数", 1, MAX_BORN);
                if (after == total) {
                    BreedingChangeSupport.sameValue();
                }
                if (after < live) {
                    throw BreedingChangeSupport.fieldError("new_value", "总产仔数不能少于活仔数 " + live + " 头");
                }
                yield countValidation(FIELD_TOTAL_BORN, "总产仔数", total, after, List.of(ChangeImpact.STATS),
                        List.of());
            }
            case FIELD_LIVE_BORN -> {
                int after = BreedingChangeSupport.intValue(newValue, "活仔数", 0, MAX_BORN);
                if (after == live) {
                    BreedingChangeSupport.sameValue();
                }
                if (after > total) {
                    throw BreedingChangeSupport.fieldError("new_value", "活仔数不能多于总产仔 " + total + " 头");
                }
                yield countValidation(FIELD_LIVE_BORN, "活仔数", live, after,
                        List.of(ChangeImpact.STOCK, ChangeImpact.STATS), List.of("存栏", "繁育统计"));
            }
            case FIELD_STILLBORN -> {
                int after = BreedingChangeSupport.intValue(newValue, "死胎数", 0, MAX_BORN);
                if (after == still) {
                    BreedingChangeSupport.sameValue();
                }
                if (after > total) {
                    throw BreedingChangeSupport.fieldError("new_value", "死胎数不能多于总产仔 " + total + " 头");
                }
                yield countValidation(FIELD_STILLBORN, "死胎数", still, after, List.of(ChangeImpact.STATS),
                        List.of());
            }
            default -> throw BreedingChangeSupport.fieldError("field_code", "请选择要修改的项目");
        };
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        FarrowingRecord fresh = change.farrowing(record.bizId());
        if (fresh == null) {
            throw BreedingErrors.stateChanged("原产仔记录已不存在，请驳回");
        }
        Values values = valuesOf(fresh, request);
        if (values.liveDelta() != 0) {
            checkBatchStock(fresh.getBatchId(), values.liveDelta());
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        FarrowingRecord fresh = change.farrowing(record.bizId());
        if (fresh == null) {
            throw BreedingErrors.notFound();
        }
        Values values = valuesOf(fresh, request);
        fresh.setFarrowDate(values.farrowDate());
        fresh.setTotalBorn(values.totalBorn());
        fresh.setLiveBorn(values.liveBorn());
        fresh.setStillborn(values.stillborn());
        fresh.setUpdatedBy(adminId);
        if (farrowingRecordMapper.updateById(fresh) == 0) {
            throw BreedingErrors.versionConflict();
        }
        if (fresh.getBatchId() != null) {
            LocalDate batchDate = FIELD_FARROW_DATE.equals(request.getFieldCode()) ? values.farrowDate() : null;
            selfBredBatchService.adjustEntry(fresh.getBatchId(), values.liveDelta(), batchDate);
        }
        Map<String, Object> beforeValues = displays(request.getBeforeJson(), "before");
        Map<String, Object> afterValues = displays(request.getAfterJson(), "after");
        BreedingAnimal animal = change.animal(fresh.getAnimalId());
        String earTag = animal == null || animal.getEarTag() == null ? "产仔 " + fresh.getId() : animal.getEarTag();
        change.logApply(request, BizType.FARROWING, fresh.getId(), earTag, fresh.getFarmId(),
                change.batchNo(fresh.getBatchId()), beforeValues, afterValues);
        String summary = fresh.getBatchId() == null
                ? "已重新计算繁育统计" : "已重新计算批次存栏与繁育统计";
        return new ChangeApplyResult(fresh.getBatchId() == null ? 2 : 3, summary);
    }

    private void checkBatchStock(Long batchId, int delta) {
        if (batchId == null || delta >= 0) {
            return;
        }
        BatchInfo batch = change.batchInfo(batchId);
        int current = batch == null || batch.currentCount() == null ? 0 : batch.currentCount();
        if (current + delta < 0) {
            throw new BizException(ErrorCode.COUNT_EXCEEDED, "在栏数不足，无法按申请修改");
        }
    }

    private static ChangeValidation countValidation(String fieldCode, String fieldName, int before, int after,
                                                    List<ChangeImpact> impacts, List<String> tags) {
        return ChangeValidation.of(fieldCode, fieldName, "头", String.valueOf(before),
                BreedingChangeSupport.display(String.valueOf(before), "头"), String.valueOf(after),
                BreedingChangeSupport.display(String.valueOf(after), "头"), impacts, tags);
    }

    /** 按申请字段把产仔记录换算成新的总数 / 活仔 / 死胎、日期与活仔差额 */
    private Values valuesOf(FarrowingRecord fresh, ChangeRequest request) {
        int total = BreedingChangeSupport.intOf(fresh.getTotalBorn());
        int live = BreedingChangeSupport.intOf(fresh.getLiveBorn());
        int still = BreedingChangeSupport.intOf(fresh.getStillborn());
        LocalDate date = fresh.getFarrowDate();
        String fieldCode = request.getFieldCode();
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), fieldCode);
        if (fieldCode != null && value != null) {
            switch (fieldCode) {
                case FIELD_FARROW_DATE -> date = BreedingChangeSupport.dateValue(value, "请选择产仔日期");
                case FIELD_TOTAL_BORN -> {
                    total = BreedingChangeSupport.intValue(value, "总产仔数", 1, MAX_BORN);
                    still = total - live;
                }
                case FIELD_LIVE_BORN -> {
                    live = BreedingChangeSupport.intValue(value, "活仔数", 0, MAX_BORN);
                    still = total - live;
                }
                case FIELD_STILLBORN -> {
                    still = BreedingChangeSupport.intValue(value, "死胎数", 0, MAX_BORN);
                    live = total - still;
                }
                default -> {
                }
            }
        }
        if (total < 1 || live < 0 || still < 0 || live + still != total) {
            throw BreedingChangeSupport.fieldError("new_value", "活仔 + 死胎须等于总产仔");
        }
        return new Values(date, total, live, still, live - BreedingChangeSupport.intOf(fresh.getLiveBorn()));
    }

    /** before_json / after_json 的显示值（字段编码 → {name, display}） */
    static Map<String, Object> displays(String json, String section) {
        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode node = ChangeRequestJson.readTree(json).path(section);
        var fields = node.propertyNames().iterator();
        while (fields.hasNext()) {
            String key = fields.next();
            JsonNode field = node.path(key);
            String name = field.path("name").isMissingNode() ? key : field.path("name").asString();
            JsonNode displayNode = field.path("display");
            String display = displayNode.isMissingNode() ? field.path("value").asString() : displayNode.asString();
            values.put(key, BreedingChangeSupport.value(name, display));
        }
        return values;
    }

    private record Values(LocalDate farrowDate, int totalBorn, int liveBorn, int stillborn, int liveDelta) {
    }
}
