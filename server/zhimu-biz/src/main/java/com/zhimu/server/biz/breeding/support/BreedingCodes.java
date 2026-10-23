package com.zhimu.server.biz.breeding.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 模块05 的枚举编码与中文名（模块05 文档 2.3.8、2.4 节）。 */
public final class BreedingCodes {

    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_MATED = "MATED";
    public static final String STATUS_PREGNANT = "PREGNANT";
    public static final String STATUS_LACTATING = "LACTATING";
    public static final String STATUS_CULLED = "CULLED";
    public static final String STATUS_DEAD = "DEAD";

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_EMPTY = "EMPTY";
    public static final String FILTER_MATED = "MATED";
    public static final String FILTER_PREGNANT = "PREGNANT";
    public static final String FILTER_LACTATING = "LACTATING";
    public static final String FILTER_EXITED = "EXITED";

    public static final String CYCLE_MATED = "MATED";
    public static final String CYCLE_PREGNANT = "PREGNANT";
    public static final String CYCLE_FAILED = "FAILED";
    public static final String CYCLE_FARROWED = "FARROWED";
    public static final String CYCLE_WEANED = "WEANED";

    public static final String RECORD_MATING = "MATING";
    public static final String RECORD_PREG_CHECK = "PREG_CHECK";
    public static final String RECORD_FARROWING = "FARROWING";
    public static final String RECORD_WEANING = "WEANING";
    public static final String RECORD_EXIT = "BREEDING_EXIT";
    public static final String RECORD_INCUBATION = "INCUBATION";

    public static final String INCUBATION_SET = "SET";
    public static final String INCUBATION_CANDLED = "CANDLED";
    public static final String INCUBATION_HATCHED = "HATCHED";

    public static final String GROUP_TODAY = "TODAY";
    public static final String GROUP_WITHIN_7 = "WITHIN_7";
    public static final String GROUP_LATER = "LATER";

    private static final Map<String, String> STATUS_TEXT = Map.of(
            STATUS_EMPTY, "空怀",
            STATUS_MATED, "已配种",
            STATUS_PREGNANT, "妊娠",
            STATUS_LACTATING, "哺乳",
            STATUS_CULLED, "已淘汰",
            STATUS_DEAD, "已死亡");

    private static final Map<String, String> FILTER_TEXT = Map.of(
            FILTER_ALL, "全部",
            FILTER_EMPTY, "空怀",
            FILTER_MATED, "已配种",
            FILTER_PREGNANT, "妊娠",
            FILTER_LACTATING, "哺乳",
            FILTER_EXITED, "已淘汰和死亡");

    private static final Map<String, String> RECORD_TEXT = Map.of(
            RECORD_MATING, "配种",
            RECORD_PREG_CHECK, "妊检",
            RECORD_FARROWING, "产仔",
            RECORD_WEANING, "断奶",
            RECORD_EXIT, "死亡 / 淘汰",
            RECORD_INCUBATION, "孵化");

    private static final Map<String, String> NEXT_ACTION_TEXT = Map.of(
            RECORD_MATING, "登记配种",
            RECORD_PREG_CHECK, "登记妊检",
            RECORD_FARROWING, "登记产仔",
            RECORD_WEANING, "登记断奶");

    private static final Map<String, String> MATING_METHOD_TEXT = Map.of(
            "ARTIFICIAL", "人工授精",
            "NATURAL", "本交");

    private static final Map<String, String> PREG_RESULT_TEXT = Map.of(
            "POSITIVE", "阳性",
            "NEGATIVE", "阴性");

    private static final Map<String, String> CHECK_METHOD_TEXT = Map.of(
            "ULTRASOUND", "B 超",
            "OBSERVATION", "观察返情",
            "OTHER", "其他");

    private static final Map<String, String> EXIT_TYPE_TEXT = Map.of(
            "CULL", "淘汰",
            "DEATH", "死亡");

    private static final Map<String, String> SOURCE_TEXT = Map.of(
            "PURCHASED", "外购",
            "SELF_BRED", "自繁");

    private static final Map<String, String> INCUBATION_STATUS_TEXT = Map.of(
            INCUBATION_SET, "已入孵",
            INCUBATION_CANDLED, "已照蛋",
            INCUBATION_HATCHED, "已出雏");

    private static final Map<String, String> GROUP_TEXT = Map.of(
            GROUP_TODAY, "今天",
            GROUP_WITHIN_7, "7 天内",
            GROUP_LATER, "更晚");

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");

    private BreedingCodes() {
    }

    public static String statusText(String code) {
        return code == null ? null : STATUS_TEXT.get(code);
    }

    public static String filterText(String code) {
        return code == null ? null : FILTER_TEXT.get(code);
    }

    public static String recordText(String code) {
        return code == null ? null : RECORD_TEXT.get(code);
    }

    public static String nextActionText(String code) {
        return code == null ? null : NEXT_ACTION_TEXT.get(code);
    }

    public static String matingMethodText(String code) {
        return code == null ? null : MATING_METHOD_TEXT.get(code);
    }

    public static String pregResultText(String code) {
        return code == null ? null : PREG_RESULT_TEXT.get(code);
    }

    public static String checkMethodText(String code) {
        return code == null ? null : CHECK_METHOD_TEXT.get(code);
    }

    public static String exitTypeText(String code) {
        return code == null ? null : EXIT_TYPE_TEXT.get(code);
    }

    public static String sourceText(String code) {
        return code == null ? null : SOURCE_TEXT.get(code);
    }

    public static String incubationStatusText(String code) {
        return code == null ? null : INCUBATION_STATUS_TEXT.get(code);
    }

    public static String groupText(String group) {
        return group == null ? null : GROUP_TEXT.get(group);
    }

    /** NEXT_ACTION：EMPTY → MATING、MATED → PREG_CHECK、PREGNANT → FARROWING、LACTATING → WEANING、终态为空 */
    public static String nextAction(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case STATUS_EMPTY -> RECORD_MATING;
            case STATUS_MATED -> RECORD_PREG_CHECK;
            case STATUS_PREGNANT -> RECORD_FARROWING;
            case STATUS_LACTATING -> RECORD_WEANING;
            default -> null;
        };
    }

    public static boolean exited(String status) {
        return STATUS_CULLED.equals(status) || STATUS_DEAD.equals(status);
    }

    public static boolean active(String status) {
        return STATUS_EMPTY.equals(status) || STATUS_MATED.equals(status)
                || STATUS_PREGNANT.equals(status) || STATUS_LACTATING.equals(status);
    }

    /** DUE_GROUP：TODAY ≤ 0（含已超期）、WITHIN_7 1–7、LATER ≥ 8 */
    public static String dueGroup(int days) {
        if (days <= 0) {
            return GROUP_TODAY;
        }
        if (days <= 7) {
            return GROUP_WITHIN_7;
        }
        return GROUP_LATER;
    }

    public static String monthDay(LocalDate date) {
        return date == null ? null : date.format(MONTH_DAY);
    }

    /** 畜种 → 母猪 / 母牛 / 母羊；禽类显示“种禽” */
    public static String animalLabel(String species) {
        if ("POULTRY".equals(species)) {
            return "种禽";
        }
        if ("CATTLE".equals(species)) {
            return "母牛";
        }
        if ("SHEEP".equals(species)) {
            return "母羊";
        }
        return "母猪";
    }
}
