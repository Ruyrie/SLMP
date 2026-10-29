package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.common.vo.QtyVO;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数量、金额口径（模块09 2.7 节）：
 * 数量存 3 位小数（饲料录入限制 2 位）、金额保留到分、单价 4 位；均用 BigDecimal，不走 double。
 * QtyVO：饲料只写千克；药品带包装换算，pack_count = 结存 ÷ pack_size 向下取整，rest_value 为余量。
 */
public final class StockQty {

    public static final int QTY_SCALE = 3;
    public static final int AMOUNT_SCALE = 2;
    public static final int PRICE_SCALE = 4;

    private StockQty() {
    }

    /** 数量：3 位小数、去掉多余的 0（500.000 → 500，1.500 → 1.5） */
    public static BigDecimal qty(BigDecimal value) {
        return value == null ? null : value.setScale(QTY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** 金额：保留到分 */
    public static BigDecimal amount(BigDecimal value) {
        return value == null ? null : value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /** 单价：4 位小数 */
    public static BigDecimal price(BigDecimal value) {
        return value == null ? null : value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return BigDecimal.ZERO;
        }
        return a.multiply(b);
    }

    /**
     * 饲料数量 QtyVO：只有千克，不带包装（App 饲料一律按千克显示，即使档案有每袋折合）。
     */
    public static QtyVO feed(BigDecimal qty) {
        if (qty == null) {
            return null;
        }
        return QtyVO.builder()
                .value(qty(qty))
                .unit(StockCodes.UNIT_KG)
                .unitText(StockCodes.unitText(StockCodes.UNIT_KG))
                .build();
    }

    /**
     * 药品数量 QtyVO：记账单位 + 包装换算。
     */
    public static QtyVO drug(BigDecimal qty, String unit, String packUnit, BigDecimal packSize) {
        if (qty == null) {
            return null;
        }
        BigDecimal value = qty(qty);
        QtyVO.QtyVOBuilder builder = QtyVO.builder()
                .value(value)
                .unit(unit)
                .unitText(StockCodes.unitText(unit));
        if (packUnit != null && !packUnit.isEmpty() && packSize != null && packSize.signum() > 0) {
            int count = value.divide(packSize, 0, RoundingMode.DOWN).intValue();
            builder.packUnit(packUnit)
                    .packSize(packSize)
                    .packCount(count)
                    .restValue(value.subtract(packSize.multiply(BigDecimal.valueOf(count))));
        }
        return builder.build();
    }

    /**
     * 消息里的数量写法：千分位、最多 2 位小数（1,000 / 500 / 2.35）。
     */
    public static String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.scale() < 0) {
            scaled = scaled.setScale(0, RoundingMode.UNNECESSARY);
        }
        String text = scaled.toPlainString();
        int dot = text.indexOf('.');
        String integer = dot < 0 ? text : text.substring(0, dot);
        String fraction = dot < 0 ? "" : text.substring(dot);
        boolean negative = integer.startsWith("-");
        String digits = negative ? integer.substring(1) : integer;
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(digits.charAt(i));
        }
        return (negative ? "-" : "") + grouped + fraction;
    }

    /** 接口 data 里的数值字符串：最多 3 位小数、去掉末尾 0、不带千分位（2500 / 2.5） */
    public static String number(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal scaled = value.setScale(QTY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.signum() == 0 ? "0" : scaled.toPlainString();
    }

    /** 记账数量换算成录入单位：PACK 且有包装规格时为包装数（可带小数），否则为记账数量 */
    public static BigDecimal inInputUnit(BigDecimal qty, String inputUnit, BigDecimal packSize) {
        BigDecimal value = zeroIfNull(qty);
        if (StockCodes.INPUT_PACK.equals(inputUnit) && packSize != null && packSize.signum() > 0) {
            return value.divide(packSize, QTY_SCALE, RoundingMode.HALF_UP);
        }
        return value;
    }

    /** 1 袋 / 2 桶 的包装数量（没有包装时返回 null） */
    public static Integer packCount(BigDecimal qty, BigDecimal packSize) {
        if (qty == null || packSize == null || packSize.signum() <= 0) {
            return null;
        }
        return qty.divide(packSize, 0, RoundingMode.DOWN).intValue();
    }
}
