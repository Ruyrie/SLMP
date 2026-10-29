package com.zhimu.server.biz.finance.support;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块10 查询与格式化公共能力：当前养殖场、品类/品种/栏舍/批次名称、版本比较、金额与日期格式。
 * 跨模块只调用对方 api 包的 Service（FarmLookup 为模块02 只读支持类，用于取省份与主要品类）。
 */
@Component
public class FinanceSupport {

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final FarmLookup farmLookup;
    private final CategoryQueryService categoryQueryService;
    private final BreedQueryService breedQueryService;
    private final PenQueryService penQueryService;
    private final BatchQueryService batchQueryService;

    public FinanceSupport(FarmLookup farmLookup, CategoryQueryService categoryQueryService,
                          BreedQueryService breedQueryService, PenQueryService penQueryService,
                          BatchQueryService batchQueryService) {
        this.farmLookup = farmLookup;
        this.categoryQueryService = categoryQueryService;
        this.breedQueryService = breedQueryService;
        this.penQueryService = penQueryService;
        this.batchQueryService = batchQueryService;
    }

    public FarmContext.FarmMember context() {
        return FarmContext.require();
    }

    public Long farmId() {
        return FarmContext.requireFarmId();
    }

    public Farm farm(Long farmId) {
        return farmLookup.findFarm(farmId);
    }

    public CategoryInfo category(Long categoryId) {
        return categoryId == null ? null : categoryQueryService.info(categoryId);
    }

    public CategoryInfo categoryOrThrow(Long categoryId) {
        CategoryInfo info = category(categoryId);
        if (info == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return info;
    }

    public String categoryName(Long categoryId) {
        CategoryInfo info = category(categoryId);
        return info == null ? null : info.name();
    }

    public String countUnitText(Long categoryId) {
        CategoryInfo info = category(categoryId);
        return info == null ? null : info.countUnitText();
    }

    public String countUnit(Long categoryId) {
        CategoryInfo info = category(categoryId);
        return info == null ? null : info.countUnit();
    }

    public String breedName(Long breedId) {
        BreedQueryService.BreedInfo info = breedId == null ? null : breedQueryService.info(breedId);
        return info == null ? null : info.name();
    }

    public Map<Long, String> penNames(Collection<Long> penIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (penIds == null || penIds.isEmpty()) {
            return result;
        }
        Map<Long, PenInfo> pens = penQueryService.getPens(penIds);
        pens.forEach((id, pen) -> result.put(id, pen.penName()));
        return result;
    }

    public String penName(Long penId) {
        if (penId == null) {
            return null;
        }
        PenInfo pen = penQueryService.getPens(List.of(penId)).get(penId);
        return pen == null ? null : pen.penName();
    }

    public BatchInfo requireBatch(Long batchId, Long farmId) {
        if (batchId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择批次");
        }
        BatchInfo batch;
        try {
            batch = batchQueryService.requireBatch(batchId);
        } catch (BizException e) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (farmId == null || !farmId.equals(batch.farmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return batch;
    }

    public void checkVersion(Integer actual, Integer request) {
        if (request != null && !request.equals(actual)) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal scale2(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal scale4(BigDecimal value) {
        return nz(value).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal rate(BigDecimal current, BigDecimal previous) {
        BigDecimal prev = nz(previous);
        if (prev.signum() == 0) {
            return null;
        }
        return nz(current).subtract(prev).divide(prev.abs(), 4, RoundingMode.HALF_UP);
    }

    public static String trendOf(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        int cmp = rate.compareTo(BigDecimal.ZERO);
        return cmp > 0 ? "UP" : cmp < 0 ? "DOWN" : "FLAT";
    }

    /** 给客户端直接展示的金额文字：千分位、最多 2 位小数（88,160 / 1,234.5） */
    public static String moneyText(BigDecimal value) {
        return new java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT))
                .format(scale2(nz(value)));
    }

    public static String format(LocalDate date) {
        return date == null ? null : date.format(BeijingTime.DATE);
    }

    public static String formatMonth(YearMonth month) {
        return month == null ? null : month.format(BeijingTime.MONTH);
    }

    public static String format(java.time.LocalDateTime time) {
        return time == null ? null : time.format(BeijingTime.DATETIME);
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    public static <T> T parse(String value, Class<T> type) {
        return value == null || value.isBlank() ? null : JSON.readValue(value, type);
    }

    /** 两个日期之间的整天数（to − from），可能为负 */
    public static int daysBetween(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    /** 月报/周报标签：10月12日—18日；跨月 9月28日—10月4日 */
    public static String periodLabel(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        if (start.getMonth() == end.getMonth()) {
            return start.getMonthValue() + "月" + start.getDayOfMonth() + "日—" + end.getDayOfMonth() + "日";
        }
        return start.getMonthValue() + "月" + start.getDayOfMonth() + "日—" + end.getMonthValue() + "月"
                + end.getDayOfMonth() + "日";
    }
}
