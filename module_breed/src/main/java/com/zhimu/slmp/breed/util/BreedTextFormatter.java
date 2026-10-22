package com.zhimu.slmp.breed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalItemVO;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 本模块的文案拼接（UI 设计文档 2.2、2.5 节）：数字与单位之间空一格（数量用不换行空格），千分位逗号。
 */
public final class BreedTextFormatter {

    public static final String DOT = " · ";
    public static final String UNIT_HEAD = "头";
    public static final String UNIT_EGG = "枚";
    public static final String UNIT_BIRD = "只";

    private BreedTextFormatter() {
    }

    // ===== 日期 =====

    /** 推算卡与提示中的完整日期：本年“11月9日 周一”，跨年“2027年2月10日 周三” */
    @NonNull
    public static String fullDate(@Nullable String date) {
        LocalDate d = DateFormatter.parseDate(date);
        return d == null ? "" : fullDate(d);
    }

    @NonNull
    public static String fullDate(@NonNull LocalDate d) {
        return DateFormatter.monthDay(d) + " " + DateFormatter.weekText(d);
    }

    /** “10月12日”（跨年带年份） */
    @NonNull
    public static String md(@Nullable String date) {
        LocalDate d = DateFormatter.parseDate(date);
        return d == null ? "" : DateFormatter.monthDay(d);
    }

    /** 带星期：“10月22日 周四”；跨年“2027年1月2日 周六” */
    @NonNull
    public static String mdWeek(@Nullable String date) {
        return fullDate(date);
    }

    /** 按天数预推日期：base + days */
    @Nullable
    public static LocalDate plusDays(@Nullable String base, @Nullable Integer days) {
        LocalDate d = DateFormatter.parseDate(base);
        return d == null || days == null ? null : d.plusDays(days);
    }

    // ===== 数字 =====

    @NonNull
    public static String qty(@Nullable Number value, @NonNull String unit) {
        return NumberFormatter.formatQuantity(value, unit);
    }

    @NonNull
    public static String num(@Nullable Number value) {
        return NumberFormatter.formatNumber(value);
    }

    /** 0–1 比例的 1 位百分比：“95.0%” */
    @NonNull
    public static String percent(@Nullable BigDecimal ratio) {
        return ratio == null ? "" : NumberFormatter.formatPercent(ratio);
    }

    /** a ÷ b（4 位小数）；b 为 0 时为 null */
    @Nullable
    public static BigDecimal ratio(int a, int b) {
        if (b <= 0) {
            return null;
        }
        return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 4, RoundingMode.HALF_UP);
    }

    /** 受精率：“95.0%（9,120 ÷ 9,600）” */
    @NonNull
    public static String fertilityText(int fertile, int setCount) {
        BigDecimal r = ratio(fertile, setCount);
        if (r == null) {
            return "";
        }
        return percent(r) + "（" + num(fertile) + " ÷ " + num(setCount) + "）";
    }

    // ===== 母猪 =====

    @NonNull
    public static String parity(int parity) {
        return "第 " + parity + " 胎";
    }

    /** “0131 · 长大二元” */
    @NonNull
    public static String animalTitle(@Nullable String earTag, @Nullable String breedName) {
        return nz(earTag) + DOT + nz(breedName);
    }

    /** “0161 · 长大二元 · 第 2 胎” */
    @NonNull
    public static String animalLabel(@Nullable String earTag, @Nullable String breedName, int parity) {
        return animalTitle(earTag, breedName) + DOT + parity(parity);
    }

    /** 临产标签：“今天预产”“还有 3 天”“超 2 天” */
    @NonNull
    public static String dueTag(int daysToDue) {
        if (daysToDue == 0) {
            return "今天预产";
        }
        if (daysToDue < 0) {
            return "超 " + (-daysToDue) + " 天";
        }
        return "还有 " + daysToDue + " 天";
    }

    /** 是否临产（距预产 0–3 天，含已超期） */
    public static boolean isDueSoon(@Nullable Integer daysToDue) {
        return daysToDue != null && daysToDue <= 3;
    }

    /** B01 列表项说明（按状态，最多两行） */
    @NonNull
    public static String animalSubtitle(@NonNull BreedingAnimalItemVO a) {
        StringBuilder sb = new StringBuilder(nz(a.getPenName())).append(DOT).append(parity(a.getParity()));
        String status = a.getStatus();
        if (BreedCodes.PREGNANT.equals(status)) {
            sb.append(DOT).append("预产期 ").append(md(a.getExpectedDueDate()));
            Integer days = a.getDaysToDue();
            if (days != null && days > 3) {
                sb.append(DOT).append("还有 ").append(days).append(" 天");
            }
        } else if (BreedCodes.LACTATING.equals(status)) {
            sb.append(DOT).append(md(a.getLastFarrowDate())).append("产仔");
            if (a.getLiveBorn() != null) {
                sb.append(DOT).append("活仔 ").append(qty(a.getLiveBorn(), UNIT_HEAD));
            }
            if (a.getNursingDays() != null) {
                sb.append(DOT).append("已哺乳 ").append(a.getNursingDays()).append(" 天");
            }
        } else if (BreedCodes.MATED.equals(status)) {
            sb.append(DOT).append(md(a.getFirstMatingDate())).append("配种");
            if (a.getDaysSinceMating() != null) {
                sb.append(DOT).append("已 ").append(a.getDaysSinceMating()).append(" 天");
                if (Boolean.TRUE.equals(a.getPendingCheck())) {
                    sb.append("，待妊检");
                }
            }
        } else if (BreedCodes.EMPTY.equals(status)) {
            if (a.getLastWeanDate() != null) {
                sb.append(DOT).append(md(a.getLastWeanDate())).append("断奶");
            }
            sb.append(DOT).append("可配种");
        } else if (BreedCodes.isExited(status)) {
            sb.append(DOT).append(md(a.getExitDate())).append(nz(a.getExitTypeText()));
            if (a.getExitReason() != null) {
                sb.append(DOT).append(a.getExitReason());
            }
        }
        return sb.toString();
    }

    /** B01 临产横幅：最多 2 头逐一列出，超过 2 头合并；无临产时为 null */
    @Nullable
    public static String dueBanner(@Nullable BreedingOverviewVO o) {
        if (o == null) {
            return null;
        }
        List<BreedingOverviewVO.DueSoonItem> list = o.getDueSoon();
        int total = o.getDueSoonTotal() != null ? o.getDueSoonTotal() : list.size();
        if (total <= 0 || list.isEmpty()) {
            return null;
        }
        if (total > 2) {
            return "今天和 3 天内共有 " + total + " 头母猪预产。";
        }
        StringBuilder sb = new StringBuilder();
        for (BreedingOverviewVO.DueSoonItem d : list) {
            int days = d.getDaysToDue();
            sb.append(nz(d.getEarTag()));
            if (days == 0) {
                sb.append(" 今天预产。");
            } else if (days < 0) {
                sb.append(" 已超预产期 ").append(-days).append(" 天。");
            } else {
                sb.append(" 预产期 ").append(mdWeek(d.getExpectedDueDate())).append("，还有 ").append(days)
                        .append(" 天。");
            }
        }
        return sb.toString();
    }

    // ===== 待产 / 待出雏 =====

    /** 提醒规则说明（UI 2.5）：两类提醒只发给所有者和场长，按角色称呼 */
    @NonNull
    public static String ruleText(@Nullable String role, @Nullable String tab) {
        String prefix = BreedExtras.TAB_HATCH.equals(tab) ? "预计出雏日前 1 天和当天各提醒一次"
                : "预产期前 3 天和当天各提醒一次";
        String to;
        if (FarmContextVO.ROLE_OWNER.equals(role)) {
            to = "发给你和场长";
        } else if (FarmContextVO.ROLE_MANAGER.equals(role)) {
            to = "发给所有者和你";
        } else {
            to = "发给所有者和场长";
        }
        return prefix + "，" + to;
    }

    /** 分组标题：“今天 · 1 头”“7 天内 · 2 批” */
    @NonNull
    public static String groupTitle(@Nullable String groupText, @Nullable Integer count, @NonNull String unit) {
        String g = nz(groupText);
        return count == null ? g : g + DOT + count + " " + unit;
    }

    /** 待产主文字：“0131 · 产房 · 第 3 胎” */
    @NonNull
    public static String farrowDueTitle(@NonNull FarrowDueItemVO i) {
        return nz(i.getEarTag()) + DOT + nz(i.getPenName()) + DOT + parity(i.getParity());
    }

    /** 待产第二行：“6月27日配种 · 预产期 10月19日”（今天不加星期，其余加星期） */
    @NonNull
    public static String farrowDueLine2(@NonNull FarrowDueItemVO i) {
        String due = i.getDaysToDue() == 0 ? md(i.getExpectedDueDate()) : mdWeek(i.getExpectedDueDate());
        return md(i.getFirstMatingDate()) + "配种" + DOT + "预产期 " + due;
    }

    /** 待出雏第二行：“9月28日入孵 · 入孵 9,600 枚 · 受精蛋 9,120 枚 · 预计出雏 10月19日” */
    @NonNull
    public static String hatchDueLine2(@NonNull HatchDueItemVO i) {
        StringBuilder sb = new StringBuilder(md(i.getSetDate())).append("入孵").append(DOT)
                .append("入孵 ").append(qty(i.getSetCount(), UNIT_EGG));
        if (i.getFertileCount() != null) {
            sb.append(DOT).append("受精蛋 ").append(qty(i.getFertileCount(), UNIT_EGG));
        }
        String due = i.getDaysToHatch() == 0 ? md(i.getExpectedHatchDate()) : mdWeek(i.getExpectedHatchDate());
        sb.append(DOT).append("预计出雏 ").append(due);
        return sb.toString();
    }

    /** 天数徽标文字：0 为“今天”，负数为“超 n”，其余为数字（下方另显示“天”） */
    @NonNull
    public static String badgeNumber(int days) {
        if (days == 0) {
            return "今天";
        }
        return days < 0 ? "超" + (-days) : String.valueOf(days);
    }

    /** 跨养殖场横幅（只有一个候选时写出名称） */
    @Nullable
    public static String switchBanner(@Nullable String currentFarm, @Nullable BreedingOverviewVO o) {
        if (o == null || o.isHasIncubation() || o.getSwitchFarms().isEmpty()) {
            return null;
        }
        List<BreedingOverviewVO.SwitchFarm> farms = o.getSwitchFarms();
        String target = farms.size() == 1 ? nz(farms.get(0).getFarmName()) : "禽类养殖场";
        return nz(currentFarm) + "没有孵化记录。切到" + target + "可以查看待出雏，预计出雏日提前 1 天和当天各提醒一次。";
    }

    // ===== 成功提示 =====

    @NonNull
    public static String matingDone(@Nullable String expectedDueDate) {
        return expectedDueDate == null ? "已登记配种" : "已登记配种，预产期 " + fullDate(expectedDueDate);
    }

    @NonNull
    public static String checkDone(@Nullable String earTag, boolean positive) {
        return "已登记妊检，" + nz(earTag) + (positive ? " 转为妊娠" : " 回到空怀");
    }

    @NonNull
    public static String farrowDone(@Nullable String earTag, boolean batchCreated) {
        return batchCreated ? "已登记产仔，新批次已入栏" : "已登记产仔，" + nz(earTag) + " 转为哺乳";
    }

    @NonNull
    public static String weanDone(@Nullable String earTag) {
        return "已登记断奶，" + nz(earTag) + " 回到空怀";
    }

    @NonNull
    public static String exitDone(@Nullable String earTag, boolean death) {
        return death ? "已登记死亡，" + nz(earTag) + " 的历史记录已保留"
                : "已登记淘汰，" + nz(earTag) + " 不再出现在配种选择中";
    }

    @NonNull
    public static String setDone(@Nullable String expectedHatchDate) {
        LocalDate d = DateFormatter.parseDate(expectedHatchDate);
        if (d == null) {
            return "已登记入孵";
        }
        return "已登记入孵，预计 " + fullDate(d) + "出雏";
    }

    @NonNull
    public static String hatchDone(boolean batchCreated) {
        return batchCreated ? "已登记出雏，新批次已入栏" : "已登记出雏";
    }

    @NonNull
    public static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }
}
