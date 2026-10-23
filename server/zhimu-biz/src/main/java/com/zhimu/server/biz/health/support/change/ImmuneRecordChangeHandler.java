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
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.entity.ImmuneRecord;
import com.zhimu.server.biz.health.mapper.ImmunePlanMapper;
import com.zhimu.server.biz.health.mapper.ImmuneRecordMapper;
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
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 接种记录（IMMUNE_RECORD）的修改申请适配：非登记当天可申请改接种头数，审核通过后按差额调整疫苗批号库存与成本，
 * 删除 / 作废时回滚库存并把免疫计划退回待登记。表单与 App mock（change_form_immune_record.json）一致。
 */
@Component
@RequiredArgsConstructor
public class ImmuneRecordChangeHandler implements ChangeRequestHandler {

    public static final String FIELD_VACCINATED_COUNT = "vaccinated_count";
    private static final List<String> RECALC_TAGS = List.of("疫苗库存", "免疫档案");

    private final ImmuneRecordMapper recordMapper;
    private final ImmunePlanMapper planMapper;
    private final DrugMapper drugMapper;
    private final DrugLotMapper drugLotMapper;
    private final DrugStockPort drugStockPort;
    private final DrugCatalogQueryService drugCatalogQueryService;
    private final HealthChangeSupport support;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.IMMUNE_RECORD);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.IMMUNE_RECORD);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        ImmuneRecord record = load(bizId);
        if (record == null) {
            return null;
        }
        String penName = support.penName(record.getPenId());
        String batchNo = support.batchNo(record.getBatchId());
        String head = penName != null ? penName : batchNo;
        String locatorHead = batchNo != null ? batchNo : (penName == null ? "批次" : penName);
        String locator = locatorHead + " · " + nullSafe(record.getVaccineName()) + " · "
                + HealthChangeSupport.monthDay(record.getVaccinateDate());
        String title = "接种记录 · " + (head == null ? "批次" : head);
        String subtitle = nullSafe(record.getVaccineName()) + " · "
                + HealthChangeSupport.monthDay(record.getVaccinateDate()) + " · "
                + nullSafe(support.userName(record.getExecutorId())) + "登记";
        return new ChangeRecord(BizType.IMMUNE_RECORD, record.getId(), record.getFarmId(),
                record.getBatchId(), record.getPenId(), locator, title, subtitle,
                versionOf(record), Boolean.TRUE.equals(record.getDeleted()), false);
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        ImmuneRecord row = load(record.bizId());
        if (row == null) {
            return false;
        }
        return !BeijingTime.today().equals(recordDate(row));
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        ImmuneRecord row = load(record.bizId());
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        int stock = support.currentStock(row.getFarmId(), row.getBatchId(), row.getPenId());
        int max = Math.max(stock, 1);
        String unit = "头";
        ChangeField count = new ChangeField(FIELD_VACCINATED_COUNT, "接种头数",
                row.getQuantity() == null ? null : String.valueOf(row.getQuantity()), unit,
                ChangeField.NUMBER, List.of(), BigDecimal.ONE, BigDecimal.valueOf(max), RECALC_TAGS,
                stock > 0 ? "存栏 " + stock + " 头" : null);
        String doseUnit = row.getDoseUnit() == null ? null
                : HealthCodes.doseUnitText(row.getDoseUnit());
        String doseText = HealthChangeSupport.display(row.getDose(), doseUnit);
        if (doseText != null && !doseText.endsWith("头")) {
            doseText = doseText + " / 头";
        }
        return new ChangeForm(
                List.of(new ChangeCardItem("疫苗", row.getVaccineName()),
                        new ChangeCardItem("接种头数", row.getQuantity() == null ? null : row.getQuantity() + " 头"),
                        new ChangeCardItem("剂量", doseText)),
                List.of(count),
                List.of("修改接种头数会调整疫苗批号的出库数量"),
                "这条记录会影响免疫档案和疫苗库存，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
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
        if (!FIELD_VACCINATED_COUNT.equals(fieldCode)) {
            throw HealthChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        ChangeField field = form.fieldOf(fieldCode);
        Integer count = parsePositiveInt(newValue, "请填写接种头数");
        if (count <= 0) {
            throw HealthChangeSupport.fieldError("new_value", "接种头数必须大于 0");
        }
        if (field.max() != null && count > field.max().intValue()) {
            throw HealthChangeSupport.fieldError("new_value", "不能超过存栏 " + field.max().intValue() + " 头");
        }
        if (String.valueOf(count).equals(field.value())) {
            throw HealthChangeSupport.fieldError("new_value", "新值与原值相同，不需要提交");
        }
        return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(), field.value(),
                ChangeRequestJson.display(field.value(), field.valueUnit()), String.valueOf(count),
                ChangeRequestJson.display(String.valueOf(count), field.valueUnit()),
                List.of(ChangeImpact.INVENTORY, ChangeImpact.COST, ChangeImpact.STATS), RECALC_TAGS);
    }

    /** 审核前确认批号仍可用、疫苗结存足够（原记录占用的数量会先回滚再按新数量扣减） */
    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        if (!ChangeType.UPDATE.name().equals(request.getChangeType())) {
            return;
        }
        ImmuneRecord row = load(record.bizId());
        if (row == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        String raw = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_VACCINATED_COUNT);
        Integer count = raw == null ? null : parseInt(raw);
        if (count == null || count <= 0) {
            throw HealthChangeSupport.fieldError("new_value", "接种头数必须大于 0");
        }
        BigDecimal dose = row.getDose() == null ? BigDecimal.ZERO : row.getDose();
        BigDecimal newTotal = dose.multiply(BigDecimal.valueOf(count)).setScale(3, RoundingMode.HALF_UP);
        DrugLot lot = row.getDrugLotId() == null ? null : drugLotMapper.selectById(row.getDrugLotId());
        if (lot == null) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE, "该批号已过期或已停用，请换一个批号");
        }
        if (lot.getRecallId() != null || lot.getRecalledAt() != null) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE,
                    "批号 " + lot.getLotNo() + " 已被平台停用，不能出库，只能登记报废");
        }
        if (lot.getExpireDate() != null && row.getVaccinateDate() != null
                && lot.getExpireDate().isBefore(row.getVaccinateDate())) {
            throw new BizException(ErrorCode.LOT_UNAVAILABLE,
                    "批号 " + lot.getLotNo() + " 已过期，不能出库，只能登记报废");
        }
        BigDecimal oldTotal = row.getTotalDose() == null ? BigDecimal.ZERO : row.getTotalDose();
        BigDecimal available = zero(lot.getStockQty()).add(oldTotal);
        if (available.compareTo(newTotal) < 0) {
            Drug drug = drugMapper.selectById(row.getDrugId());
            String unit = drug == null ? null : HealthCodes.unitText(drug.getUnit());
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                    "疫苗库存不足，批号 " + lot.getLotNo() + " 当前可用 " + HealthChangeSupport.number(available)
                            + (unit == null ? "" : " " + unit) + "，请先补充入库");
        }
        Drug drug = drugMapper.selectById(row.getDrugId());
        if (drug != null) {
            drugCatalogQueryService.assertNotBanned(drug.getCatalogId(), null);
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        ImmuneRecord row = load(record.bizId());
        if (row == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        ChangeType changeType = changeTypeOf(request.getChangeType());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (changeType == ChangeType.UPDATE) {
            String raw = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_VACCINATED_COUNT);
            Integer count = raw == null ? null : parseInt(raw);
            if (count == null || count <= 0) {
                throw HealthChangeSupport.fieldError("new_value", "接种头数必须大于 0");
            }
            int oldCount = row.getQuantity() == null ? 0 : row.getQuantity();
            BigDecimal dose = row.getDose() == null ? BigDecimal.ZERO : row.getDose();
            BigDecimal totalDose = dose.multiply(BigDecimal.valueOf(count)).setScale(3, RoundingMode.HALF_UP);
            drugStockPort.restore(DrugStockCommand.BIZ_IMMUNE_RECORD, row.getId());
            StockConsumeResult consume = drugStockPort.deduct(new DrugStockCommand(
                    row.getFarmId(), row.getDrugId(), row.getDrugLotId(), totalDose,
                    DrugStockCommand.FLOW_VACCINE_USE, DrugStockCommand.BIZ_IMMUNE_RECORD, row.getId(),
                    row.getBatchId(), row.getPenId(), row.getExecutorId(), null, row.getVaccinateDate(),
                    row.getVaccineName()));
            row.setQuantity(count);
            row.setTotalDose(totalDose);
            row.setStockFlowId(consume.flowId());
            row.setUnitPrice(consume.unitPrice());
            row.setCostAmount(consume.amount());
            recordMapper.updateById(row);
            before.put(FIELD_VACCINATED_COUNT, Map.of("name", "接种头数", "display", oldCount + " 头"));
            after.put(FIELD_VACCINATED_COUNT, Map.of("name", "接种头数", "display", count + " 头"));
        } else if (changeType == ChangeType.DELETE || changeType == ChangeType.VOID) {
            drugStockPort.restore(DrugStockCommand.BIZ_IMMUNE_RECORD, row.getId());
            recordMapper.deleteById(row.getId());
            resetPlan(row.getPlanId());
            before.put("record_status", Map.of("name", "记录状态", "display", "有效"));
            after.put("record_status", Map.of("name", "记录状态",
                    "display", changeType == ChangeType.DELETE ? "已删除" : "已作废"));
        } else {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "不支持的修改类型");
        }
        support.record(request, record, BizType.IMMUNE_RECORD.name(), changeType,
                row.getVaccineName() + " · " + HealthChangeSupport.monthDay(row.getVaccinateDate()), before, after);
        return new ChangeApplyResult(2, "已重新计算疫苗库存和免疫档案");
    }

    private void resetPlan(Long planId) {
        if (planId == null) {
            return;
        }
        ImmunePlan plan = planMapper.selectById(planId);
        if (plan == null || !HealthCodes.PLAN_DONE.equals(plan.getStatus())) {
            return;
        }
        plan.setStatus(HealthCodes.PLAN_PENDING);
        plan.setRecordId(null);
        planMapper.updateById(plan);
    }

    private ImmuneRecord load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> recordMapper.selectByIdIncludingDeleted(bizId));
    }

    private static java.time.LocalDate recordDate(ImmuneRecord row) {
        if (row.getCreatedAt() != null) {
            return row.getCreatedAt().toLocalDate();
        }
        return row.getVaccinateDate();
    }

    private static String versionOf(ImmuneRecord row) {
        if (row.getVersion() != null) {
            return String.valueOf(row.getVersion());
        }
        return row.getUpdatedAt() == null ? "0"
                : String.valueOf(row.getUpdatedAt().atZone(BeijingTime.ZONE).toInstant().toEpochMilli());
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static Integer parsePositiveInt(String value, String emptyMessage) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw HealthChangeSupport.fieldError("new_value", emptyMessage);
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            throw HealthChangeSupport.fieldError("new_value", "请填写整数");
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ChangeType changeTypeOf(String code) {
        ChangeType type = ChangeRequestJson.parseChangeType(code);
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "修改类型不正确");
        }
        return type;
    }
}
