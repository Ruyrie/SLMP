package com.zhimu.slmp.stock.util;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.DrugLotItemVO;
import com.zhimu.slmp.stock.model.vo.DrugStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.FeedStockItemVO;
import com.zhimu.slmp.stock.model.vo.FeedStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.QtyVO;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 本模块文案拼写（UI 文档 2.5 节）：横幅、饲料卡说明行、流水与批号列表项、状态标签。只依赖 VO，便于单元测试。
 */
public final class StockTexts {

    public static final String DOT = " · ";
    private static final String SP = NumberFormatter.NBSP;

    private StockTexts() {
    }

    // ===================== M01 =====================

    /** M01 库存不足横幅：DAYS / MIN / MULTI；没有横幅返回 null */
    @Nullable
    public static String feedBanner(@Nullable FeedStockOverviewVO.Banner b) {
        if (b == null || b.kind == null) {
            return null;
        }
        switch (b.kind) {
            case StockEnums.BANNER_DAYS:
                return nz(b.feedName) + "库存不足，剩余可用 " + n(b.remainingDays) + " 天，低于预警天数 "
                        + n(b.warnDays) + " 天。";
            case StockEnums.BANNER_MIN: {
                String unit = b.stock == null ? "" : StockQtyFormatter.unitText(b.stock);
                return nz(b.feedName) + "库存不足，结存 " + StockQtyFormatter.format(b.stock) + "，低于库存下限 "
                        + StockQtyFormatter.qty(b.minStock, unit) + "。";
            }
            case StockEnums.BANNER_MULTI: {
                List<String> names = b.feedNames == null ? new ArrayList<>() : b.feedNames;
                return names.size() + " 种饲料库存不足：" + String.join("、", names) + "。";
            }
            default:
                return null;
        }
    }

    /** 饲料卡“剩余可用”：没有消耗时写“暂无消耗” */
    @NonNull
    public static String remaining(@NonNull FeedStockItemVO f) {
        return f.remainingDays == null ? "暂无消耗" : f.remainingDays + SP + "天";
    }

    /** 饲料卡说明行：“近 7 天领用 3,360 千克 · 均价每千克 3.20 元”；不满 7 天按实际天数；没有领用写“近 7 天没有领用” */
    @NonNull
    public static String feedUsage(@NonNull FeedStockItemVO f) {
        String unit = f.stock == null ? "千克" : StockQtyFormatter.unitText(f.stock);
        String usage;
        if (f.usageQty == null || f.usageQty.signum() == 0) {
            usage = "近 7 天没有领用";
        } else {
            int days = f.usageDays == null || f.usageDays <= 0 ? 7 : Math.min(f.usageDays, 7);
            usage = "近 " + days + " 天领用 " + StockQtyFormatter.qty(f.usageQty, unit);
        }
        return usage + DOT + avgPrice(unit, f.avgPrice);
    }

    /** “均价每千克 3.20 元” */
    @NonNull
    public static String avgPrice(@NonNull String unitText, @Nullable BigDecimal price) {
        return "均价每" + unitText + " " + StockCalc.money(price) + " 元";
    }

    /** 饲料卡副标题“全价料 · 40 千克/袋 · 育肥猪” */
    @NonNull
    public static String feedSubtitle(@NonNull FeedStockItemVO f) {
        return join(DOT, f.feedTypeText, f.spec, f.categoryText);
    }

    // ===================== M05 =====================

    /** M05 药品库存不足横幅 */
    @Nullable
    public static String drugBanner(@Nullable DrugStockOverviewVO.LowStockBanner b) {
        if (b == null || b.drugs == null || b.drugs.isEmpty()) {
            return null;
        }
        if (b.drugs.size() == 1) {
            DrugStockOverviewVO.LowDrug d = b.drugs.get(0);
            String unit = d.usable == null ? "" : d.usable.packUnit != null && StockQtyFormatter.hasPack(d.usable)
                    ? d.usable.packUnit : StockQtyFormatter.unitText(d.usable);
            return nz(d.name) + "库存不足，可用结存 " + StockQtyFormatter.packText(d.usable) + "，低于库存下限 "
                    + StockQtyFormatter.qty(d.minStock, unit) + "。";
        }
        List<String> names = new ArrayList<>();
        for (DrugStockOverviewVO.LowDrug d : b.drugs) {
            names.add(nz(d.name));
        }
        return b.drugs.size() + " 种药品库存不足：" + String.join("、", names) + "。";
    }

    /** “按“物资 + 批号”分别结存 · 到期前 30 天为临期”；各类别不同时写“15–30 天” */
    @NonNull
    public static String nearRule(@Nullable DrugStockOverviewVO.NearDays d) {
        int min = 30;
        int max = 30;
        if (d != null) {
            min = Math.min(d.vet, Math.min(d.vaccine, d.disinfectant));
            max = Math.max(d.vet, Math.max(d.vaccine, d.disinfectant));
        }
        String days = min == max ? String.valueOf(min) : min + "–" + max;
        return "按“物资 + 批号”分别结存" + DOT + "到期前 " + days + " 天为临期";
    }

    /** 批号状态标签文字；正常且不是优先出库时为 null */
    @Nullable
    public static String lotTag(@NonNull DrugLotItemVO lot) {
        if (lot.lotStatus == null) {
            return lot.priorityOut ? "优先出库" : null;
        }
        switch (lot.lotStatus) {
            case StockEnums.LOT_RECALLED:
                return "已停用";
            case StockEnums.LOT_EXPIRED:
                return "已过期";
            case StockEnums.LOT_BANNED:
                return "禁用药";
            case StockEnums.LOT_NEAR_EXPIRY:
                return lot.daysToExpire == null ? "临期" : "临期 " + lot.daysToExpire + " 天";
            default:
                return lot.priorityOut ? "优先出库" : null;
        }
    }

    @StatusTagView.TagType
    public static int lotTagType(@Nullable String lotStatus) {
        if (StockEnums.LOT_RECALLED.equals(lotStatus) || StockEnums.LOT_EXPIRED.equals(lotStatus)
                || StockEnums.LOT_BANNED.equals(lotStatus)) {
            return StatusTagView.ERROR;
        }
        if (StockEnums.LOT_NEAR_EXPIRY.equals(lotStatus)) {
            return StatusTagView.WARNING;
        }
        return StatusTagView.NEUTRAL;
    }

    @DrawableRes
    public static int lotTagIcon(@Nullable String lotStatus) {
        return StockEnums.LOT_RECALLED.equals(lotStatus) ? com.zhimu.slmp.base.R.drawable.ic_block : 0;
    }

    /** 物资图标底色：已停用、已过期 error；临期 warning；其余 secondary */
    @ListRowView.Tone
    public static int lotTone(@Nullable String lotStatus) {
        int t = lotTagType(lotStatus);
        return t == StatusTagView.ERROR ? ListRowView.TONE_ERROR
                : t == StatusTagView.WARNING ? ListRowView.TONE_WARNING : ListRowView.TONE_SECONDARY;
    }

    /** 批号行副文字：数量较长（疫苗带头份）时分两行“批号 2025081”“至 2026-12-30”；已停用第 2 行写说明 */
    @NonNull
    public static String lotSubtitle(@NonNull DrugLotItemVO lot) {
        String no = "批号 " + nz(lot.lotNo);
        if (StockEnums.LOT_RECALLED.equals(lot.lotStatus)) {
            return no + "\n" + (lot.statusNote != null ? lot.statusNote : "平台停用，只能报废");
        }
        String expire = "至 " + nz(lot.expireDate);
        boolean longQty = lot.stock != null && StockEnums.UNIT_DOSE.equals(lot.stock.unit);
        return longQty ? no + "\n" + expire : no + "  " + expire;
    }

    /** 药品类别图标 */
    @DrawableRes
    public static int categoryIcon(@Nullable String category) {
        if (StockEnums.CAT_VACCINE.equals(category)) {
            return com.zhimu.slmp.base.R.drawable.ic_vaccines;
        }
        if (StockEnums.CAT_DISINFECTANT.equals(category)) {
            return com.zhimu.slmp.base.R.drawable.ic_sanitizer;
        }
        return com.zhimu.slmp.base.R.drawable.ic_medication;
    }

    /** 登记时用到这类药品的动作：疫苗“接种”、兽药“用药”、消毒剂“消毒” */
    @NonNull
    public static String useVerb(@Nullable String category) {
        if (StockEnums.CAT_VACCINE.equals(category)) {
            return "接种";
        }
        if (StockEnums.CAT_DISINFECTANT.equals(category)) {
            return "消毒";
        }
        return "用药";
    }

    // ===================== 流水 =====================

    /** 流水主文字：饲料与全部药品流水“饲喂领用 · 育肥全价料”；批号详情页内只写类型 */
    @NonNull
    public static String flowTitle(@NonNull StockFlowItemVO f, boolean withMaterial) {
        String type = nz(f.flowTypeText);
        return withMaterial && f.materialName != null ? type + DOT + f.materialName : type;
    }

    /**
     * 流水副文字。饲料：“ZM2610-031 · 今天 07:40”“退回供应商 · 今天 10:05”；
     * 批号详情（lotStyle）：“10月12日 · 1 号保育舍 · 张伟”“10月19日 · 松山区康牧兽药店”。
     */
    @NonNull
    public static String flowSubtitle(@NonNull StockFlowItemVO f, boolean lotStyle) {
        if (lotStyle) {
            String date = DateFormatter.formatDate(f.bizDate != null ? f.bizDate : f.occurAt, false);
            String target = f.penName != null ? f.penName : f.batchNo != null ? f.batchNo : f.relatedDesc;
            return join(DOT, date, target, f.operatorName);
        }
        String head = f.batchNo != null ? f.batchNo : f.relatedDesc;
        if (StockEnums.MATERIAL_DRUG.equals(f.materialType) && f.lotNo != null) {
            head = join(DOT, "批号 " + f.lotNo, f.penName != null ? f.penName : f.relatedDesc);
        }
        return join(DOT, head, DateFormatter.formatDateTime(f.occurAt));
    }

    /** 流水是否已作废（status 非 NORMAL） */
    public static boolean flowVoided(@NonNull StockFlowItemVO f) {
        return f.status != null && !StockEnums.STATUS_NORMAL.equals(f.status);
    }

    /** 流水图标：饲喂领用 rice_bowl、入库 inventory_2、接种 vaccines、用药 medication、消毒 sanitizer、其他出库 output、退货 assignment_return、报损 delete_sweep、报废 delete、盘盈盘亏 trending */
    @DrawableRes
    public static int flowIcon(@NonNull StockFlowItemVO f) {
        String t = f.flowType == null ? "" : f.flowType;
        switch (t) {
            case StockEnums.FLOW_FEED_USE:
                return com.zhimu.slmp.base.R.drawable.ic_rice_bowl;
            case StockEnums.FLOW_VACCINE_USE:
                return com.zhimu.slmp.base.R.drawable.ic_vaccines;
            case StockEnums.FLOW_MEDICATION_USE:
                return com.zhimu.slmp.base.R.drawable.ic_medication;
            case StockEnums.FLOW_DISINFECT_USE:
                return com.zhimu.slmp.base.R.drawable.ic_sanitizer;
            case StockEnums.FLOW_MANUAL_USE:
                return com.zhimu.slmp.base.R.drawable.ic_medication;
            case StockEnums.FLOW_OTHER_OUT:
                return R.drawable.stock_ic_output;
            case StockEnums.FLOW_RETURN:
                return R.drawable.stock_ic_assignment_return;
            case StockEnums.FLOW_LOSS:
                return R.drawable.stock_ic_delete_sweep;
            case StockEnums.FLOW_SCRAP:
                return com.zhimu.slmp.base.R.drawable.ic_delete;
            case StockEnums.FLOW_STOCKTAKE_GAIN:
                return com.zhimu.slmp.base.R.drawable.ic_trending_up;
            case StockEnums.FLOW_STOCKTAKE_LOSS:
                return com.zhimu.slmp.base.R.drawable.ic_trending_down;
            default:
                return com.zhimu.slmp.base.R.drawable.ic_inventory_2;
        }
    }

    /** 流水按日期分组的标题：“10月19日 周一 · 今天” */
    @NonNull
    public static String flowDateHeader(@Nullable String date) {
        String d = DateFormatter.formatDate(date, true);
        java.time.LocalDate ld = DateFormatter.parseDate(date);
        if (ld != null && ld.equals(DateFormatter.today())) {
            return d + DOT + "今天";
        }
        if (ld != null && ld.equals(DateFormatter.today().minusDays(1))) {
            return d + DOT + "昨天";
        }
        return d;
    }

    // ===================== 通用 =====================

    @NonNull
    public static String join(@NonNull String sep, @Nullable String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p);
        }
        return sb.toString();
    }

    @NonNull
    public static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    @NonNull
    private static String n(@Nullable Integer v) {
        return v == null ? "0" : String.valueOf(v);
    }

    /** 库存单位文字（QtyVO 为空时退回记账单位） */
    @NonNull
    public static String unitOf(@Nullable QtyVO q, @Nullable String fallbackUnit) {
        return q == null ? StockEnums.unitText(fallbackUnit) : StockQtyFormatter.unitText(q);
    }
}
