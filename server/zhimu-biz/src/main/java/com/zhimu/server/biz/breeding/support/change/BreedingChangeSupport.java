package com.zhimu.server.biz.breeding.support.change;

import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
import com.zhimu.server.biz.breeding.mapper.FarrowingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.IncubationMapper;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块05 修改申请 handler 的公共支撑：跨场读取产仔 / 孵化 / 种畜、展示文字、
 * 字段级校验（field_errors 只用 field_code / new_value / reason）与用户端留痕。
 * <p>
 * 字段与文案以 App Mock（module_breed/src/debug/assets/mock/breed/change_form_*.json）为准。
 */
@Component
@RequiredArgsConstructor
public class BreedingChangeSupport {

    private final FarrowingRecordMapper farrowingRecordMapper;
    private final IncubationMapper incubationMapper;
    private final BreedingAnimalMapper breedingAnimalMapper;
    private final BreedingCycleMapper breedingCycleMapper;
    private final BreedingSupport breedingSupport;
    private final FarmLookup farmLookup;
    private final OperationLogRecorder operationLogRecorder;

    // ===== 读取（跨养殖场，公共能力按记录所属 farm 判权） =====

    public FarrowingRecord farrowing(Long id) {
        return id == null ? null : TenantSkip.call(() -> farrowingRecordMapper.selectById(id));
    }

    public Incubation incubation(Long id) {
        return id == null ? null : TenantSkip.call(() -> incubationMapper.selectById(id));
    }

    public BreedingAnimal animal(Long id) {
        return id == null ? null : TenantSkip.call(() -> breedingAnimalMapper.selectById(id));
    }

    public BreedingCycle cycle(Long id) {
        return id == null ? null : TenantSkip.call(() -> breedingCycleMapper.selectById(id));
    }

    public String penName(Long penId) {
        String name = breedingSupport.penName(penId);
        return name == null ? "" : name;
    }

    public String batchNo(Long batchId) {
        return breedingSupport.batchNo(batchId);
    }

    /** 批次简要信息（不存在返回 null；不校验当前养殖场上下文） */
    public com.zhimu.server.biz.herd.api.BatchInfo batchInfo(Long batchId) {
        return batchId == null ? null : breedingSupport.batchOrNull(batchId);
    }

    public String breedName(Long breedId) {
        String name = breedingSupport.breedName(breedId);
        return name == null ? "" : name;
    }

    public String unitOfBreed(Long breedId) {
        return breedingSupport.unitOfBreed(breedId);
    }

    public String userName(Long userId) {
        if (userId == null) {
            return "";
        }
        var brief = breedingSupport.user(userId);
        return brief == null || brief.getName() == null ? "" : brief.getName();
    }

    public String farmName(Long farmId) {
        Farm farm = farmLookup.findFarm(farmId);
        return farm == null || farm.getName() == null ? "" : farm.getName();
    }

    public String monthDay(LocalDate date) {
        return BreedingCodes.monthDay(date);
    }

    // ===== 留痕 =====

    /** 修改申请回写留痕：operator 记提交人，op_type RECORD_EDIT，带 change_request_id */
    public void logApply(ChangeRequest request, BizType bizType, Long bizId, String targetName, Long farmId,
                         String searchText, Map<String, Object> before, Map<String, Object> after) {
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(request.getSubmittedBy())
                .operatorRole(request.getSubmitterRole())
                .opType("RECORD_EDIT")
                .opAction(request.getChangeType())
                .result("SUCCESS")
                .bizType(bizType.name())
                .bizId(bizId)
                .targetName(targetName)
                .farmId(farmId)
                .searchText(searchText)
                .reason(request.getReason())
                .before(before)
                .after(after)
                .changeRequestId(request.getId())
                .build());
    }

    public static Map<String, Object> value(String name, String display) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("display", display);
        return row;
    }

    // ===== 校验工具（field_errors 键只用 field_code / new_value / reason） =====

    public static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message)
                .with("field_errors", List.of(new FieldErrorVO(field, message)));
    }

    public static void requireField(com.zhimu.server.biz.common.change.ChangeForm form, String fieldCode) {
        if (form == null || form.fieldOf(fieldCode) == null) {
            throw fieldError("field_code", "请选择要修改的项目");
        }
    }

    public static int intValue(String newValue, String fieldName, int min, int max) {
        String text = newValue == null ? "" : newValue.trim();
        if (text.isEmpty()) {
            throw fieldError("new_value", "请填写" + fieldName);
        }
        BigDecimal number;
        try {
            number = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw fieldError("new_value", fieldName + "需为整数");
        }
        if (number.stripTrailingZeros().scale() > 0) {
            throw fieldError("new_value", fieldName + "需为整数");
        }
        int value = number.intValue();
        if (value < min || value > max) {
            throw fieldError("new_value", fieldName + "需在 " + min + "–" + max + " 之间");
        }
        return value;
    }

    public static LocalDate dateValue(String newValue, String message) {
        String text = newValue == null ? "" : newValue.trim();
        if (text.isEmpty()) {
            throw fieldError("new_value", message);
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw fieldError("new_value", message);
        }
    }

    public static String textValue(String newValue, String fieldName, int max) {
        String text = newValue == null ? "" : newValue.trim();
        if (text.isEmpty()) {
            throw fieldError("new_value", "请填写" + fieldName);
        }
        if (text.codePointCount(0, text.length()) > max) {
            throw fieldError("new_value", fieldName + "最多 " + max + " 字");
        }
        return text;
    }

    public static void sameValue() {
        throw fieldError("new_value", "新值与原值相同，不需要提交");
    }

    public static String display(String value, String unit) {
        return unit == null || unit.isBlank() ? value : value + " " + unit;
    }

    public static int intOf(Integer value) {
        return value == null ? 0 : value;
    }
}
