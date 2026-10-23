package com.zhimu.server.biz.common.change;

import com.zhimu.server.common.vo.OptionVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * U01 的可改字段。value 为纯值（不带单位），单位单独放 valueUnit；inputType 只用 NUMBER / TEXT / DATE / SELECT，
 * 未知类型按 TEXT 处理。照片类字段不要放进表单；DATE 不下发 min / max。
 *
 * @param recalcTags 该字段通过后需要重算的项目（中文，例如 头均采食量）
 * @param hint       新值参考（"计划 160 千克"）；SELECT 字段未填时由公共能力补 "可选：…"
 */
public record ChangeField(
        String fieldCode,
        String fieldName,
        String value,
        String valueUnit,
        String inputType,
        List<OptionVO> options,
        BigDecimal min,
        BigDecimal max,
        List<String> recalcTags,
        String hint) {

    public static final String NUMBER = "NUMBER";
    public static final String TEXT = "TEXT";
    public static final String DATE = "DATE";
    public static final String SELECT = "SELECT";

    public ChangeField {
        inputType = normalize(inputType);
        options = options == null ? List.of() : List.copyOf(options);
        recalcTags = recalcTags == null ? List.of() : List.copyOf(recalcTags);
        if (DATE.equals(inputType)) {
            // DATE 不下发 min / max
            min = null;
            max = null;
        }
    }

    /** 未知或空的 input_type 按 TEXT */
    public static String normalize(String inputType) {
        if (inputType == null) {
            return TEXT;
        }
        return switch (inputType.trim().toUpperCase(java.util.Locale.ROOT)) {
            case NUMBER -> NUMBER;
            case DATE -> DATE;
            case SELECT -> SELECT;
            default -> TEXT;
        };
    }
}
