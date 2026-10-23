package com.zhimu.server.biz.feeding.support.change;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
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
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块06 修改申请 handler 的公共支撑：跨场读取投喂 / 称重 / 任务 / 批次生长、
 * 展示文字、库存端口与用户端留痕（RECORD_EDIT，带 change_request_id）。
 * <p>
 * 只使用模块04 的只读 API（BatchQueryService、PenQueryService）与模块09 的库存端口，不直接访问其他模块 Mapper。
 */
@Component
@RequiredArgsConstructor
public class FeedingChangeSupport {

    /** "10月18日"：原记录定位与标题中的日期口径 */
    public static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");

    private final FeedRecordMapper feedRecordMapper;
    private final WeighRecordMapper weighRecordMapper;
    private final FeedTaskMapper feedTaskMapper;
    private final BatchGrowthMapper batchGrowthMapper;
    private final BatchQueryService batchQueryService;
    private final PenQueryService penQueryService;
    private final AccountQueryService accountQueryService;
    private final FarmLookup farmLookup;
    private final FeedStockPort feedStockPort;
    private final OperationLogRecorder operationLogRecorder;

    // ===== 读取（跨养殖场，公共能力按记录所属 farm 判权） =====

    public FeedRecord feedRecord(Long id) {
        return id == null ? null : TenantSkip.call(() -> feedRecordMapper.selectById(id));
    }

    public WeighRecord weighRecord(Long id) {
        return id == null ? null : TenantSkip.call(() -> weighRecordMapper.selectById(id));
    }

    public FeedTask task(Long id) {
        return id == null ? null : TenantSkip.call(() -> feedTaskMapper.selectById(id));
    }

    public BatchInfo batch(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            return batchQueryService.requireBatch(batchId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public BatchGrowth growth(Long batchId) {
        if (batchId == null) {
            return null;
        }
        List<BatchGrowth> rows = TenantSkip.call(() -> batchGrowthMapper.selectList(
                Wrappers.<BatchGrowth>lambdaQuery().eq(BatchGrowth::getBatchId, batchId).last("LIMIT 1")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String penName(Long penId) {
        if (penId == null) {
            return "";
        }
        Map<Long, PenInfo> pens = penQueryService.getPens(List.of(penId));
        PenInfo pen = pens == null ? null : pens.get(penId);
        return pen == null || pen.penName() == null ? "" : pen.penName();
    }

    public String feedName(Long farmId, Long feedId) {
        FeedStockPort.FeedBrief feed = feedStockPort.getFeed(farmId, feedId);
        return feed == null || feed.feedName() == null ? "" : feed.feedName();
    }

    public FeedStockPort.FeedBrief feed(Long farmId, Long feedId) {
        return feedId == null ? null : feedStockPort.getFeed(farmId, feedId);
    }

    public List<FeedStockPort.FeedBrief> feedOptions(Long farmId) {
        List<FeedStockPort.FeedBrief> feeds = feedStockPort.listFeeds(farmId);
        return feeds == null ? List.of() : feeds;
    }

    public String userName(Long userId) {
        if (userId == null) {
            return "";
        }
        AccountBrief brief = accountQueryService.getBrief(userId);
        return brief == null || brief.name() == null ? "" : brief.name();
    }

    public String farmName(Long farmId) {
        Farm farm = farmLookup.findFarm(farmId);
        return farm == null || farm.getName() == null ? "" : farm.getName();
    }

    public String monthDay(LocalDate date) {
        return date == null ? "" : date.format(MONTH_DAY);
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

    /** before_json / after_json 的显示值（字段编码 → {name, display}） */
    public static Map<String, Object> displays(String json, String section) {
        Map<String, Object> values = new LinkedHashMap<>();
        tools.jackson.databind.JsonNode node = com.zhimu.server.biz.common.change.ChangeRequestJson
                .readTree(json).path(section);
        java.util.Iterator<String> keys = node.propertyNames().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            tools.jackson.databind.JsonNode field = node.path(key);
            String name = field.path("name").isMissingNode() ? key : field.path("name").asString();
            String display = field.path("display").isMissingNode()
                    ? field.path("value").asString() : field.path("display").asString();
            values.put(key, value(name, display));
        }
        return values;
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

    /** 数量字段：大于 0、最多 1 位小数、不超过 max */
    public static BigDecimal decimalValue(String newValue, String fieldName, BigDecimal max, String unit) {
        String text = newValue == null ? "" : newValue.trim();
        if (text.isEmpty()) {
            throw fieldError("new_value", "请填写" + fieldName);
        }
        BigDecimal number;
        try {
            number = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw fieldError("new_value", "请填写" + fieldName);
        }
        if (number.signum() <= 0) {
            throw fieldError("new_value", fieldName + "要大于 0 " + unit);
        }
        if (number.stripTrailingZeros().scale() > 1) {
            throw fieldError("new_value", fieldName + "最多 1 位小数");
        }
        if (max != null && number.compareTo(max) > 0) {
            throw fieldError("new_value", fieldName + "不能超过 " + plain(max) + " " + unit);
        }
        return number;
    }

    public static int intValue(String newValue, String fieldName, int min, int max) {
        String text = newValue == null ? "" : newValue.trim();
        if (text.isEmpty()) {
            throw fieldError("new_value", "请填写" + fieldName);
        }
        int value;
        try {
            value = new BigDecimal(text).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw fieldError("new_value", fieldName + "需为整数");
        }
        if (value < min || value > max) {
            throw fieldError("new_value", fieldName + "需在 " + min + "–" + max + " 之间");
        }
        return value;
    }

    public static void sameValue() {
        throw fieldError("new_value", "新值与原值相同，不需要提交");
    }

    public static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    public static String display(String value, String unit) {
        return unit == null || unit.isBlank() ? value : value + " " + unit;
    }
}
