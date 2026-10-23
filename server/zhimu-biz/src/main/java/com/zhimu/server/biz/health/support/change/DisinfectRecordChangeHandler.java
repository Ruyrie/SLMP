package com.zhimu.server.biz.health.support.change;

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
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.health.entity.DisinfectRecord;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.mapper.DisinfectRecordMapper;
import com.zhimu.server.biz.health.mapper.DisinfectTaskMapper;
import com.zhimu.server.biz.health.support.HealthCodes;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
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
 * 消毒记录（DISINFECT_RECORD）的修改申请适配：登记超过 3 天可申请改用量，审核通过后按差额调整消毒剂批号库存与成本；
 * 删除 / 作废时回滚库存并把计划任务退回 OVERDUE 或 PENDING。表单与 App mock 一致（drug_qty 用量，单位升）。
 */
@Component
@RequiredArgsConstructor
public class DisinfectRecordChangeHandler implements ChangeRequestHandler {

    public static final String FIELD_DRUG_QTY = "drug_qty";
    private static final List<String> RECALC_TAGS = List.of("消毒剂库存");
    private static final BigDecimal MAX_USAGE = new BigDecimal("100");

    private final DisinfectRecordMapper recordMapper;
    private final DisinfectTaskMapper taskMapper;
    private final DrugMapper drugMapper;
    private final DrugLotMapper drugLotMapper;
    private final DrugStockPort drugStockPort;
    private final DrugCatalogQueryService drugCatalogQueryService;
    private final HealthChangeSupport support;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.DISINFECT_RECORD);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.DISINFECT_RECORD);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        DisinfectRecord record = load(bizId);
        if (record == null) {
            return null;
        }
        String penName = support.penName(record.getPenId());
        LocalDate date = recordDate(record);
        String head = penName == null ? "栏舍 " + record.getPenId() : penName;
        String locator = head + " · " + HealthChangeSupport.monthDay(date) + "消毒";
        String title = "消毒记录 · " + head;
        String subtitle = nullSafe(record.getDisinfectantName()) + " · " + HealthChangeSupport.monthDay(date)
                + " · " + nullSafe(support.userName(record.getExecutorId())) + "登记";
        return new ChangeRecord(BizType.DISINFECT_RECORD, record.getId(), record.getFarmId(),
                null, record.getPenId(), locator, title, subtitle,
                versionOf(record), Boolean.TRUE.equals(record.getDeleted()), false);
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        DisinfectRecord row = load(record.bizId());
        if (row == null) {
            return false;
        }
        LocalDate date = recordDate(row);
        return date == null || !BeijingTime.withinEditWindow(date, 3);
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        DisinfectRecord row = load(record.bizId());
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        String unit = row.getUsageUnit() == null ? null : HealthCodes.unitText(row.getUsageUnit());
        ChangeField usage = new ChangeField(FIELD_DRUG_QTY, "用量",
                HealthChangeSupport.number(row.getUsageQty()), unit, ChangeField.NUMBER, List.of(),
                BigDecimal.ZERO, MAX_USAGE, RECALC_TAGS, null);
        String disinfectant = nullSafe(row.getDisinfectantName())
                + (row.getConcentration() == null || row.getConcentration().isBlank()
                ? "" : " " + row.getConcentration());
        return new ChangeForm(
                List.of(new ChangeCardItem("消毒剂", disinfectant.trim()),
                        new ChangeCardItem("用量", HealthChangeSupport.display(row.getUsageQty(), unit)),
                        new ChangeCardItem("方式", HealthCodes.methodText(row.getMethodCode()))),
                List.of(usage),
                List.of("修改用量会调整消毒剂的出库数量"),
                "这条记录会影响消毒剂库存，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                true, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            return ChangeValidation.withoutField(List.of(ChangeImpact.INVENTORY, ChangeImpact.COST,
                    ChangeImpact.STATS), RECALC_TAGS);
        }
        ChangeRequestJson.requireField(form, fieldCode);
        if (!FIELD_DRUG_QTY.equals(fieldCode)) {
            throw HealthChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        ChangeField field = form.fieldOf(fieldCode);
        BigDecimal usage = parseDecimal(newValue);
        if (usage == null || usage.signum() <= 0) {
            throw HealthChangeSupport.fieldError("new_value", "用量必须大于 0");
        }
        if (field.max() != null && usage.compareTo(field.max()) > 0) {
            throw HealthChangeSupport.fieldError("new_value",
                    "用量不能超过 " + HealthChangeSupport.number(field.max()) + field.valueUnit());
        }
        if (usage.compareTo(new BigDecimal(field.value())) == 0) {
            throw HealthChangeSupport.fieldError("new_value", "新值与原值相同，不需要提交");
        }
        String normalized = HealthChangeSupport.number(usage);
        return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(), field.value(),
                ChangeRequestJson.display(field.value(), field.valueUnit()), normalized,
                ChangeRequestJson.display(normalized, field.valueUnit()),
                List.of(ChangeImpact.INVENTORY, ChangeImpact.COST, ChangeImpact.STATS), RECALC_TAGS);
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        if (!ChangeType.UPDATE.name().equals(request.getChangeType())) {
            return;
        }
        DisinfectRecord row = load(record.bizId());
        if (row == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        BigDecimal usage = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_DRUG_QTY));
        if (usage == null || usage.signum() <= 0) {
            throw HealthChangeSupport.fieldError("new_value", "用量必须大于 0");
        }
        DrugLot lot = row.getDrugLotId() == null ? null : drugLotMapper.selectById(row.getDrugLotId());
        if (lot == null) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE, "该批号已过期或已停用，请换一个批号");
        }
        if (lot.getRecallId() != null || lot.getRecalledAt() != null) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE,
                    "批号 " + lot.getLotNo() + " 已被平台停用，不能出库，只能登记报废");
        }
        LocalDate date = recordDate(row);
        if (lot.getExpireDate() != null && date != null && lot.getExpireDate().isBefore(date)) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE,
                    "批号 " + lot.getLotNo() + " 已过期，不能出库，只能登记报废");
        }
        BigDecimal available = zero(lot.getStockQty()).add(zero(row.getUsageQty()));
        if (available.compareTo(usage) < 0) {
            Drug drug = drugMapper.selectById(row.getDrugId());
            String unit = drug == null ? null : HealthCodes.unitText(drug.getUnit());
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                    "消毒剂库存不足，批号 " + lot.getLotNo() + " 当前可用 "
                            + HealthChangeSupport.display(available, unit) + "，请先补充入库");
        }
        Drug drug = drugMapper.selectById(row.getDrugId());
        if (drug != null) {
            drugCatalogQueryService.assertNotBanned(drug.getCatalogId(), null);
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        DisinfectRecord row = load(record.bizId());
        if (row == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        ChangeType changeType = changeTypeOf(request.getChangeType());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (changeType == ChangeType.UPDATE) {
            BigDecimal usage = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_DRUG_QTY));
            if (usage == null || usage.signum() <= 0) {
                throw HealthChangeSupport.fieldError("new_value", "用量必须大于 0");
            }
            String oldText = HealthChangeSupport.display(row.getUsageQty(),
                    row.getUsageUnit() == null ? null : HealthCodes.unitText(row.getUsageUnit()));
            drugStockPort.restore(DrugStockCommand.BIZ_DISINFECT_RECORD, row.getId());
            StockConsumeResult consume = drugStockPort.deduct(new DrugStockCommand(
                    row.getFarmId(), row.getDrugId(), row.getDrugLotId(), usage,
                    DrugStockCommand.FLOW_DISINFECT_USE, DrugStockCommand.BIZ_DISINFECT_RECORD, row.getId(),
                    null, row.getPenId(), row.getExecutorId(), row.getActualTime(),
                    recordDate(row), row.getDisinfectantName()));
            row.setUsageQty(usage);
            row.setUnitPrice(consume.unitPrice());
            row.setCostAmount(consume.amount());
            recordMapper.updateById(row);
            String newText = HealthChangeSupport.display(usage,
                    row.getUsageUnit() == null ? null : HealthCodes.unitText(row.getUsageUnit()));
            before.put(FIELD_DRUG_QTY, Map.of("name", "用量", "display", oldText == null ? "" : oldText));
            after.put(FIELD_DRUG_QTY, Map.of("name", "用量", "display", newText == null ? "" : newText));
        } else if (changeType == ChangeType.DELETE || changeType == ChangeType.VOID) {
            drugStockPort.restore(DrugStockCommand.BIZ_DISINFECT_RECORD, row.getId());
            recordMapper.deleteById(row.getId());
            resetTask(row.getTaskId());
            before.put("record_status", Map.of("name", "记录状态", "display", "有效"));
            after.put("record_status", Map.of("name", "记录状态",
                    "display", changeType == ChangeType.DELETE ? "已删除" : "已作废"));
        } else {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "不支持的修改类型");
        }
        support.record(request, record, BizType.DISINFECT_RECORD.name(), changeType,
                row.getDisinfectantName() + " · " + HealthChangeSupport.monthDay(recordDate(row)), before, after);
        return new ChangeApplyResult(2, "已重新计算消毒剂库存和消毒执行率");
    }

    /** 作废后计划任务回到待执行：任务日期已过为 OVERDUE，否则 PENDING */
    private void resetTask(Long taskId) {
        if (taskId == null) {
            return;
        }
        DisinfectTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        LocalDate today = BeijingTime.today();
        task.setStatus(task.getTaskDate() != null && task.getTaskDate().isBefore(today)
                ? HealthCodes.TASK_OVERDUE : HealthCodes.TASK_PENDING);
        task.setRecordId(null);
        task.setManualDone(false);
        task.setOverdueAt(task.getTaskDate() != null && task.getTaskDate().isBefore(today)
                ? BeijingTime.now() : null);
        taskMapper.updateById(task);
    }

    private DisinfectRecord load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> recordMapper.selectByIdIncludingDeleted(bizId));
    }

    private static LocalDate recordDate(DisinfectRecord row) {
        if (row.getActualTime() != null) {
            return row.getActualTime().toLocalDate();
        }
        return row.getCreatedAt() == null ? null : row.getCreatedAt().toLocalDate();
    }

    private static String versionOf(DisinfectRecord row) {
        if (row.getVersion() != null) {
            return String.valueOf(row.getVersion());
        }
        return row.getUpdatedAt() == null ? "0"
                : String.valueOf(row.getUpdatedAt().atZone(BeijingTime.ZONE).toInstant().toEpochMilli());
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal parseDecimal(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static ChangeType changeTypeOf(String code) {
        ChangeType type = ChangeRequestJson.parseChangeType(code);
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "修改类型不正确");
        }
        return type;
    }
}
