package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.vo.ReportVos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 经营报表 AI 摘要的输入：指标先按页面展示格式写成文字（比率为百分数、金额 / 数量带千分位与单位），再交给模型，
 * 避免模型照抄“死亡率0.0181”“资金余额99380.00”这类原始数字。
 */
public final class ReportSummaryPrompt {

    public static final String VERSION = "v2";
    public static final String SYSTEM = "你是养殖场经营分析助手，只输出 JSON。";

    private ReportSummaryPrompt() {
    }

    public static String build(ReportVos.ReportMetrics metrics, ReportVos.BatchProfits profits) {
        StringBuilder sb = new StringBuilder("经营报表指标（已按展示格式给出）：\n");
        List<String> units = new ArrayList<>();
        if (metrics != null && metrics.getStock() != null) {
            for (ReportVos.StockMetric stock : metrics.getStock()) {
                String unit = unitText(stock.getCountUnitText());
                units.add(unit);
                sb.append("- 存栏：期初 ").append(count(stock.getBegin(), unit))
                        .append("，期末 ").append(count(stock.getEnd(), unit))
                        .append("，较期初 ").append(signedCount(stock.getChange(), unit)).append('\n');
            }
        }
        sb.append("- 死亡率：").append(mortalityText(metrics == null ? null : metrics.getMortality(), deathUnit(units)))
                .append('\n');
        ReportVos.Feed feed = metrics == null ? null : metrics.getFeed();
        sb.append("- 饲料消耗：").append(feed == null || feed.getKg() == null ? "暂无数据"
                        : decimal(feed.getKg(), "#,##0.##") + " 千克")
                .append(changeText(feed == null ? null : feed.getComparable(), feed == null ? null : feed.getChangeRate()))
                .append('\n');
        ReportVos.Balance balance = metrics == null ? null : metrics.getBalance();
        sb.append("- 资金余额：").append(balance == null || balance.getAmount() == null ? "暂无数据"
                        : money(balance.getAmount()))
                .append(changeText(balance == null ? null : balance.getComparable(),
                        balance == null ? null : balance.getChangeRate()))
                .append('\n');
        sb.append("批次盈亏：").append(profitText(profits)).append('\n');
        sb.append("请输出 JSON：{\"highlight\": \"亮点\", \"problem\": \"问题\", \"suggestion\": \"建议\"}，每段不超过 100 字。")
                .append("数字必须来自上面的输入，并照上面的格式书写：比率写百分数（如 1.81%），金额带千分位和“元”（如 99,380.00 元），")
                .append("数量带千分位和单位（如 9,300 只）；不要写成 0.0181、99380.00 这类原始数字。");
        return sb.toString();
    }

    static String mortalityText(ReportVos.Mortality mortality, String deathUnit) {
        if (mortality == null || mortality.getRate() == null) {
            return "暂无数据";
        }
        StringBuilder sb = new StringBuilder(percent(mortality.getRate(), 2))
                .append("（死淘 ").append(count(mortality.getDeathCount(), deathUnit)).append('）');
        if (Boolean.TRUE.equals(mortality.getComparable()) && mortality.getChangePoint() != null) {
            BigDecimal point = mortality.getChangePoint().multiply(BigDecimal.valueOf(100));
            sb.append("，较上期").append(point.signum() > 0 ? "上升 " : point.signum() < 0 ? "下降 " : "持平 ")
                    .append(decimal(point.abs(), "0.00")).append(" 个百分点");
        } else {
            sb.append("，上期无法比较");
        }
        return sb.toString();
    }

    static String profitText(ReportVos.BatchProfits profits) {
        if (profits == null || profits.getItems() == null || profits.getItems().isEmpty()) {
            return "本期没有已结算批次";
        }
        List<String> parts = new ArrayList<>();
        if (profits.getProfitTotal() != null) {
            parts.add("合计" + profitWord(profits.getProfitTotal()));
        }
        for (ReportVos.ProfitItem item : profits.getItems()) {
            boolean withFarm = item.getFarmName() != null && !item.getFarmName().isBlank();
            String name = (item.getBatchNo() == null ? "批次" : item.getBatchNo())
                    + (withFarm ? "（" + item.getFarmName() + "）" : " ");
            parts.add(name + (item.getProfit() == null ? "暂无数据" : profitWord(item.getProfit())));
        }
        return String.join("；", parts);
    }

    private static String profitWord(BigDecimal amount) {
        int sign = amount.signum();
        return (sign > 0 ? "盈利 " : sign < 0 ? "亏损 " : "持平 ") + money(amount.abs());
    }

    private static String changeText(Boolean comparable, BigDecimal rate) {
        if (!Boolean.TRUE.equals(comparable) || rate == null) {
            return "，较上期无法比较";
        }
        String text = percent(rate.abs(), 1);
        return rate.signum() > 0 ? "，较上期 +" + text : rate.signum() < 0 ? "，较上期 -" + text : "，较上期持平";
    }

    /** 0.0181 → “1.81%” */
    static String percent(BigDecimal ratio, int digits) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(digits, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** 99380 → “99,380.00 元” */
    static String money(BigDecimal amount) {
        return decimal(amount, "#,##0.00") + " 元";
    }

    static String count(Integer value, String unit) {
        return decimal(BigDecimal.valueOf(value == null ? 0 : value), "#,##0") + " " + unit;
    }

    private static String signedCount(Integer value, String unit) {
        int v = value == null ? 0 : value;
        return (v > 0 ? "+" : v < 0 ? "-" : "") + count(Math.abs(v), unit);
    }

    private static String decimal(BigDecimal value, String pattern) {
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    private static String unitText(String unit) {
        return unit == null || unit.isBlank() ? "头" : unit;
    }

    /** 死淘数跨品类合计：单一品类用它的单位，混养写“头/只” */
    private static String deathUnit(List<String> units) {
        Set<String> distinct = new LinkedHashSet<>(units);
        return distinct.isEmpty() ? "头" : String.join("/", distinct);
    }
}
