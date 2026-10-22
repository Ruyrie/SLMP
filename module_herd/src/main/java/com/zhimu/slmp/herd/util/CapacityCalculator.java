package com.zhimu.slmp.herd.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.NumberFormatter;

/**
 * 登记页容量提示（L08 入栏、L10 转入）：after = 在栏数 + 数量（编辑且同一栏舍时扣除原数量），over = after − 容量。
 * 这是本模块唯一的本地计算，最终以服务端 42206 为准。
 */
public final class CapacityCalculator {

    private CapacityCalculator() {
    }

    /** 计算结果 */
    public static final class Result {
        public final int capacity;
        public final int current;
        public final int after;
        public final int over;

        public Result(int capacity, int current, int after, int over) {
            this.capacity = capacity;
            this.current = current;
            this.after = after;
            this.over = over;
        }

        public boolean isOver() {
            return over > 0;
        }
    }

    /**
     * @param stockCount  栏舍当前在栏数
     * @param capacity    栏舍容量
     * @param quantity    本次入栏 / 转入数量
     * @param originalQty 编辑且仍为同一栏舍时的原数量（新增为 0）
     */
    @NonNull
    public static Result calc(int stockCount, int capacity, int quantity, int originalQty) {
        int current = Math.max(0, stockCount - Math.max(0, originalQty));
        int after = current + Math.max(0, quantity);
        return new Result(capacity, current, after, after - capacity);
    }

    /**
     * 横幅正文：“4 号育肥舍容量 220 头，现有 150 头，入栏后共 240 头，超出 20 头。”
     *
     * @param verb   “入栏”或“转入”
     * @param suffix 正文末尾的补充（转栏为“确认后才能保存。”），可为 null
     */
    @Nullable
    public static String bannerText(@NonNull String penName, @NonNull Result r, @NonNull String unit,
                                    @NonNull String verb, @Nullable String suffix) {
        if (!r.isOver()) {
            return null;
        }
        String text = penName + "容量 " + qty(r.capacity, unit) + "，现有 " + qty(r.current, unit) + "，" + verb
                + "后共 " + qty(r.after, unit) + "，超出 " + qty(r.over, unit) + "。";
        return suffix == null ? text : text + suffix;
    }

    private static String qty(int v, String unit) {
        return NumberFormatter.formatQuantity(v, unit);
    }
}
