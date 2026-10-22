package com.zhimu.slmp.breed.model;

/**
 * 本模块枚举取值（服务端文档 2.3.8 节）与 BIZ_TYPE。
 */
public final class BreedCodes {

    private BreedCodes() {
    }

    // BREEDING_STATUS
    public static final String EMPTY = "EMPTY";
    public static final String MATED = "MATED";
    public static final String PREGNANT = "PREGNANT";
    public static final String LACTATING = "LACTATING";
    public static final String CULLED = "CULLED";
    public static final String DEAD = "DEAD";

    // ANIMAL_FILTER（另含 EMPTY、MATED、PREGNANT、LACTATING）
    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_EXITED = "EXITED";

    // REGISTER_TYPE / NEXT_ACTION / SUBMISSION_TYPE
    public static final String TYPE_MATING = "MATING";
    public static final String TYPE_PREG_CHECK = "PREG_CHECK";
    public static final String TYPE_FARROWING = "FARROWING";
    public static final String TYPE_WEANING = "WEANING";
    public static final String TYPE_INCUBATION = "INCUBATION";
    public static final String TYPE_HATCH = "HATCH";

    // MATING_METHOD
    public static final String METHOD_ARTIFICIAL = "ARTIFICIAL";
    public static final String METHOD_NATURAL = "NATURAL";

    // PREG_RESULT
    public static final String POSITIVE = "POSITIVE";
    public static final String NEGATIVE = "NEGATIVE";

    // EXIT_TYPE
    public static final String EXIT_CULL = "CULL";
    public static final String EXIT_DEATH = "DEATH";

    // ANIMAL_SOURCE
    public static final String SOURCE_PURCHASED = "PURCHASED";
    public static final String SOURCE_SELF_BRED = "SELF_BRED";

    // INCUBATION_STATUS
    public static final String INC_SET = "SET";
    public static final String INC_CANDLED = "CANDLED";
    public static final String INC_HATCHED = "HATCHED";

    // DUE_GROUP
    public static final String GROUP_TODAY = "TODAY";
    public static final String GROUP_WITHIN_7 = "WITHIN_7";
    public static final String GROUP_LATER = "LATER";

    // FARM_KIND
    public static final String KIND_LIVESTOCK = "LIVESTOCK";
    public static final String KIND_POULTRY = "POULTRY";

    // BIZ_TYPE（05 号 6.3 节）
    public static final String BIZ_BREEDING_ANIMAL = "BREEDING_ANIMAL";
    public static final String BIZ_MATING = "MATING";
    public static final String BIZ_PREG_CHECK = "PREG_CHECK";
    public static final String BIZ_FARROWING = "FARROWING";
    public static final String BIZ_WEANING = "WEANING";
    public static final String BIZ_BREEDING_EXIT = "BREEDING_EXIT";
    public static final String BIZ_INCUBATION = "INCUBATION";

    /** 品种未设置待妊检天数时取 21 天（服务端 2.7.1） */
    public static final int DEFAULT_CHECK_DAYS = 21;

    public static boolean isExited(String status) {
        return CULLED.equals(status) || DEAD.equals(status);
    }
}
