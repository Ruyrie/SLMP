package com.zhimu.server.biz.common.fixture;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
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
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.common.vo.OptionVO;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 测试用投喂记录 handler：内存中的一条 10月18日 晚餐投喂记录，验证 U01 表单、U02 提交、审核回写与 U14 留痕的完整链路。
 * 形态与 App Mock（change_form_feed_record.json）一致。
 */
public class TestFeedRecordHandler implements ChangeRequestHandler {

    public static final Long FARM_ID = 1001L;
    public static final Long RECORD_ID = 7001L;
    public static final Long BATCH_ID = 3001L;
    public static final Long PEN_ID = 2003L;
    public static final String LOCATOR = "3 号育肥舍 · ZM2610-031 · 10月18日 晚餐";
    public static final String TITLE = "投喂记录 · 3 号育肥舍";
    public static final String SUBTITLE = "ZM2610-031 · 10月18日 晚餐 · 李明登记";

    private final OperationLogRecorder recorder;

    private String feedId = "4001";
    private String feedName = "育肥全价料";
    private String actualQty = "140";
    private String version = "3";
    private boolean deleted;
    private boolean voided;

    public TestFeedRecordHandler(OperationLogRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.FEED_RECORD);
    }

    @Override
    public SourceModule sourceModule() {
        return SourceModule.FEEDING;
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        if (!RECORD_ID.equals(bizId)) {
            return null;
        }
        return new ChangeRecord(BizType.FEED_RECORD, RECORD_ID, FARM_ID, BATCH_ID, PEN_ID, LOCATOR, TITLE, SUBTITLE,
                version, deleted, voided);
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        return true;
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        List<ChangeField> fields = List.of(
                new ChangeField("feed_id", "饲料", feedName, null, ChangeField.SELECT,
                        List.of(OptionVO.of("4001", "育肥全价料"), OptionVO.of("4002", "保育料")),
                        null, null, List.of("饲料成本", "经营报表"), null),
                new ChangeField("actual_qty", "实际投喂量", actualQty, "千克", ChangeField.NUMBER,
                        List.of(), BigDecimal.ZERO, new BigDecimal("1000"),
                        List.of("头均采食量", "料肉比", "饲料成本", "经营报表"), "计划 160 千克"));
        return new ChangeForm(
                List.of(new ChangeCardItem("饲料", feedName),
                        new ChangeCardItem("实际投喂量", actualQty + " 千克"),
                        new ChangeCardItem("计划量", "160 千克")),
                fields,
                List.of("修改实际投喂量会同步调整饲料库存出库数量"),
                "这条记录会影响饲料库存和成本统计，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false,
                true,
                "审核通过后这条投喂记录作废，" + actualQty + " 千克育肥全价料退回库存；10月18日 晚餐的饲喂任务在补登期限内恢复为待投喂。",
                List.of("库存（退回饲料）", "成本", "统计"));
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType == ChangeType.VOID) {
            return ChangeValidation.withoutField(List.of(ChangeImpact.INVENTORY, ChangeImpact.COST, ChangeImpact.STATS),
                    List.of("库存（退回饲料）", "成本", "统计"));
        }
        ChangeField field = form.fieldOf(fieldCode);
        if (field == null) {
            throw fieldError("field_code", "请选择要修改的项目");
        }
        String value = newValue == null ? "" : newValue.trim();
        if (value.isEmpty()) {
            throw fieldError("new_value", "请填写新值");
        }
        String normalized;
        if (ChangeField.SELECT.equals(field.inputType())) {
            OptionVO matched = field.options().stream()
                    .filter(o -> value.equals(o.getLabel()) || value.equals(o.getValue()))
                    .findFirst()
                    .orElseThrow(() -> fieldError("new_value", "可选：育肥全价料、保育料"));
            normalized = matched.getValue();
        } else if (ChangeField.NUMBER.equals(field.inputType())) {
            BigDecimal number;
            try {
                number = new BigDecimal(value);
            } catch (NumberFormatException e) {
                throw fieldError("new_value", "请填写数字");
            }
            if (field.min() != null && number.compareTo(field.min()) < 0) {
                throw fieldError("new_value", "实际投喂量不能小于 " + field.min().stripTrailingZeros().toPlainString() + " 千克");
            }
            if (field.max() != null && number.compareTo(field.max()) > 0) {
                throw fieldError("new_value", "实际投喂量不能超过 " + field.max().stripTrailingZeros().toPlainString() + " 千克");
            }
            BigDecimal before = new BigDecimal(field.value());
            if (number.compareTo(before) == 0) {
                throw fieldError("new_value", "新值与原值相同，不需要提交");
            }
            normalized = number.stripTrailingZeros().toPlainString();
        } else {
            normalized = value;
        }
        String beforeDisplay = ChangeRequestJson.display(field.value(), field.valueUnit());
        String afterDisplay = ChangeRequestJson.display(normalized, field.valueUnit());
        List<ChangeImpact> impacts = "actual_qty".equals(fieldCode)
                ? List.of(ChangeImpact.INVENTORY, ChangeImpact.COST, ChangeImpact.STATS)
                : List.of(ChangeImpact.COST, ChangeImpact.INVENTORY);
        return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(), field.value(), beforeDisplay,
                normalized, afterDisplay, impacts, field.recalcTags());
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        String before = actualQty;
        if (ChangeType.UPDATE.name().equals(request.getChangeType())) {
            String value = ChangeRequestJson.afterValue(request.getAfterJson(), request.getFieldCode());
            if ("actual_qty".equals(request.getFieldCode()) && value != null) {
                actualQty = value;
            }
            if ("feed_id".equals(request.getFieldCode())) {
                feedName = "4002".equals(value) ? "保育料" : "育肥全价料";
                feedId = value;
            }
        } else if (ChangeType.VOID.name().equals(request.getChangeType())) {
            voided = true;
        } else if (ChangeType.DELETE.name().equals(request.getChangeType())) {
            deleted = true;
        }
        version = String.valueOf(Integer.parseInt(version) + 1);

        if (recorder != null) {
            Map<String, Object> beforeValues = new LinkedHashMap<>();
            Map<String, Object> afterValues = new LinkedHashMap<>();
            if (ChangeType.VOID.name().equals(request.getChangeType())
                    || ChangeType.DELETE.name().equals(request.getChangeType())) {
                beforeValues.put("record_status", Map.of("name", "记录状态", "display", "有效"));
                afterValues.put("record_status", Map.of("name", "记录状态",
                        "display", ChangeType.VOID.name().equals(request.getChangeType()) ? "已作废" : "已删除"));
            } else {
                String name = request.getFieldName() == null ? "实际投喂量" : request.getFieldName();
                beforeValues.put(request.getFieldCode() == null ? "actual_qty" : request.getFieldCode(),
                        Map.of("name", name, "display", before + " 千克"));
                afterValues.put(request.getFieldCode() == null ? "actual_qty" : request.getFieldCode(),
                        Map.of("name", name, "display", request.getAfterDisplay()));
            }
            recorder.record(OperationLogEntry.builder()
                    .operatorType("USER")
                    .operatorId(request.getSubmittedBy())
                    .operatorName("王建国")
                    .operatorRole(request.getSubmitterRole())
                    .opType("RECORD_EDIT")
                    .opAction(request.getChangeType())
                    .result("SUCCESS")
                    .bizType(BizType.FEED_RECORD.name())
                    .bizId(request.getBizId())
                    .farmId(request.getFarmId())
                    .reason(request.getReason())
                    .before(beforeValues)
                    .after(afterValues)
                    .changeRequestId(request.getId())
                    .build());
        }
        return new ChangeApplyResult(4, "已重新计算批次收益");
    }

    public String actualQty() {
        return actualQty;
    }

    public String version() {
        return version;
    }

    public boolean voided() {
        return voided;
    }

    public void reset() {
        feedId = "4001";
        feedName = "育肥全价料";
        actualQty = "140";
        version = "3";
        deleted = false;
        voided = false;
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message)
                .with("field_errors", List.of(new FieldErrorVO(field, message)));
    }
}
