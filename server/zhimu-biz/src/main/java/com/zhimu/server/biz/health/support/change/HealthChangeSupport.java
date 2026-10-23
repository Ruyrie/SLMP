package com.zhimu.server.biz.health.support.change;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 模块08 修改申请 handler 的公共能力：原记录定位用的名称、日期与数量的中文写法、用户端留痕（RECORD_EDIT）。
 * 审批在管理端上下文执行，这里不依赖 FarmContext / UserContext。
 */
@Component
@RequiredArgsConstructor
public class HealthChangeSupport {

    private final AccountQueryService accountQueryService;
    private final HealthReferenceMapper referenceMapper;
    private final BatchQueryService batchQueryService;
    private final OperationLogRecorder operationLogRecorder;

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AccountBrief brief = accountQueryService.getBrief(userId);
        return brief == null ? null : brief.name();
    }

    public String penName(Long penId) {
        return penId == null ? null : referenceMapper.selectPenName(penId);
    }

    public String batchNo(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            return batchQueryService.requireBatch(batchId).batchNo();
        } catch (BizException e) {
            return null;
        }
    }

    /** 当前批次在栏数（种畜栏舍取在档种畜数），用于接种头数的上限与提示 */
    public int currentStock(Long farmId, Long batchId, Long penId) {
        if (batchId != null) {
            try {
                Integer count = batchQueryService.requireBatch(batchId).currentCount();
                if (count != null && count > 0) {
                    return count;
                }
            } catch (BizException e) {
                // 批次已删除时退回栏舍在档数
            }
        }
        if (farmId != null && penId != null) {
            return referenceMapper.countBreedingAnimals(farmId, penId);
        }
        return 0;
    }

    public void record(ChangeRequest request, ChangeRecord record, String bizType, ChangeType changeType,
                       String targetName, Map<String, Object> before, Map<String, Object> after) {
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(request.getSubmittedBy())
                .operatorRole(request.getSubmitterRole())
                .opType("RECORD_EDIT")
                .opAction(changeType.name())
                .result("SUCCESS")
                .bizType(bizType)
                .bizId(request.getBizId())
                .targetName(targetName == null ? record.recordTitle() : targetName)
                .farmId(request.getFarmId())
                .reason(request.getReason())
                .before(before)
                .after(after)
                .changeRequestId(request.getId())
                .build());
    }

    /** 10月8日 */
    public static String monthDay(LocalDate date) {
        return date == null ? null : date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    /** 10月5日–10月9日 */
    public static String range(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        return monthDay(from) + "–" + monthDay(to);
    }

    /** 去掉多余 0 的数字文本（200.000 → 200） */
    public static String number(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().toPlainString();
    }

    public static String display(BigDecimal value, String unit) {
        String number = number(value);
        if (number == null) {
            return null;
        }
        return unit == null || unit.isBlank() ? number : number + " " + unit;
    }

    public static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message)
                .with("field_errors", List.of(new FieldErrorVO(field, message)));
    }

    public static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }
}
