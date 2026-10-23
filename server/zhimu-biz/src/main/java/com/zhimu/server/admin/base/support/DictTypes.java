package com.zhimu.server.admin.base.support;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 管理端-03 字典类型常量与范围规则（8 类；收支分类待模块10）。 */
public final class DictTypes {

    public static final String PEN_TYPE = "PEN_TYPE";
    public static final String DEATH_REASON = "DEATH_REASON";
    public static final String SYMPTOM = "SYMPTOM";
    public static final String FEED_TYPE = "FEED_TYPE";
    public static final String VACCINATE_METHOD = "VACCINATE_METHOD";
    public static final String DISINFECT_METHOD = "DISINFECT_METHOD";
    public static final String SCRAP_REASON = "SCRAP_REASON";
    public static final String PAYMENT_METHOD = "PAYMENT_METHOD";

    /** D49 本批返回的 8 类与展示顺序 */
    public static final List<String> ALL = List.of(PEN_TYPE, DEATH_REASON, SYMPTOM, FEED_TYPE,
            VACCINATE_METHOD, DISINFECT_METHOD, SCRAP_REASON, PAYMENT_METHOD);

    public static final Set<String> SPECIES_SCOPED = Set.of(PEN_TYPE, VACCINATE_METHOD);
    public static final Set<String> CATEGORY_SCOPED = Set.of(SYMPTOM);

    private DictTypes() {
    }

    public static boolean isKnown(String dictType) {
        return dictType != null && ALL.contains(dictType.toUpperCase(Locale.ROOT));
    }

    public static String normalize(String dictType) {
        return dictType == null ? null : dictType.trim().toUpperCase(Locale.ROOT);
    }

    public static String displayName(String dictType) {
        return switch (dictType) {
            case PEN_TYPE -> "栏舍类型";
            case DEATH_REASON -> "死淘原因";
            case SYMPTOM -> "症状";
            case FEED_TYPE -> "饲料类型";
            case VACCINATE_METHOD -> "接种方式";
            case DISINFECT_METHOD -> "消毒方式";
            case SCRAP_REASON -> "报废原因";
            case PAYMENT_METHOD -> "收款方式";
            default -> dictType;
        };
    }

    public static String icon(String dictType) {
        return switch (dictType) {
            case PEN_TYPE -> "fence";
            case DEATH_REASON -> "heart_minus";
            case SYMPTOM -> "sick";
            case FEED_TYPE -> "inventory_2";
            case VACCINATE_METHOD -> "vaccines";
            case DISINFECT_METHOD -> "sanitizer";
            case SCRAP_REASON -> "delete";
            case PAYMENT_METHOD -> "payments";
            default -> null;
        };
    }

    public static String scopeMode(String dictType) {
        if (SPECIES_SCOPED.contains(dictType)) {
            return "SPECIES";
        }
        if (CATEGORY_SCOPED.contains(dictType)) {
            return "CATEGORY";
        }
        return "NONE";
    }
}
