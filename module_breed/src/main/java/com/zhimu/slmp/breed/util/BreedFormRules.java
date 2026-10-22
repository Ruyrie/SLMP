package com.zhimu.slmp.breed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.model.BreedCodes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本模块表单校验（技术文档 2.4 节、UI 文档 2.5 节）：返回“JSON 字段名 → 提示文案”，每个字段只保留第一条错误，
 * 便于和服务端 40001 的 field_errors 合并显示。日期为 yyyy-MM-dd，“今天”为北京时间。
 */
public final class BreedFormRules {

    // 字段名（与请求体 JSON 一致）
    public static final String F_ANIMAL = "animal_id";
    public static final String F_MATING_DATE = "mating_date";
    public static final String F_MATING_METHOD = "mating_method";
    public static final String F_BOAR = "boar_info";
    public static final String F_CHECK_DATE = "check_date";
    public static final String F_RESULT = "result";
    public static final String F_FARROW_DATE = "farrow_date";
    public static final String F_TOTAL = "total_born";
    public static final String F_LIVE = "live_born";
    public static final String F_STILL = "stillborn";
    public static final String F_TRANSFER = "transfer_to_batch";
    public static final String F_TARGET_PEN = "target_pen_id";
    public static final String F_CAPACITY = "capacity_confirmed";
    public static final String F_WEAN_DATE = "wean_date";
    public static final String F_WEANED = "weaned_count";
    public static final String F_WEIGHT = "litter_weight";
    public static final String F_EAR_TAG = "ear_tag";
    public static final String F_BREED = "breed_id";
    public static final String F_PEN = "pen_id";
    public static final String F_BIRTH = "birth_date";
    public static final String F_SOURCE = "source";
    public static final String F_INITIAL_PARITY = "initial_parity";
    public static final String F_EXIT_DATE = "exit_date";
    public static final String F_REASON = "reason";
    public static final String F_FLOCK = "breeding_batch_id";
    public static final String F_SET_DATE = "set_date";
    public static final String F_SET_COUNT = "set_count";
    public static final String F_CANDLE_DATE = "candle_date";
    public static final String F_FERTILE = "fertile_count";
    public static final String F_HATCH_DATE = "hatch_date";
    public static final String F_HATCHED = "hatched_count";
    public static final String F_HEALTHY = "healthy_count";

    public static final int BOAR_MAX = 20;
    public static final int EAR_TAG_MAX = 20;
    public static final int REASON_MAX = 50;
    public static final int TOTAL_BORN_MAX = 50;
    public static final int PARITY_MAX = 30;
    public static final int SET_COUNT_MAX = 999_999;
    public static final BigDecimal WEIGHT_MAX = new BigDecimal("9999.9");

    public static final String MSG_SELECT_ANIMAL = "请选择母猪";
    public static final String MSG_CAPACITY = "请先确认超出容量";

    private static final String EAR_TAG_PATTERN = "^[A-Za-z0-9-]{1,20}$";

    private BreedFormRules() {
    }

    // ===== 配种 / 妊检（B03、B06 妊检面板） =====

    /**
     * 登记 / 修改配种。
     *
     * @param minMatingDate   日期下限（API-B11 min_mating_date）
     * @param lowerIsWean     下限是否为上次断奶日期（是则本地提示“不能早于上次断奶日期”，否则由服务端校验）
     * @param firstMatingDate 已配种母猪本周期首次配种日期（同一情期再配不能早于它）
     */
    @NonNull
    public static Map<String, String> mating(@Nullable Long animalId, @Nullable String matingDate,
                                             @Nullable String minMatingDate, boolean lowerIsWean,
                                             @Nullable String firstMatingDate, @Nullable String matingMethod,
                                             @Nullable String boarInfo) {
        Map<String, String> e = new LinkedHashMap<>();
        if (animalId == null) {
            e.put(F_ANIMAL, MSG_SELECT_ANIMAL);
        }
        LocalDate d = DateFormatter.parseDate(matingDate);
        LocalDate today = DateFormatter.today();
        if (d == null) {
            e.put(F_MATING_DATE, "请选择配种日期");
        } else if (d.isAfter(today)) {
            e.put(F_MATING_DATE, "配种日期不能晚于今天");
        } else {
            LocalDate first = DateFormatter.parseDate(firstMatingDate);
            LocalDate min = DateFormatter.parseDate(minMatingDate);
            if (first != null && d.isBefore(first)) {
                e.put(F_MATING_DATE, "配种日期不能早于首次配种日期 " + DateFormatter.monthDay(first));
            } else if (min != null && lowerIsWean && d.isBefore(min)) {
                e.put(F_MATING_DATE, "配种日期不能早于上次断奶日期 " + DateFormatter.monthDay(min));
            }
        }
        if (!BreedCodes.METHOD_ARTIFICIAL.equals(matingMethod) && !BreedCodes.METHOD_NATURAL.equals(matingMethod)) {
            e.put(F_MATING_METHOD, "请选择配种方式");
        }
        if (boarInfo != null && boarInfo.trim().length() > BOAR_MAX) {
            e.put(F_BOAR, "最多 20 个字");
        }
        return e;
    }

    /** 登记 / 修改妊检：日期不晚于今天、不早于首次配种日期；必须选结果 */
    @NonNull
    public static Map<String, String> pregCheck(@Nullable String checkDate, @Nullable String firstMatingDate,
                                                @Nullable String result) {
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate d = DateFormatter.parseDate(checkDate);
        LocalDate first = DateFormatter.parseDate(firstMatingDate);
        if (d == null) {
            e.put(F_CHECK_DATE, "请选择妊检日期");
        } else if (d.isAfter(DateFormatter.today())) {
            e.put(F_CHECK_DATE, "妊检日期不能晚于今天");
        } else if (first != null && d.isBefore(first)) {
            e.put(F_CHECK_DATE, "妊检日期不能早于配种日期 " + DateFormatter.monthDay(first));
        }
        if (!BreedCodes.POSITIVE.equals(result) && !BreedCodes.NEGATIVE.equals(result)) {
            e.put(F_RESULT, "请选择妊检结果");
        }
        return e;
    }

    /** 预产期 = 首次配种日期（未配种时为本次配种日期）+ 妊娠期 */
    @Nullable
    public static LocalDate expectedDueDate(@Nullable String firstMatingDate, @Nullable String matingDate,
                                            @Nullable Integer gestationDays) {
        String base = firstMatingDate != null ? firstMatingDate : matingDate;
        return BreedTextFormatter.plusDays(base, gestationDays);
    }

    // ===== 产仔（B04） =====

    /** 三个数量都填写后的和校验文案；一致或未填全时为 null */
    @Nullable
    public static String sumError(@Nullable CharSequence total, @Nullable CharSequence live,
                                  @Nullable CharSequence still) {
        Integer t = SubmitSupport.parseInt(total);
        Integer l = SubmitSupport.parseInt(live);
        Integer s = SubmitSupport.parseInt(still);
        if (t == null || l == null || s == null || l + s == t) {
            return null;
        }
        return "活仔 " + l + " 头 + 死胎 " + s + " 头 = " + (l + s) + " 头，与总产仔 " + t + " 头不一致";
    }

    /**
     * 登记 / 修改产仔。sum 错误同时写入活仔、死胎两个字段（页面横跨两栏只显示一次）。
     *
     * @param transfer       是否转为新批次（修改模式传 false）
     * @param overCapacity   入栏后是否超容量（本地预判或 42206）
     * @param capacityOk     是否已勾选“我已确认，仍要入栏”
     */
    @NonNull
    public static Map<String, String> farrowing(@Nullable Long animalId, @Nullable String farrowDate,
                                                @Nullable String firstMatingDate, @Nullable CharSequence total,
                                                @Nullable CharSequence live, @Nullable CharSequence still,
                                                boolean transfer, boolean overCapacity, boolean capacityOk) {
        Map<String, String> e = new LinkedHashMap<>();
        if (animalId == null) {
            e.put(F_ANIMAL, MSG_SELECT_ANIMAL);
        }
        LocalDate d = DateFormatter.parseDate(farrowDate);
        LocalDate first = DateFormatter.parseDate(firstMatingDate);
        if (d == null) {
            e.put(F_FARROW_DATE, "请选择产仔日期");
        } else if (d.isAfter(DateFormatter.today())) {
            e.put(F_FARROW_DATE, "产仔日期不能晚于今天");
        } else if (first != null && d.isBefore(first)) {
            e.put(F_FARROW_DATE, "产仔日期不能早于配种日期 " + DateFormatter.monthDay(first));
        }
        Integer t = SubmitSupport.parseInt(total);
        Integer l = SubmitSupport.parseInt(live);
        Integer s = SubmitSupport.parseInt(still);
        if (SubmitSupport.isBlank(total)) {
            e.put(F_TOTAL, "请填写总产仔数");
        } else if (t == null || t < 1) {
            e.put(F_TOTAL, "总产仔数至少为 1 头");
        } else if (t > TOTAL_BORN_MAX) {
            e.put(F_TOTAL, "总产仔数最多 50 头");
        }
        if (SubmitSupport.isBlank(live)) {
            e.put(F_LIVE, "请填写活仔数");
        } else if (l == null) {
            e.put(F_LIVE, "请输入整数");
        } else if (t != null && l > t) {
            e.put(F_LIVE, "活仔数不能多于总产仔 " + t + " 头");
        }
        if (SubmitSupport.isBlank(still)) {
            e.put(F_STILL, "请填写死胎数");
        } else if (s == null) {
            e.put(F_STILL, "请输入整数");
        } else if (t != null && s > t) {
            e.put(F_STILL, "死胎数不能多于总产仔 " + t + " 头");
        }
        String sum = sumError(total, live, still);
        if (sum != null && !e.containsKey(F_LIVE) && !e.containsKey(F_STILL)) {
            e.put(F_LIVE, sum);
            e.put(F_STILL, sum);
        }
        if (transfer && l != null && l == 0) {
            e.put(F_TRANSFER, "活仔数为 0，不能转为新批次。请关闭“活仔一键转为新批次”。");
        }
        if (transfer && overCapacity && !capacityOk) {
            e.put(F_CAPACITY, MSG_CAPACITY);
        }
        return e;
    }

    // ===== 断奶（B05） =====

    @NonNull
    public static Map<String, String> weaning(@Nullable Long animalId, @Nullable String weanDate,
                                              @Nullable String farrowDate, @Nullable CharSequence weaned,
                                              @Nullable Integer liveBorn, @Nullable CharSequence weight) {
        Map<String, String> e = new LinkedHashMap<>();
        if (animalId == null) {
            e.put(F_ANIMAL, MSG_SELECT_ANIMAL);
        }
        LocalDate d = DateFormatter.parseDate(weanDate);
        LocalDate farrow = DateFormatter.parseDate(farrowDate);
        if (d == null) {
            e.put(F_WEAN_DATE, "请选择断奶日期");
        } else if (d.isAfter(DateFormatter.today())) {
            e.put(F_WEAN_DATE, "断奶日期不能晚于今天");
        } else if (farrow != null && d.isBefore(farrow)) {
            e.put(F_WEAN_DATE, "断奶日期不能早于产仔日期 " + DateFormatter.monthDay(farrow));
        }
        String weanedError = weanedError(weaned, liveBorn);
        if (weanedError != null) {
            e.put(F_WEANED, weanedError);
        }
        if (!SubmitSupport.isBlank(weight)) {
            BigDecimal w = SubmitSupport.parseDecimal(weight);
            if (w == null || w.signum() <= 0 || w.stripTrailingZeros().scale() > 1) {
                e.put(F_WEIGHT, "请输入大于 0 的重量，最多 1 位小数");
            } else if (w.compareTo(WEIGHT_MAX) > 0) {
                e.put(F_WEIGHT, "断奶窝重不能超过 9,999.9 千克");
            }
        }
        return e;
    }

    /** 断奶仔猪数：0 至产仔时活仔数 */
    @Nullable
    public static String weanedError(@Nullable CharSequence weaned, @Nullable Integer liveBorn) {
        if (SubmitSupport.isBlank(weaned)) {
            return "请填写断奶仔猪数";
        }
        Integer w = SubmitSupport.parseInt(weaned);
        if (w == null) {
            return "请输入整数";
        }
        if (liveBorn != null && w > liveBorn) {
            return weanedLimit(liveBorn);
        }
        return null;
    }

    @NonNull
    public static String weanedLimit(int liveBorn) {
        return "不能多于产仔时的活仔 " + liveBorn + " 头";
    }

    // ===== 档案表单、死亡淘汰（B06） =====

    @NonNull
    public static Map<String, String> animal(@Nullable String earTag, @Nullable Long breedId, @Nullable Long penId,
                                             @Nullable String birthDate, @Nullable String source,
                                             @Nullable CharSequence initialParity) {
        Map<String, String> e = new LinkedHashMap<>();
        String ear = earTag == null ? "" : earTag.trim();
        if (ear.isEmpty()) {
            e.put(F_EAR_TAG, "请填写耳号");
        } else if (!ear.matches(EAR_TAG_PATTERN)) {
            e.put(F_EAR_TAG, "耳号最多 20 个字，只能包含字母、数字和短横线");
        }
        if (breedId == null) {
            e.put(F_BREED, "请选择品种");
        }
        LocalDate birth = DateFormatter.parseDate(birthDate);
        if (birth != null && birth.isAfter(DateFormatter.today())) {
            e.put(F_BIRTH, "出生日期不能晚于今天");
        }
        if (!BreedCodes.SOURCE_PURCHASED.equals(source) && !BreedCodes.SOURCE_SELF_BRED.equals(source)) {
            e.put(F_SOURCE, "请选择来源");
        }
        if (penId == null) {
            e.put(F_PEN, "请选择栏舍");
        }
        if (!SubmitSupport.isBlank(initialParity)) {
            Integer p = SubmitSupport.parseInt(initialParity);
            if (p == null || p > PARITY_MAX) {
                e.put(F_INITIAL_PARITY, "建档时胎次为 0–30");
            }
        }
        return e;
    }

    @NonNull
    public static Map<String, String> exit(@Nullable String exitDate, @Nullable String filingDate,
                                           @Nullable String reason) {
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate d = DateFormatter.parseDate(exitDate);
        LocalDate filing = DateFormatter.parseDate(filingDate);
        if (d == null) {
            e.put(F_EXIT_DATE, "请选择日期");
        } else if (d.isAfter(DateFormatter.today())) {
            e.put(F_EXIT_DATE, "日期不能晚于今天");
        } else if (filing != null && d.isBefore(filing)) {
            e.put(F_EXIT_DATE, "日期不能早于建档日期 " + DateFormatter.monthDay(filing));
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            e.put(F_REASON, "请填写原因");
        } else if (r.length() > REASON_MAX) {
            e.put(F_REASON, "最多 50 字");
        }
        return e;
    }

    // ===== 孵化（B07） =====

    @NonNull
    public static Map<String, String> incubationSet(@Nullable Long flockId, @Nullable String setDate,
                                                    @Nullable CharSequence setCount) {
        Map<String, String> e = new LinkedHashMap<>();
        if (flockId == null) {
            e.put(F_FLOCK, "请选择种禽群");
        }
        LocalDate d = DateFormatter.parseDate(setDate);
        if (d == null) {
            e.put(F_SET_DATE, "请选择入孵日期");
        } else if (d.isAfter(DateFormatter.today())) {
            e.put(F_SET_DATE, "入孵日期不能晚于今天");
        }
        Integer c = SubmitSupport.parseInt(setCount);
        if (SubmitSupport.isBlank(setCount)) {
            e.put(F_SET_COUNT, "请填写入孵蛋数");
        } else if (c == null || c < 1 || c > SET_COUNT_MAX) {
            e.put(F_SET_COUNT, "入孵蛋数为 1–999,999 枚");
        }
        return e;
    }

    @NonNull
    public static Map<String, String> candling(@Nullable String candleDate, @Nullable String setDate,
                                               @Nullable CharSequence fertile, int setCount) {
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate d = DateFormatter.parseDate(candleDate);
        LocalDate set = DateFormatter.parseDate(setDate);
        if (d == null) {
            e.put(F_CANDLE_DATE, "请选择照蛋日期");
        } else if (d.isAfter(DateFormatter.today())) {
            e.put(F_CANDLE_DATE, "照蛋日期不能晚于今天");
        } else if (set != null && d.isBefore(set)) {
            e.put(F_CANDLE_DATE, "照蛋日期不能早于入孵日期");
        }
        String fe = fertileError(fertile, setCount);
        if (fe != null) {
            e.put(F_FERTILE, fe);
        }
        return e;
    }

    @Nullable
    public static String fertileError(@Nullable CharSequence fertile, int setCount) {
        if (SubmitSupport.isBlank(fertile)) {
            return "请填写受精蛋数";
        }
        Integer f = SubmitSupport.parseInt(fertile);
        if (f == null) {
            return "请输入整数";
        }
        if (f > setCount) {
            return "不能多于入孵蛋数 " + BreedTextFormatter.qty(setCount, BreedTextFormatter.UNIT_EGG);
        }
        return null;
    }

    @NonNull
    public static Map<String, String> hatch(@Nullable String hatchDate, @Nullable String candleDate,
                                            @Nullable CharSequence hatched, @Nullable CharSequence healthy,
                                            int fertileCount, boolean transfer, @Nullable Long targetPenId,
                                            boolean overCapacity, boolean capacityOk) {
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate d = DateFormatter.parseDate(hatchDate);
        LocalDate candle = DateFormatter.parseDate(candleDate);
        if (d == null) {
            e.put(F_HATCH_DATE, "请选择出雏日期");
        } else if (d.isAfter(DateFormatter.today())) {
            e.put(F_HATCH_DATE, "出雏日期不能晚于今天");
        } else if (candle != null && d.isBefore(candle)) {
            e.put(F_HATCH_DATE, "出雏日期不能早于照蛋日期");
        }
        String he = hatchedError(hatched, fertileCount);
        if (he != null) {
            e.put(F_HATCHED, he);
        }
        String hh = healthyError(healthy, hatched);
        if (hh != null) {
            e.put(F_HEALTHY, hh);
        }
        Integer h = SubmitSupport.parseInt(healthy);
        if (transfer && h != null && h == 0) {
            e.put(F_TRANSFER, "健雏数为 0，不能转为新批次");
        } else if (transfer && targetPenId == null) {
            e.put(F_TARGET_PEN, "请选择入栏鸡舍");
        } else if (transfer && overCapacity && !capacityOk) {
            e.put(F_CAPACITY, MSG_CAPACITY);
        }
        return e;
    }

    @Nullable
    public static String hatchedError(@Nullable CharSequence hatched, int fertileCount) {
        if (SubmitSupport.isBlank(hatched)) {
            return "请填写出雏数";
        }
        Integer v = SubmitSupport.parseInt(hatched);
        if (v == null) {
            return "请输入整数";
        }
        if (v > fertileCount) {
            return "不能多于受精蛋数 " + BreedTextFormatter.qty(fertileCount, BreedTextFormatter.UNIT_EGG);
        }
        return null;
    }

    @Nullable
    public static String healthyError(@Nullable CharSequence healthy, @Nullable CharSequence hatched) {
        if (SubmitSupport.isBlank(healthy)) {
            return "请填写健雏数";
        }
        Integer v = SubmitSupport.parseInt(healthy);
        if (v == null) {
            return "请输入整数";
        }
        Integer h = SubmitSupport.parseInt(hatched);
        if (h != null && v > h) {
            return "健雏数不能多于出雏数 " + BreedTextFormatter.qty(h, BreedTextFormatter.UNIT_BIRD);
        }
        return null;
    }

    /** 预计出雏日 = 入孵日期 + 孵化期 */
    @Nullable
    public static LocalDate expectedHatchDate(@Nullable String setDate, @Nullable Integer incubationDays) {
        return BreedTextFormatter.plusDays(setDate, incubationDays);
    }
}
