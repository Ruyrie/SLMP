package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.common.util.BeijingTime;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;

/**
 * 行情涨跌计算（ADM-C14/C18）：涨跌 =（本次价 − 上一个有数据日期的已发布价）÷ 上一个价，保留 4 位；
 * 绝对值 > 20% 需复核，正好 20% 不需要。无上一个价时不计算、不复核。
 */
@Component
public class MarketPriceCalculator {

    public static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("0.20");
    public static final Set<String> PRICE_UNITS = Set.of("YUAN_PER_KG", "YUAN_PER_HEAD", "YUAN_PER_BIRD");

    /** 最多保留 2 位小数且 > 0 */
    public boolean validPrice(BigDecimal price) {
        return price != null && price.signum() > 0
                && (price.scale() <= 2 || price.stripTrailingZeros().scale() <= 2);
    }

    public BigDecimal rate(BigDecimal price, BigDecimal previous) {
        if (price == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return price.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP);
    }

    public boolean needReview(BigDecimal rate) {
        return rate != null && rate.abs().compareTo(REVIEW_THRESHOLD) > 0;
    }

    /** 可发布日期：今天和前 3 天 */
    public LocalDate today() {
        return BeijingTime.today();
    }

    public LocalDate publishableFrom() {
        return BeijingTime.today().minusDays(3);
    }

    public void requirePublishableDate(LocalDate date) {
        if (date == null) {
            throw ContentErrors.field("price_date", "请选择价格日期");
        }
        if (date.isAfter(BeijingTime.today())) {
            throw ContentErrors.field("price_date", "只能发布今天和前 3 天的价格");
        }
        if (date.isBefore(publishableFrom())) {
            throw ContentErrors.field("price_date", "只能发布今天和前 3 天的价格");
        }
    }

    public void requireViewableDate(LocalDate date) {
        if (date != null && date.isAfter(BeijingTime.today())) {
            throw ContentErrors.field("date", "只能查看今天及以前的行情");
        }
    }

    public static String priceTypeText(String code) {
        return switch (code == null ? "" : code) {
            case "SLAUGHTER" -> "出栏价";
            case "SEEDLING" -> "种苗价";
            default -> code;
        };
    }

    public static String priceUnitText(String code) {
        return switch (code == null ? "" : code) {
            case "YUAN_PER_KG" -> "元/千克";
            case "YUAN_PER_HEAD" -> "元/头";
            case "YUAN_PER_BIRD" -> "元/只";
            default -> code;
        };
    }
}
