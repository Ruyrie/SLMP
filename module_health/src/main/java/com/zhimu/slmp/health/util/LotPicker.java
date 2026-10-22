package com.zhimu.slmp.health.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.health.model.vo.DrugLotOptionVO;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 库存批号的选择规则（D03 疫苗、D04 兽药、D08 消毒剂共用）：
 * <ul>
 *     <li>只能选 selectable 中的批号；默认取 default_lot_id，其次 priority_out（先到期先出），再其次第一项</li>
 *     <li>已过期、已停用、禁用药批号在“不可选择”分组，写明原因“只能报废”，点击无反应</li>
 *     <li>结存小于本次用量（insufficient）时可以选，但提交前提示“批号 2025081 结存 150 头份，不够本次 200 头份”</li>
 *     <li>临期批号标“临期”并写剩余天数；均价只在管理视图返回（员工不渲染单价）</li>
 * </ul>
 */
public final class LotPicker {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_NEAR_EXPIRY = "NEAR_EXPIRY";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_RECALLED = "RECALLED";
    public static final String STATUS_BANNED = "BANNED";

    private LotPicker() {
    }

    /** 面板中的一行 */
    public static final class Row {
        public final long lotId;
        @NonNull
        public final String label;
        @NonNull
        public final String hint;
        @Nullable
        public final String tagText;
        public final int tagType;
        public final boolean disabled;

        Row(long lotId, @NonNull String label, @NonNull String hint, @Nullable String tagText, int tagType,
            boolean disabled) {
            this.lotId = lotId;
            this.label = label;
            this.hint = hint;
            this.tagText = tagText;
            this.tagType = tagType;
            this.disabled = disabled;
        }
    }

    /** 默认批号：default_lot_id → priority_out → 第一项；没有可选批号时为 null */
    @Nullable
    public static DrugLotOptionVO defaultLot(@Nullable DrugLotOptionsVO options) {
        if (options == null || options.getSelectable().isEmpty()) {
            return null;
        }
        DrugLotOptionVO byId = find(options, options.getDefaultLotId());
        if (byId != null) {
            return byId;
        }
        for (DrugLotOptionVO lot : options.getSelectable()) {
            if (lot.isPriorityOut()) {
                return lot;
            }
        }
        return options.getSelectable().get(0);
    }

    /** 在可选批号中按 ID 查找（不可选的批号返回 null） */
    @Nullable
    public static DrugLotOptionVO find(@Nullable DrugLotOptionsVO options, @Nullable Long lotId) {
        if (options == null || lotId == null) {
            return null;
        }
        for (DrugLotOptionVO lot : options.getSelectable()) {
            if (lot.getLotId() == lotId) {
                return lot;
            }
        }
        return null;
    }

    /** 按批号文字在可选批号中查找（修改记录回填只有 lot_no 时） */
    @Nullable
    public static DrugLotOptionVO findByNo(@Nullable DrugLotOptionsVO options, @Nullable String lotNo) {
        if (options == null || lotNo == null) {
            return null;
        }
        for (DrugLotOptionVO lot : options.getSelectable()) {
            if (lotNo.equals(lot.getLotNo())) {
                return lot;
            }
        }
        return null;
    }

    public static boolean isSelectable(@Nullable DrugLotOptionsVO options, long lotId) {
        return find(options, lotId) != null;
    }

    /** 是否为默认（先到期先出）批号 */
    public static boolean isDefault(@Nullable DrugLotOptionsVO options, @Nullable DrugLotOptionVO lot) {
        DrugLotOptionVO d = defaultLot(options);
        return d != null && lot != null && d.getLotId() == lot.getLotId();
    }

    /** 结存是否不够本次用量（服务端 insufficient 或本地比较） */
    public static boolean isInsufficient(@Nullable DrugLotOptionVO lot, @Nullable BigDecimal need) {
        if (lot == null) {
            return false;
        }
        if (need != null && lot.getStock() != null && need.signum() > 0) {
            return lot.getStock().compareTo(need) < 0;
        }
        return lot.isInsufficient();
    }

    /** “批号 2025081 结存 150 头份，不够本次 200 头份” */
    @NonNull
    public static String insufficientText(@NonNull DrugLotOptionVO lot, @Nullable BigDecimal need, @Nullable String unit) {
        return "批号 " + lot.getLotNo() + " 结存 " + NumberFormatter.formatQuantity(lot.getStock(), unit)
                + "，不够本次 " + NumberFormatter.formatQuantity(need, unit);
    }

    /** 没有可用批号：员工“请联系所有者或场长先登记入库。”，养殖户“请先登记入库。” */
    @NonNull
    public static String noLotText(@Nullable String drugName, boolean staff) {
        String name = drugName == null ? "" : drugName;
        return name + "没有可用的库存批号。" + (staff ? "请联系所有者或场长先登记入库。" : "请先登记入库。");
    }

    /** 批号字段的辅助文字：默认批号写“已默认选择有效期最早的批号”，临期写剩余天数 */
    @NonNull
    public static String helperText(@NonNull DrugLotOptionVO lot, boolean isDefault) {
        StringBuilder sb = new StringBuilder("有效期至 ").append(HealthFormat.nonNull(lot.getExpireDate()));
        if (STATUS_NEAR_EXPIRY.equals(lot.getLotStatus()) && lot.getDaysToExpire() != null) {
            sb.append(HealthFormat.DOT).append("临期，剩 ").append(lot.getDaysToExpire()).append(" 天");
        }
        if (isDefault) {
            sb.append(HealthFormat.DOT).append("已默认选择有效期最早的批号");
        }
        return sb.toString();
    }

    /**
     * 面板行：可选批号在前（按服务端顺序，即有效期升序），不可选在后。
     *
     * @param unit      结存单位（头份、克、升）
     * @param showPrice 管理视图显示单价（员工为 false，且服务端也不返回 avg_price）
     */
    @NonNull
    public static List<Row> rows(@Nullable DrugLotOptionsVO options, @Nullable String unit, boolean showPrice,
                                 @Nullable BigDecimal need) {
        List<Row> rows = new ArrayList<>();
        if (options == null) {
            return rows;
        }
        DrugLotOptionVO def = defaultLot(options);
        for (DrugLotOptionVO lot : options.getSelectable()) {
            StringBuilder hint = new StringBuilder("有效期至 ").append(HealthFormat.nonNull(lot.getExpireDate()))
                    .append(HealthFormat.DOT).append("结存 ").append(NumberFormatter.formatQuantity(lot.getStock(), unit));
            if (STATUS_NEAR_EXPIRY.equals(lot.getLotStatus()) && lot.getDaysToExpire() != null) {
                hint.append(HealthFormat.DOT).append("剩 ").append(lot.getDaysToExpire()).append(" 天");
            }
            if (showPrice && lot.getAvgPrice() != null) {
                hint.append(HealthFormat.DOT).append("单价 ").append(NumberFormatter.formatAmount(lot.getAvgPrice(), null))
                        .append(" 元 / ").append(HealthFormat.nonNull(unit));
            }
            String tag = null;
            int type = StatusTagView.PRIMARY;
            if (isInsufficient(lot, need)) {
                tag = "结存不足";
                type = StatusTagView.WARNING;
            } else if (def != null && def.getLotId() == lot.getLotId()) {
                tag = "推荐";
            } else if (STATUS_NEAR_EXPIRY.equals(lot.getLotStatus())) {
                tag = "临期";
                type = StatusTagView.WARNING;
            }
            rows.add(new Row(lot.getLotId(), HealthFormat.nonNull(lot.getLotNo()), hint.toString(), tag, type, false));
        }
        for (DrugLotOptionVO lot : options.getUnselectable()) {
            String status = lot.getLotStatus();
            String tag;
            String hint;
            if (STATUS_RECALLED.equals(status)) {
                tag = "已停用";
                hint = "平台已停用该批号" + HealthFormat.DOT + "只能报废";
            } else if (STATUS_BANNED.equals(status)) {
                tag = "禁用药";
                hint = "该药品属于禁用兽药" + HealthFormat.DOT + "只能报废";
            } else {
                tag = "已过期";
                hint = "有效期至 " + HealthFormat.nonNull(lot.getExpireDate()) + HealthFormat.DOT + "只能报废";
            }
            rows.add(new Row(lot.getLotId(), HealthFormat.nonNull(lot.getLotNo()), hint, tag, StatusTagView.ERROR,
                    true));
        }
        return rows;
    }

    /** 当前选中的批号是否仍在可选列表中（重新查询后用） */
    public static boolean stillSelectable(@Nullable DrugLotOptionsVO options, @Nullable Long lotId) {
        return lotId != null && Objects.nonNull(find(options, lotId));
    }
}
