package com.zhimu.server.biz.health.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestHandler;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.health.entity.BatchWithdrawal;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.BatchWithdrawalMapper;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用药记录（MEDICATION_RECORD）的修改申请适配：用药记录不允许直接修改（会提前可出栏日期），
 * 只能删除；U01 与 App mock（change_form_medication.json）一致，没有可改字段，只取 record_version。
 * 审核通过后回滚药品库存、逻辑删除记录并重算批次休药期与可出栏日期。
 */
@Component
@RequiredArgsConstructor
public class MedicationRecordChangeHandler implements ChangeRequestHandler {

    private static final List<String> RECALC_TAGS = List.of("可出栏日期", "药品库存");
    private static final List<ChangeImpact> IMPACTS = List.of(ChangeImpact.SELLABLE_DATE,
            ChangeImpact.INVENTORY, ChangeImpact.COST);

    private final MedicationRecordMapper recordMapper;
    private final BatchWithdrawalMapper withdrawalMapper;
    private final DrugStockPort drugStockPort;
    private final HealthChangeSupport support;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.MEDICATION_RECORD);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.MEDICATION_RECORD);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        MedicationRecord record = load(bizId);
        if (record == null) {
            return null;
        }
        String penName = support.penName(record.getPenId());
        String batchNo = support.batchNo(record.getBatchId());
        String head = penName != null ? penName : batchNo;
        String locatorHead = batchNo != null ? batchNo : (penName == null ? "批次" : penName);
        String range = HealthChangeSupport.range(record.getFirstDate(), record.getLastDate());
        String locator = locatorHead + " · " + nullSafe(record.getDrugName()) + " · " + range;
        String title = "用药记录 · " + (head == null ? "批次" : head);
        String subtitle = nullSafe(record.getDrugName()) + " · " + range + " · "
                + nullSafe(support.userName(record.getExecutorId())) + "登记";
        return new ChangeRecord(BizType.MEDICATION_RECORD, record.getId(), record.getFarmId(),
                record.getBatchId(), record.getPenId(), locator, title, subtitle,
                versionOf(record), Boolean.TRUE.equals(record.getDeleted()), false);
    }

    /** 登记当天可以直接删除；其余情况（改可出栏日期、批次锁定）必须走修改申请 */
    @Override
    public boolean requiresRequest(ChangeRecord record) {
        MedicationRecord row = load(record.bizId());
        if (row == null) {
            return false;
        }
        LocalDate date = row.getCreatedAt() == null ? row.getFirstDate() : row.getCreatedAt().toLocalDate();
        return !Objects.equals(BeijingTime.today(), date);
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        MedicationRecord row = load(record.bizId());
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        // 用药记录的修改只能通过删除重新登记完成，因此没有可改字段（App mock 与 D26 口径一致）
        return new ChangeForm(List.of(), List.of(), List.of(),
                "审核通过前，记录和可出栏日期保持不变。", true, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType == ChangeType.DELETE || changeType == ChangeType.VOID) {
            return ChangeValidation.withoutField(IMPACTS, RECALC_TAGS);
        }
        // 表单没有可改字段：任何 UPDATE 都是非法字段编码
        throw HealthChangeSupport.fieldError("field_code", "用药记录不支持直接修改，请删除后重新登记");
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        MedicationRecord row = load(record.bizId());
        if (row == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        ChangeType changeType = changeTypeOf(request.getChangeType());
        if (changeType == ChangeType.UPDATE) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "用药记录不支持直接修改，请删除后重新登记");
        }
        LocalDate releaseBefore = currentRelease(row.getBatchId());
        drugStockPort.restore(DrugStockCommand.BIZ_MEDICATION_RECORD, row.getId());
        recordMapper.deleteById(row.getId());
        LocalDate releaseAfter = recalculate(row.getFarmId(), row.getBatchId());

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        before.put("record_status", Map.of("name", "记录状态", "display", "有效"));
        after.put("record_status", Map.of("name", "记录状态",
                "display", changeType == ChangeType.DELETE ? "已删除" : "已作废"));
        before.put("release_date", Map.of("name", "可出栏日期",
                "display", releaseBefore == null ? "无" : HealthChangeSupport.monthDay(releaseBefore)));
        after.put("release_date", Map.of("name", "可出栏日期",
                "display", releaseAfter == null ? "无" : HealthChangeSupport.monthDay(releaseAfter)));
        support.record(request, record, BizType.MEDICATION_RECORD.name(), changeType,
                row.getDrugName() + " · " + HealthChangeSupport.range(row.getFirstDate(), row.getLastDate()),
                before, after);
        return new ChangeApplyResult(2, releaseAfter == null ? "已重算可出栏日期"
                : "已重算可出栏日期，当前为 " + HealthChangeSupport.monthDay(releaseAfter));
    }

    /** 重算批次休药汇总：取剩余有效记录中最晚的 withdrawal_end_date；没有行时插入 */
    private LocalDate recalculate(Long farmId, Long batchId) {
        if (batchId == null) {
            return null;
        }
        BatchWithdrawal row = withdrawalMapper.selectForUpdate(batchId);
        List<MedicationRecord> records = recordMapper.selectEffective(farmId, batchId);
        LocalDate release = null;
        MedicationRecord decisive = null;
        if (records != null) {
            for (MedicationRecord item : records) {
                if (item.getWithdrawalEndDate() == null) {
                    continue;
                }
                if (release == null || item.getWithdrawalEndDate().isAfter(release)) {
                    release = item.getWithdrawalEndDate();
                    decisive = item;
                }
            }
        }
        if (row == null) {
            row = new BatchWithdrawal();
            row.setFarmId(farmId);
            row.setBatchId(batchId);
            row.setReleaseDate(release);
            row.setDecisiveRecordId(decisive == null ? null : decisive.getId());
            row.setDecisiveDrugName(decisive == null ? null : decisive.getDrugName());
            row.setLastMedDate(decisive == null ? null : decisive.getLastDate());
            row.setWithdrawalDays(decisive == null ? null : decisive.getWithdrawalDays());
            row.setCycleNo(0);
            row.setRecalculatedAt(BeijingTime.now());
            withdrawalMapper.insert(row);
            return release;
        }
        row.setReleaseDate(release);
        row.setDecisiveRecordId(decisive == null ? null : decisive.getId());
        row.setDecisiveDrugName(decisive == null ? null : decisive.getDrugName());
        row.setLastMedDate(decisive == null ? null : decisive.getLastDate());
        row.setWithdrawalDays(decisive == null ? null : decisive.getWithdrawalDays());
        row.setRecalculatedAt(BeijingTime.now());
        withdrawalMapper.updateById(row);
        return release;
    }

    private LocalDate currentRelease(Long batchId) {
        if (batchId == null) {
            return null;
        }
        BatchWithdrawal row = withdrawalMapper.selectByBatchId(batchId);
        return row == null ? null : row.getReleaseDate();
    }

    private MedicationRecord load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> recordMapper.selectByIdIncludingDeleted(bizId));
    }

    private static String versionOf(MedicationRecord row) {
        if (row.getVersion() != null) {
            return String.valueOf(row.getVersion());
        }
        return row.getUpdatedAt() == null ? "0"
                : String.valueOf(row.getUpdatedAt().atZone(BeijingTime.ZONE).toInstant().toEpochMilli());
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
