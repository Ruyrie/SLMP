package com.zhimu.slmp.finance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * E12 出栏建议测试直连的输入、提示词与输出校验（与服务端 2.6.6 一致）：
 * <ul>
 *     <li>四项理由按模板生成，数字与输入一致；休药期内为 RULE 模式，不调用模型：NOT_SELLABLE、时间段为可出栏日期至其后 7 天</li>
 *     <li>模型只输出结论、时间段、0–3 条风险；结论只能三种，非休药期的 NOT_SELLABLE 纠正为 KEEP_FEEDING；
 *     window_start ≥ 今天、跨度 ≤ 30 天；风险取前 3 条、每条 ≤ 60 字</li>
 * </ul>
 * 结果组装为与 API-E36 相同结构的 JSON，由调用方用 Gson 解析为 SaleAdviceVO。
 */
public final class AdviceComposer {

    static final int WINDOW_MAX_DAYS = 30;
    static final int RULE_WINDOW_DAYS = 7;
    static final int MAX_RISKS = 3;
    static final int RISK_MAX_CHARS = 60;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss",
            Locale.ROOT);

    private AdviceComposer() {
    }

    /** 四项理由 */
    @NonNull
    public static JsonObject reasons(@NonNull SaleBatchOptionVO b, @Nullable MarketTrendVO trend) {
        JsonObject r = new JsonObject();
        String unit = b.getCountUnitText();
        if (b.isInWithdrawal()) {
            String drug = b.getWithdrawalDrugName() == null ? "用药" : b.getWithdrawalDrugName();
            r.addProperty("withdrawal", drug + "休药中，剩 " + safe(b.getWithdrawalDaysLeft()) + " 天，"
                    + monthDay(b.getSellableDate()) + "起可出栏。");
        } else {
            r.addProperty("withdrawal", "不在休药期，可以出栏。");
        }
        r.addProperty("age", b.getDayAge() + " 日龄，存栏 " + b.getCurrentCount() + " " + unit + "。");
        r.addProperty("weight", "测试直连没有读取称重记录，本次未参考体重。");
        r.addProperty("price", priceReason(trend));
        return r;
    }

    @NonNull
    static String priceReason(@Nullable MarketTrendVO t) {
        if (!hasPrice(t)) {
            return "近 7 天没有出栏价。";
        }
        MarketTrendVO.PriceStats s = t.getStats();
        StringBuilder sb = new StringBuilder("近 30 天每千克 ")
                .append(NumberFormatter.formatNumber(s.getMinPrice(), 2)).append("—")
                .append(NumberFormatter.formatNumber(s.getMaxPrice(), 2)).append(" 元，平均 ")
                .append(NumberFormatter.formatNumber(s.getAvgPrice(), 2)).append(" 元");
        MarketTrendVO.PriceChange c = t.getChange();
        if (c != null && c.getChangeRate() != null) {
            String pct = NumberFormatter.formatPercent(c.getChangeRate().abs());
            if (FinanceConst.UP.equals(c.getDirection())) {
                sb.append("；今天涨 ").append(pct);
            } else if (FinanceConst.DOWN.equals(c.getDirection())) {
                sb.append("；今天跌 ").append(pct);
            } else {
                sb.append("；今天持平");
            }
        }
        return sb.append("。").toString();
    }

    static boolean hasPrice(@Nullable MarketTrendVO t) {
        return t != null && !t.isEmpty() && t.getStats() != null && t.getStats().getAvgPrice() != null;
    }

    /** 系统提示词 */
    @NonNull
    public static String system() {
        return "你是生猪、家禽养殖场的出栏决策助手，只根据给出的批次和行情数据给出出栏建议，不编造数据。"
                + "只输出一个 JSON 对象，不要输出其他文字，格式："
                + "{\"conclusion\":\"SELL_SOON 或 KEEP_FEEDING\",\"window_start\":\"yyyy-MM-dd\","
                + "\"window_end\":\"yyyy-MM-dd\",\"risks\":[\"不超过 60 字的风险提示\"]}。"
                + "SELL_SOON 表示建议近期出栏，KEEP_FEEDING 表示建议继续饲养；建议时间段从今天或之后开始，跨度不超过 30 天；"
                + "风险 0 到 3 条，用简体中文，语气平实。";
    }

    /** 用户提示词：批次、休药、行情（数字与理由一致） */
    @NonNull
    public static String user(@NonNull SaleBatchOptionVO b, @Nullable MarketTrendVO trend, @NonNull LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("今天：").append(DateFormatter.toDateString(today)).append('\n');
        sb.append("批次：").append(b.getBatchNo()).append("，").append(b.getPenName()).append("，")
                .append(b.getBreedName() == null ? b.getCategoryName() : b.getBreedName() + b.getCategoryName())
                .append('\n');
        sb.append("入栏日期：").append(b.getEntryDate()).append("，当前 ").append(b.getDayAge()).append(" 日龄，存栏 ")
                .append(b.getCurrentCount()).append(' ').append(b.getCountUnitText()).append('\n');
        sb.append("休药：").append(b.isInWithdrawal() ? "休药中，可出栏日期 " + b.getSellableDate() : "不在休药期")
                .append('\n');
        sb.append("体重：没有称重数据\n");
        if (hasPrice(trend)) {
            MarketTrendVO.PriceStats s = trend.getStats();
            sb.append("本省同品类近 30 天出栏价（元/千克）：最低 ").append(s.getMinPrice()).append("（")
                    .append(s.getMinDate()).append("），最高 ").append(s.getMaxPrice()).append("（")
                    .append(s.getMaxDate()).append("），平均 ").append(s.getAvgPrice()).append('\n');
            if (trend.getLatest() != null) {
                sb.append("最新价：").append(trend.getLatest().getPrice()).append("（")
                        .append(trend.getLatest().getPriceDate()).append("）");
                if (trend.getChange() != null && trend.getChange().getChangeRate() != null) {
                    sb.append("，较上一个有数据的日期变化 ")
                            .append(NumberFormatter.formatSignedPercent(trend.getChange().getChangeRate()));
                }
                sb.append('\n');
            }
        } else {
            sb.append("行情：近 7 天没有出栏价\n");
        }
        sb.append("请给出出栏建议。");
        return sb.toString();
    }

    /** 休药期内 RULE 模式（不调用模型） */
    @NonNull
    public static JsonObject rule(@NonNull SaleBatchOptionVO b, @Nullable MarketTrendVO trend,
                                  @NonNull LocalDateTime now) {
        LocalDate sellable = DateFormatter.parseDate(b.getSellableDate());
        LocalDate start = sellable == null || sellable.isBefore(now.toLocalDate()) ? now.toLocalDate() : sellable;
        JsonArray risks = new JsonArray();
        risks.add("休药期未满前出栏有药物残留风险，" + monthDay(DateFormatter.toDateString(start)) + "前不要出栏。");
        if (hasPrice(trend) && trend.getChange() != null && FinanceConst.UP.equals(trend.getChange().getDirection())) {
            risks.add("近期价格上涨，休药期满后价格可能回落。");
        }
        return base(b, trend, now, FinanceConst.NOT_SELLABLE, start, start.plusDays(RULE_WINDOW_DAYS), risks);
    }

    /**
     * 校验并组装模型输出。
     *
     * @return null 表示结构不合规（调用方重试 1 次，仍失败置 FAILED）
     */
    @Nullable
    public static JsonObject fromModel(@Nullable JsonObject out, @NonNull SaleBatchOptionVO b,
                                       @Nullable MarketTrendVO trend, @NonNull LocalDateTime now) {
        if (out == null || !out.has("conclusion")) {
            return null;
        }
        String conclusion = str(out, "conclusion");
        if (!FinanceConst.SELL_SOON.equals(conclusion) && !FinanceConst.KEEP_FEEDING.equals(conclusion)
                && !FinanceConst.NOT_SELLABLE.equals(conclusion)) {
            return null;
        }
        if (FinanceConst.NOT_SELLABLE.equals(conclusion)) {
            conclusion = FinanceConst.KEEP_FEEDING;
        }
        LocalDate today = now.toLocalDate();
        LocalDate start = DateFormatter.parseDate(str(out, "window_start"));
        LocalDate end = DateFormatter.parseDate(str(out, "window_end"));
        if (start == null || start.isBefore(today)) {
            start = today;
        }
        if (end == null || end.isBefore(start)) {
            end = start.plusDays(RULE_WINDOW_DAYS);
        }
        if (ChronoUnit.DAYS.between(start, end) > WINDOW_MAX_DAYS) {
            end = start.plusDays(WINDOW_MAX_DAYS);
        }
        JsonArray risks = new JsonArray();
        JsonElement arr = out.get("risks");
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement e : arr.getAsJsonArray()) {
                if (risks.size() >= MAX_RISKS) {
                    break;
                }
                if (e != null && e.isJsonPrimitive()) {
                    String s = e.getAsString().trim();
                    if (!s.isEmpty()) {
                        risks.add(s.length() > RISK_MAX_CHARS ? s.substring(0, RISK_MAX_CHARS) : s);
                    }
                }
            }
        }
        return base(b, trend, now, conclusion, start, end, risks);
    }

    @NonNull
    private static JsonObject base(@NonNull SaleBatchOptionVO b, @Nullable MarketTrendVO trend,
                                   @NonNull LocalDateTime now, @NonNull String conclusion, @NonNull LocalDate start,
                                   @NonNull LocalDate end, @NonNull JsonArray risks) {
        JsonObject o = new JsonObject();
        o.addProperty("batch_id", b.getBatchId());
        o.addProperty("batch_no", b.getBatchNo());
        o.addProperty("pen_name", b.getPenName());
        o.addProperty("status", FinanceConst.GEN_SUCCESS);
        o.addProperty("conclusion", conclusion);
        o.addProperty("conclusion_text", FinanceConst.conclusionText(conclusion));
        o.addProperty("in_withdrawal", b.isInWithdrawal());
        o.addProperty("generated_at", now.format(DATE_TIME));
        o.addProperty("window_start", DateFormatter.toDateString(start));
        o.addProperty("window_end", DateFormatter.toDateString(end));
        o.addProperty("sellable_date", b.getSellableDate());
        if (b.getWithdrawalDaysLeft() != null) {
            o.addProperty("withdrawal_days_left", b.getWithdrawalDaysLeft());
        }
        o.add("reasons", reasons(b, trend));
        o.add("risks", risks);
        // 没有称重数据，或近 7 天没有价格时依据不足
        o.addProperty("basis_insufficient", true);
        o.addProperty("basis_note", hasPrice(trend) ? "依据不足：测试直连没有读取称重记录，未参考体重。"
                : "依据不足：近 7 天没有出栏价，也没有读取称重记录。");
        return o;
    }

    @NonNull
    private static String monthDay(@Nullable String date) {
        LocalDate d = DateFormatter.parseDate(date);
        return d == null ? "" : DateFormatter.monthDay(d);
    }

    @NonNull
    private static String safe(@Nullable Integer v) {
        return v == null ? "—" : String.valueOf(v);
    }

    @Nullable
    private static String str(@NonNull JsonObject o, @NonNull String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString().trim();
    }

    /** 金额类数字（理由文字中的价格）保留两位 */
    @NonNull
    static String price(@Nullable BigDecimal v) {
        return NumberFormatter.formatNumber(v, 2);
    }
}
