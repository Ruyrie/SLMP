package com.zhimu.slmp.stock.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.common.model.vo.QtyVO;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数量拼写（UI 文档 M05“数量写法”）：饲料“500 千克”；疫苗“30 瓶（300 头份）”，有零头“12 瓶 + 5 头份”；
 * 兽药、消毒剂写包装单位“18 袋”“8 桶”，有零头“12 袋 + 50 克”。数字与单位之间为不换行空格。
 */
public final class StockQtyFormatter {

    private static final String SP = NumberFormatter.NBSP;

    private StockQtyFormatter() {
    }

    /** 完整写法（列表右侧数量、辅助文字） */
    @NonNull
    public static String format(@Nullable QtyVO q) {
        if (q == null || q.value == null) {
            return "";
        }
        if (!hasPack(q)) {
            return qty(q.value, unitText(q));
        }
        int count = packCount(q);
        BigDecimal rest = rest(q, count);
        if (q.value.signum() == 0) {
            return count + SP + q.packUnit;
        }
        if (rest.signum() > 0) {
            return count + SP + q.packUnit + " + " + qty(rest, unitText(q));
        }
        if (StockEnums.UNIT_DOSE.equals(q.unit)) {
            return count + SP + q.packUnit + "（" + qty(q.value, unitText(q)) + "）";
        }
        return count + SP + q.packUnit;
    }

    /** 只写包装数（M06 概要卡“30 瓶”、报废确认的括号）；没有包装时同 format */
    @NonNull
    public static String packText(@Nullable QtyVO q) {
        if (q == null || q.value == null) {
            return "";
        }
        if (!hasPack(q)) {
            return qty(q.value, unitText(q));
        }
        BigDecimal packs = StockCalc.toPack(q.value, q.packSize);
        return NumberFormatter.formatNumber(packs) + SP + q.packUnit;
    }

    /** 带方向的流水数量：入库“+2,000 千克”，出库“−100 千克” */
    @NonNull
    public static String signed(@Nullable QtyVO q, @Nullable String direction) {
        String s = shortText(q);
        if (s.isEmpty()) {
            return s;
        }
        return (StockEnums.DIRECTION_OUT.equals(direction) ? NumberFormatter.MINUS : "+") + s;
    }

    /** 流水里的简写：疫苗按瓶、兽药按包装（有零头时写记账单位），饲料按千克 */
    @NonNull
    public static String shortText(@Nullable QtyVO q) {
        if (q == null || q.value == null) {
            return "";
        }
        if (!hasPack(q)) {
            return qty(q.value, unitText(q));
        }
        int count = packCount(q);
        BigDecimal rest = rest(q, count);
        if (rest.signum() > 0) {
            return qty(q.value, unitText(q));
        }
        return count + SP + q.packUnit;
    }

    /** “1,850 千克” */
    @NonNull
    public static String qty(@Nullable BigDecimal value, @Nullable String unitText) {
        return NumberFormatter.formatQuantity(value, unitText);
    }

    /** 按包装记账的物资（兽药、疫苗、消毒剂）；饲料即使有每袋折合也按千克显示 */
    public static boolean hasPack(@Nullable QtyVO q) {
        return q != null && q.packUnit != null && !q.packUnit.isEmpty() && q.packSize != null
                && q.packSize.signum() > 0 && !StockEnums.UNIT_KG.equals(q.unit);
    }

    @NonNull
    public static String unitText(@NonNull QtyVO q) {
        return q.unitText != null && !q.unitText.isEmpty() ? q.unitText : StockEnums.unitText(q.unit);
    }

    private static int packCount(@NonNull QtyVO q) {
        if (q.packCount != null) {
            return q.packCount;
        }
        return q.value.divide(q.packSize, 0, RoundingMode.DOWN).intValue();
    }

    @NonNull
    private static BigDecimal rest(@NonNull QtyVO q, int count) {
        if (q.restValue != null) {
            return q.restValue;
        }
        return q.value.subtract(q.packSize.multiply(BigDecimal.valueOf(count)));
    }
}
