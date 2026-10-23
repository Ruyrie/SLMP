package com.zhimu.server.biz.inspect.support.change;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
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
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import com.zhimu.server.biz.inspect.mapper.InspectReferenceMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionItemResultMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.support.InspectRules;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 巡检记录（INSPECTION）的修改申请适配：超过 3 天的已巡记录改写异常头数、说明、手动标记与执行人。
 * <p>
 * 表单与原记录卡与 App Mock（module_inspect change_form_inspection.json）一致：主修改项 abnormal_count
 * （异常头数，单位“头”），只影响巡检统计，不涉及存栏、库存与成本。
 */
@Component
@RequiredArgsConstructor
public class InspectionChangeHandler implements ChangeRequestHandler {

    public static final String FIELD_ABNORMAL_COUNT = "abnormal_count";
    private static final String ABNORMAL_SUMMARY_PATTERN = "^异常\\s*(\\d+)\\s*头$";
    private static final Pattern ABNORMAL_SUMMARY = Pattern.compile(ABNORMAL_SUMMARY_PATTERN);
    private static final int DEFAULT_MAX_COUNT = 200;

    private final InspectionMapper inspectionMapper;
    private final InspectionItemResultMapper itemResultMapper;
    private final InspectReferenceMapper referenceMapper;
    private final AccountQueryService accountQueryService;
    private final OperationLogRecorder operationLogRecorder;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.INSPECTION);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.INSPECTION);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        if (bizId == null) {
            return null;
        }
        Inspection inspection = TenantSkip.call(() -> inspectionMapper.selectByIdIncludingDeleted(bizId));
        if (inspection == null) {
            return null;
        }
        String penName = penName(inspection.getPenId());
        String dateText = monthDay(inspection.getInspectDate());
        String registrant = userName(inspection.getRegistrantId());
        String locator = (penName == null ? "栏舍 " + inspection.getPenId() : penName) + " · " + dateText + "巡检";
        String title = "巡检记录 · " + (penName == null ? "栏舍 " + inspection.getPenId() : penName);
        String subtitle = dateText + " · " + (registrant == null ? "系统" : registrant)
                + (Boolean.TRUE.equals(inspection.getIsBackfill()) ? "补录" : "登记");
        boolean voided = InspectRules.CANCELLED.equals(inspection.getInspectStatus());
        return new ChangeRecord(BizType.INSPECTION, inspection.getId(), inspection.getFarmId(),
                inspection.getBatchId(), inspection.getPenId(), locator, title, subtitle,
                versionOf(inspection), Boolean.TRUE.equals(inspection.getDeleted()), voided);
    }

    /**
     * 巡检日期第 3 天 24:00 前可直接修改；之后必须走修改申请。未登记（PENDING / MISSED）的记录没有可改内容，
     * 由来源模块的登记 / 补录接口处理。
     */
    @Override
    public boolean requiresRequest(ChangeRecord record) {
        Inspection inspection = load(record.bizId());
        if (inspection == null || !InspectRules.DONE.equals(inspection.getInspectStatus())) {
            return false;
        }
        return !InspectRules.withinEditWindow(inspection.getInspectDate());
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        Inspection inspection = load(record.bizId());
        if (inspection == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        List<InspectionItemResult> results = itemResults(inspection.getId());
        int abnormal = abnormalCount(inspection, results);
        int max = maxCount(inspection);
        List<ChangeField> fields = List.of(new ChangeField(FIELD_ABNORMAL_COUNT, "异常头数",
                String.valueOf(abnormal), "头", ChangeField.NUMBER, List.of(),
                BigDecimal.ZERO, BigDecimal.valueOf(max), List.of("巡检统计"),
                inspection.getStockSnapshot() == null ? null : "存栏 " + inspection.getStockSnapshot() + " 头"));
        List<ChangeCardItem> card = new ArrayList<>();
        card.add(new ChangeCardItem("结果", abnormal > 0 ? abnormal + " 项异常" : results.size() + " 项正常"));
        card.add(new ChangeCardItem("位置", InspectRules.locationText(inspection.getLocationStatus())));
        return new ChangeForm(card, fields,
                List.of("修改异常头数会同步更新当天巡检统计"),
                "超过 3 天的巡检记录需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            return ChangeValidation.withoutField(List.of(ChangeImpact.STATS), List.of("巡检统计"));
        }
        ChangeRequestJson.requireField(form, fieldCode);
        if (!FIELD_ABNORMAL_COUNT.equals(fieldCode)) {
            throw fieldError("field_code", "请选择要修改的项目");
        }
        ChangeField field = form.fieldOf(fieldCode);
        String value = newValue == null ? "" : newValue.trim();
        if (value.isEmpty()) {
            throw fieldError("new_value", "请填写新值");
        }
        int count;
        try {
            count = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw fieldError("new_value", "请填写整数");
        }
        if (count < 0) {
            throw fieldError("new_value", "异常头数不能小于 0");
        }
        if (field.max() != null && count > field.max().intValue()) {
            throw fieldError("new_value", "异常头数不能超过 " + field.max().intValue() + " 头");
        }
        int before = parseInt(field.value());
        if (count == before) {
            throw fieldError("new_value", "新值与原值相同，不需要提交");
        }
        return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(),
                field.value(), ChangeRequestJson.display(field.value(), field.valueUnit()),
                String.valueOf(count), ChangeRequestJson.display(String.valueOf(count), field.valueUnit()),
                List.of(ChangeImpact.STATS), List.of("巡检统计"));
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        Inspection inspection = load(record.bizId());
        if (inspection == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        ChangeType changeType = changeTypeOf(request.getChangeType());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        int recalcCount = 0;
        if (changeType == ChangeType.UPDATE) {
            int oldCount = abnormalCount(inspection, itemResults(inspection.getId()));
            String raw = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_ABNORMAL_COUNT);
            if (raw == null && FIELD_ABNORMAL_COUNT.equals(request.getFieldCode())) {
                raw = request.getAfterDisplay();
            }
            int newCount = parseAfterCount(raw);
            applyAbnormalCount(inspection, newCount, adminId, request.getReason());
            before.put(FIELD_ABNORMAL_COUNT, Map.of("name", "异常头数", "display", oldCount + " 头"));
            after.put(FIELD_ABNORMAL_COUNT, Map.of("name", "异常头数", "display", newCount + " 头"));
            recalcCount = 1;
        } else if (changeType == ChangeType.DELETE) {
            inspectionMapper.deleteById(inspection.getId());
            before.put("record_status", Map.of("name", "记录状态", "display", "有效"));
            after.put("record_status", Map.of("name", "记录状态", "display", "已删除"));
            recalcCount = 1;
        } else {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "巡检记录不支持作废");
        }
        writeLog(request, record, adminId, changeType, before, after);
        return new ChangeApplyResult(recalcCount, "已重新计算巡检统计");
    }

    private void applyAbnormalCount(Inspection inspection, int newCount, Long adminId, String reason) {
        inspection.setIsAbnormal(newCount > 0);
        if (newCount > 0) {
            inspection.setAbnormalSource("MANUAL");
            inspection.setAbnormalSummary("异常 " + newCount + " 头");
            inspection.setIsManualMarked(true);
            inspection.setManualReason(reason);
            inspection.setManualMarkedBy(adminId);
            inspection.setManualMarkedAt(BeijingTime.now());
        } else {
            inspection.setAbnormalSource(null);
            inspection.setAbnormalSummary(null);
            inspection.setIsManualMarked(false);
            inspection.setManualReason(null);
            inspection.setManualMarkedBy(null);
            inspection.setManualMarkedAt(null);
        }
        inspection.setUpdatedBy(adminId);
        inspectionMapper.updateById(inspection);
    }

    private void writeLog(ChangeRequest request, ChangeRecord record, Long adminId, ChangeType changeType,
                          Map<String, Object> before, Map<String, Object> after) {
        if (operationLogRecorder == null) {
            return;
        }
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(request.getSubmittedBy())
                .operatorRole(request.getSubmitterRole())
                .opType("RECORD_EDIT")
                .opAction(changeType.name())
                .result("SUCCESS")
                .bizType(BizType.INSPECTION.name())
                .bizId(request.getBizId())
                .targetName(record.recordTitle())
                .farmId(request.getFarmId())
                .reason(request.getReason())
                .before(before)
                .after(after)
                .changeRequestId(request.getId())
                .build());
    }

    private Inspection load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> inspectionMapper.selectByIdIncludingDeleted(bizId));
    }

    private List<InspectionItemResult> itemResults(Long inspectionId) {
        List<InspectionItemResult> rows = itemResultMapper.selectByInspectionId(inspectionId);
        return rows == null ? List.of() : rows;
    }

    /** 异常头数：优先取手动标记时写入 abnormal_summary 的“异常 N 头”，否则按异常巡检项计数 */
    private int abnormalCount(Inspection inspection, List<InspectionItemResult> results) {
        Integer fromSummary = summaryCount(inspection == null ? null : inspection.getAbnormalSummary());
        if (fromSummary != null) {
            return fromSummary;
        }
        int count = 0;
        for (InspectionItemResult row : results) {
            if (Boolean.TRUE.equals(row.getOptionAbnormal())) {
                count++;
            }
        }
        return count;
    }

    private static Integer summaryCount(String summary) {
        if (summary == null) {
            return null;
        }
        Matcher matcher = ABNORMAL_SUMMARY.matcher(summary.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int maxCount(Inspection inspection) {
        Integer snapshot = inspection == null ? null : inspection.getStockSnapshot();
        return snapshot == null || snapshot <= 0 ? DEFAULT_MAX_COUNT : snapshot;
    }

    private String penName(Long penId) {
        return penId == null ? null : referenceMapper.selectPenName(penId);
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AccountBrief brief = accountQueryService.getBrief(userId);
        return brief == null ? null : brief.name();
    }

    private static String monthDay(LocalDate date) {
        return date == null ? "" : date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private static String versionOf(Inspection inspection) {
        if (inspection.getVersion() != null) {
            return String.valueOf(inspection.getVersion());
        }
        return inspection.getUpdatedAt() == null ? "0"
                : String.valueOf(inspection.getUpdatedAt().atZone(BeijingTime.ZONE).toInstant().toEpochMilli());
    }

    private static ChangeType changeTypeOf(String code) {
        ChangeType type = ChangeRequestJson.parseChangeType(code);
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "修改类型不正确");
        }
        return type;
    }

    private static int parseAfterCount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写新值");
        }
        String text = raw.trim();
        int space = text.indexOf(' ');
        if (space > 0) {
            text = text.substring(0, space);
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新值不正确");
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message)
                .with("field_errors", List.of(new FieldErrorVO(field, message)));
    }
}
