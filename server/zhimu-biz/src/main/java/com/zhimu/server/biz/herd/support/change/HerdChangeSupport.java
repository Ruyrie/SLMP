package com.zhimu.server.biz.herd.support.change;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.BatchTimeline;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.BatchTimelineMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.support.HerdErrors;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.common.enums.BizType;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块04 修改申请 handler 的公共支撑：跨场读取原记录、展示文字、栏舍/批次在栏数原子调整、
 * 时间线刷新与用户端留痕（RECORD_EDIT，带 change_request_id）。
 * <p>
 * 记录定位与字段口径以 App Mock（module_herd/src/debug/assets/mock/herd/change_form_*.json）为准。
 */
@Component
@RequiredArgsConstructor
public class HerdChangeSupport {

    /** "10月12日"：原记录定位与标题中的日期口径 */
    public static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");

    private final BatchMapper batchMapper;
    private final BatchEventMapper eventMapper;
    private final BatchPenMapper batchPenMapper;
    private final BatchTimelineMapper timelineMapper;
    private final PenMapper penMapper;
    private final HerdSupport support;
    private final OperationLogRecorder operationLogRecorder;

    // ===== 读取（跨养殖场，公共能力按记录所属 farm 判权） =====

    /** 入栏流水：biz_id 优先按流水 ID 找；找不到时兼容按批次 ID 取该批次的入栏流水 */
    public BatchEvent entryEvent(Long bizId) {
        if (bizId == null) {
            return null;
        }
        BatchEvent event = TenantSkip.call(() -> eventMapper.selectById(bizId));
        if (event != null && "ENTRY".equals(event.getEventType())) {
            return event;
        }
        return TenantSkip.call(() -> eventMapper.selectEntry(bizId));
    }

    public BatchEvent event(Long eventId, String eventType) {
        if (eventId == null) {
            return null;
        }
        BatchEvent event = TenantSkip.call(() -> eventMapper.selectById(eventId));
        if (event == null || !eventType.equals(event.getEventType())) {
            return null;
        }
        return event;
    }

    /** 含已删除的批次（用于区分 40401 与 41001） */
    public Batch batch(Long batchId) {
        return batchId == null ? null : TenantSkip.call(() -> batchMapper.selectByIdIncludingDeleted(batchId));
    }

    public BatchPen penRow(Long batchId, Long penId) {
        if (batchId == null || penId == null) {
            return null;
        }
        List<BatchPen> rows = TenantSkip.call(() -> batchPenMapper.selectList(Wrappers.<BatchPen>lambdaQuery()
                .eq(BatchPen::getBatchId, batchId)
                .eq(BatchPen::getPenId, penId)
                .last("LIMIT 1")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int penStock(Long batchId, Long penId) {
        BatchPen row = penRow(batchId, penId);
        return row == null || row.getStockCount() == null ? 0 : row.getStockCount();
    }

    public long followUpEvents(Long batchId) {
        return batchId == null ? 0 : TenantSkip.call(() -> batchMapper.countFollowUpEvents(batchId));
    }

    public String penName(Long penId) {
        if (penId == null) {
            return "栏舍";
        }
        return support.pensByIds(List.of(penId)).stream().findFirst()
                .map(pen -> pen.getPenName() == null ? "栏舍" : pen.getPenName())
                .orElse("栏舍");
    }

    public String breedName(Long breedId) {
        String name = support.breedName(breedId);
        return name == null || name.isBlank() ? "" : name;
    }

    public String userName(Long userId) {
        String name = support.userName(userId);
        return name == null || name.isBlank() ? "" : name;
    }

    public String monthDay(LocalDate date) {
        return date == null ? "" : date.format(MONTH_DAY);
    }

    public String farmUnitText(Long farmId) {
        return support.unitText(support.farmUnit(farmId));
    }

    public static String deathReasonText(String code) {
        if (code == null) {
            return "其他";
        }
        return switch (code) {
            case "SICK" -> "病死";
            case "CULL" -> "淘汰";
            case "ACCIDENT" -> "意外";
            default -> "其他";
        };
    }

    // ===== 在栏数调整（原子更新；影响 0 行全部按 42205 处理） =====

    /** 批次在某栏舍增加在栏数；没有分布行时补一行（从 0 变正数写 first_in_date） */
    public void addStock(Batch batch, Long penId, int quantity, LocalDate eventDate, String penName) {
        if (quantity == 0) {
            return;
        }
        LocalDateTime now = BeijingTime.now();
        if (quantity < 0) {
            deductStock(batch, penId, -quantity, eventDate, penName);
            return;
        }
        if (batchPenMapper.addStock(batch.getId(), penId, quantity, eventDate, now) == 0) {
            BatchPen row = new BatchPen();
            row.setFarmId(batch.getFarmId());
            row.setBatchId(batch.getId());
            row.setPenId(penId);
            row.setStockCount(quantity);
            row.setFirstInDate(eventDate);
            batchPenMapper.insert(row);
        }
        if (penMapper.addStock(penId, quantity, now) == 0) {
            throw HerdErrors.countExceeded(penId, penName, 0, null);
        }
    }

    /** 批次在某栏舍扣减在栏数；不足抛 42205 并带最新在栏数 */
    public void deductStock(Batch batch, Long penId, int quantity, LocalDate eventDate, String penName) {
        if (quantity <= 0) {
            return;
        }
        LocalDateTime now = BeijingTime.now();
        if (batchPenMapper.deduct(batch.getId(), penId, quantity, eventDate, now) == 0) {
            throw HerdErrors.countExceeded(penId, penName, penStock(batch.getId(), penId), null);
        }
        if (penMapper.addStock(penId, -quantity, now) == 0) {
            throw HerdErrors.countExceeded(penId, penName, penStock(batch.getId(), penId), null);
        }
    }

    /** 只更新批次在栏数与死淘累计（不碰栏舍分布）：在栏降为 0 时结束批次，回到正数时恢复在养 */
    public void updateBatchCounters(Batch batch, int current, int death, LocalDate eventDate) {
        boolean ended = current <= 0;
        int rows = batchMapper.update(null, Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, batch.getId())
                .eq(Batch::getVersion, batch.getVersion() == null ? 0 : batch.getVersion())
                .set(Batch::getCurrentCount, Math.max(0, current))
                .set(Batch::getDeathCount, Math.max(0, death))
                .set(Batch::getStatus, ended ? "ENDED" : "ACTIVE")
                .set(Batch::getEndType, ended ? "ALL_DEAD" : null)
                .set(Batch::getEndDate, ended ? eventDate : null)
                .set(Batch::getEndedAt, ended ? BeijingTime.now() : null)
                .set(Batch::getVersion, (batch.getVersion() == null ? 0 : batch.getVersion()) + 1)
                .set(Batch::getUpdatedAt, BeijingTime.now()));
        if (rows == 0) {
            throw HerdErrors.versionConflict();
        }
        batch.setCurrentCount(Math.max(0, current));
        batch.setDeathCount(Math.max(0, death));
        batch.setStatus(ended ? "ENDED" : "ACTIVE");
        batch.setVersion((batch.getVersion() == null ? 0 : batch.getVersion()) + 1);
    }

    /** 批次乐观锁 +1（存栏变化不改 t_pen.version，只动批次） */
    public void bumpBatchVersion(Batch batch) {
        int rows = batchMapper.update(null, Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, batch.getId())
                .eq(Batch::getVersion, batch.getVersion() == null ? 0 : batch.getVersion())
                .set(Batch::getVersion, (batch.getVersion() == null ? 0 : batch.getVersion()) + 1)
                .set(Batch::getUpdatedAt, BeijingTime.now()));
        if (rows == 0) {
            throw HerdErrors.versionConflict();
        }
        batch.setVersion((batch.getVersion() == null ? 0 : batch.getVersion()) + 1);
    }

    // ===== 时间线与留痕 =====

    public void updateTimeline(Long batchId, String bizType, Long bizId, Long penId, LocalDate bizDate,
                               String title, String subtitle) {
        if (batchId == null || bizId == null) {
            return;
        }
        timelineMapper.update(null, Wrappers.<BatchTimeline>lambdaUpdate()
                .eq(BatchTimeline::getBatchId, batchId)
                .eq(BatchTimeline::getBizType, bizType)
                .eq(BatchTimeline::getBizId, bizId)
                .set(BatchTimeline::getPenId, penId)
                .set(BatchTimeline::getBizDate, bizDate)
                .set(BatchTimeline::getTitle, title)
                .set(BatchTimeline::getSubtitle, subtitle)
                .set(BatchTimeline::getUpdatedAt, BeijingTime.now()));
    }

    /** 修改申请回写留痕：operator 记提交人，biz_type 为来源记录类型，带 change_request_id */
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

    // ===== 校验工具 =====

    public static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message)
                .with("field_errors", List.of(new FieldErrorVO(field, message)));
    }

    /** 数量字段：整数且落在 [min, max]；空值与非数字都按 new_value 字段错误返回 */
    public static int requireCount(String newValue, String fieldName, int min, int max, String unit) {
        String text = newValue == null ? "" : newValue.trim();
        if (text.isEmpty()) {
            throw fieldError("new_value", "请填写" + fieldName);
        }
        BigDecimal number;
        try {
            number = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw fieldError("new_value", fieldName + "需为大于 0 的整数");
        }
        if (number.stripTrailingZeros().scale() > 0) {
            throw fieldError("new_value", fieldName + "需为大于 0 的整数");
        }
        int value = number.intValue();
        if (value < min) {
            throw fieldError("new_value", fieldName + "不能小于 " + min + " " + unit);
        }
        if (value > max) {
            throw fieldError("new_value", fieldName + "不能超过 " + max + " " + unit);
        }
        return value;
    }

    /** 表单字段必须属于 U01 下发的可改字段 */
    public static void requireField(com.zhimu.server.biz.common.change.ChangeForm form, String fieldCode) {
        if (form == null || form.fieldOf(fieldCode) == null) {
            throw fieldError("field_code", "请选择要修改的项目");
        }
    }

    public static String display(String value, String unit) {
        return unit == null || unit.isBlank() ? value : value + " " + unit;
    }
}
