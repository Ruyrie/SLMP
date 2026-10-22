package com.zhimu.slmp.common.data;

/**
 * 平台字典类型（05 号文档 6.6 节，API-U10 的 dict_type）。
 */
public final class DictTypes {

    private DictTypes() {
    }

    /** 栏舍类型（L05） */
    public static final String PEN_TYPE = "PEN_TYPE";
    /** 死淘原因（L11） */
    public static final String DEATH_REASON = "DEATH_REASON";
    /** 症状（I02、I05） */
    public static final String SYMPTOM = "SYMPTOM";
    /** 饲料类型（M02） */
    public static final String FEED_TYPE = "FEED_TYPE";
    /** 接种方式（D03） */
    public static final String VACCINATE_METHOD = "VACCINATE_METHOD";
    /** 消毒方式（D07、D08） */
    public static final String DISINFECT_METHOD = "DISINFECT_METHOD";
    /** 报废原因（M07） */
    public static final String SCRAP_REASON = "SCRAP_REASON";
}
