package com.zhimu.server.biz.stock.support.change;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 模块09 修改申请 handler 的公共能力：物资名称与单位、输入单位折算（袋 / 千克）、中文日期数量写法，
 * 以及带 change_request_id 的用户端留痕（RECORD_EDIT）。审批在管理端上下文执行，不依赖 FarmContext。
 */
@Component
@RequiredArgsConstructor
public class StockChangeSupport {

    private final FeedMapper feedMapper;
    private final DrugMapper drugMapper;
    private final DrugLotMapper drugLotMapper;
    private final AccountQueryService accountQueryService;
    private final OperationLogRecorder operationLogRecorder;

    public Feed feed(Long feedId) {
        return feedId == null ? null : TenantSkip.call(() -> feedMapper.selectById(feedId));
    }

    public Drug drug(Long drugId) {
        return drugId == null ? null : TenantSkip.call(() -> drugMapper.selectById(drugId));
    }

    public DrugLot lot(Long drugLotId) {
        return drugLotId == null ? null : TenantSkip.call(() -> drugLotMapper.selectById(drugLotId));
    }

    public String feedName(Long feedId) {
        Feed feed = feed(feedId);
        return feed == null ? null : feed.getName();
    }

    public String drugName(Long drugId) {
        Drug drug = drug(drugId);
        return drug == null ? null : drug.getName();
    }

    public String materialName(String materialType, Long feedId, Long drugId) {
        return StockCodes.isFeed(materialType) ? feedName(feedId) : drugName(drugId);
    }

    /** 记账单位文字：饲料千克，药品按档案单位 */
    public String measureUnitText(String materialType, Long drugId) {
        if (StockCodes.isFeed(materialType)) {
            return StockCodes.unitText(StockCodes.UNIT_KG);
        }
        Drug drug = drug(drugId);
        return drug == null ? null : StockCodes.unitText(drug.getUnit());
    }

    /**
     * 录入单位折算：MEASURE 直接按记账单位；PACK 按包装规格折合。
     *
     * @param inputUnit  MEASURE / PACK
     * @param unitCode   记账单位编码（饲料 KG，药品 DOSE/G/ML/L）
     * @param packUnit   包装单位，例如 袋、桶
     * @param packSize   每包装折合的记账单位数量
     */
    public UnitInfo inputUnit(String inputUnit, String unitCode, String packUnit, BigDecimal packSize) {
        if (StockCodes.INPUT_PACK.equals(inputUnit) && packUnit != null && packSize != null
                && packSize.signum() > 0) {
            return new UnitInfo(packUnit, packSize);
        }
        return new UnitInfo(StockCodes.unitText(unitCode), BigDecimal.ONE);
    }

    public BigDecimal measureQty(BigDecimal inputQty, UnitInfo unit) {
        if (inputQty == null) {
            return BigDecimal.ZERO;
        }
        return StockQty.qty(inputQty.multiply(unit.factor()));
    }

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AccountBrief brief = accountQueryService.getBrief(userId);
        return brief == null ? null : brief.name();
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

    /** 1 袋 = 25 千克 的折算信息 */
    public record UnitInfo(String text, BigDecimal factor) {
    }

    /** 10月12日 */
    public static String monthDay(LocalDate date) {
        return date == null ? null : date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    /** 千分位数量：2,000 / 488 / 1.5 */
    public static String qtyText(BigDecimal value) {
        return StockQty.plain(value);
    }

    /** 单价保留 2 位：3.20 */
    public static String priceText(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** 数量文本：去掉多余 0 */
    public static String number(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    public static String display(BigDecimal value, String unit) {
        String number = number(value);
        if (number == null) {
            return null;
        }
        return unit == null || unit.isBlank() ? number : number + " " + unit;
    }

    public static com.zhimu.server.common.error.BizException fieldError(String field, String message) {
        return StockErrors.field(field, message);
    }
}
