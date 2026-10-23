package com.zhimu.slmp.stock.model;

import androidx.annotation.Nullable;

/**
 * 模块09 枚举常量（服务端 2.4 节）：字段一律用 String 承载，便于混淆后仍能按原值解析。
 */
public final class StockEnums {

    private StockEnums() {
    }

    // MATERIAL_TYPE
    public static final String MATERIAL_FEED = "FEED";
    public static final String MATERIAL_DRUG = "DRUG";

    // DRUG_CATEGORY
    public static final String CAT_VET_DRUG = "VET_DRUG";
    public static final String CAT_VACCINE = "VACCINE";
    public static final String CAT_DISINFECTANT = "DISINFECTANT";

    // MEASURE_UNIT
    public static final String UNIT_KG = "KG";
    public static final String UNIT_DOSE = "DOSE";
    public static final String UNIT_G = "G";
    public static final String UNIT_ML = "ML";
    public static final String UNIT_L = "L";

    // QTY_INPUT_UNIT
    public static final String INPUT_MEASURE = "MEASURE";
    public static final String INPUT_PACK = "PACK";

    // FLOW_TYPE
    public static final String FLOW_INBOUND = "INBOUND";
    public static final String FLOW_FEED_USE = "FEED_USE";
    public static final String FLOW_VACCINE_USE = "VACCINE_USE";
    public static final String FLOW_MEDICATION_USE = "MEDICATION_USE";
    public static final String FLOW_DISINFECT_USE = "DISINFECT_USE";
    public static final String FLOW_MANUAL_USE = "MANUAL_USE";
    public static final String FLOW_RETURN = "RETURN";
    public static final String FLOW_OTHER_OUT = "OTHER_OUT";
    public static final String FLOW_LOSS = "LOSS";
    public static final String FLOW_SCRAP = "SCRAP";
    public static final String FLOW_STOCKTAKE_GAIN = "STOCKTAKE_GAIN";
    public static final String FLOW_STOCKTAKE_LOSS = "STOCKTAKE_LOSS";

    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";

    // 流水 / 单据状态（作废只改 status）
    public static final String STATUS_NORMAL = "NORMAL";

    // LOT_STATUS
    public static final String LOT_NORMAL = "NORMAL";
    public static final String LOT_NEAR_EXPIRY = "NEAR_EXPIRY";
    public static final String LOT_EXPIRED = "EXPIRED";
    public static final String LOT_RECALLED = "RECALLED";
    public static final String LOT_BANNED = "BANNED";

    // LOW_STOCK_REASON / BANNER_KIND
    public static final String BANNER_DAYS = "DAYS";
    public static final String BANNER_MIN = "MIN";
    public static final String BANNER_MULTI = "MULTI";

    // OUTBOUND_TYPE
    public static final String OUT_OTHER = "OTHER_OUT";
    public static final String OUT_USE = "USE";
    public static final String OUT_RETURN = "RETURN";

    // OTHER_OUT_DEST
    public static final String DEST_RETURN_SUPPLIER = "RETURN_SUPPLIER";
    public static final String DEST_TRANSFER_FARM = "TRANSFER_FARM";
    public static final String DEST_SALE = "SALE";
    public static final String DEST_OTHER = "OTHER";

    // USE_TARGET_TYPE
    public static final String TARGET_BATCH = "BATCH";
    public static final String TARGET_PEN = "PEN";

    // RETURN_REASON
    public static final String RETURN_QUALITY = "QUALITY";
    public static final String RETURN_PACKAGE_DAMAGED = "PACKAGE_DAMAGED";
    public static final String RETURN_OVER_PURCHASE = "OVER_PURCHASE";
    public static final String RETURN_OTHER = "OTHER";

    // FEED_LOSS_REASON
    public static final String LOSS_MILDEW = "MILDEW";
    public static final String LOSS_DAMP = "DAMP";
    public static final String LOSS_RODENT = "RODENT";
    public static final String LOSS_EXPIRED = "EXPIRED";
    public static final String LOSS_OTHER = "OTHER";

    // 报废原因（字典 SCRAP_REASON）
    public static final String SCRAP_PLATFORM_RECALL = "PLATFORM_RECALL";
    public static final String SCRAP_EXPIRED = "EXPIRED";
    public static final String SCRAP_DAMAGED = "DAMAGED";
    public static final String SCRAP_OTHER = "OTHER";

    // STOCKTAKE_RESULT
    public static final String RESULT_GAIN = "GAIN";
    public static final String RESULT_LOSS = "LOSS";
    public static final String RESULT_MATCH = "MATCH";

    // EDIT_STATE
    public static final String EDIT_EDITABLE = "EDITABLE";
    public static final String EDIT_REQUEST_ONLY = "REQUEST_ONLY";
    public static final String EDIT_PENDING = "PENDING";

    // REQUEST_STATUS（API-M42）
    public static final String REQ_DONE = "DONE";
    public static final String REQ_PROCESSING = "PROCESSING";
    public static final String REQ_NOT_FOUND = "NOT_FOUND";

    // 流水分组（API-M13 / M28）
    public static final String GROUP_ALL = "ALL";
    public static final String GROUP_INBOUND = "INBOUND";
    public static final String GROUP_FEED_USE = "FEED_USE";
    public static final String GROUP_OTHER_OUT = "OTHER_OUT";
    public static final String GROUP_LOSS = "LOSS";
    public static final String GROUP_STOCKTAKE = "STOCKTAKE";
    public static final String GROUP_USE = "USE";
    public static final String GROUP_RETURN = "RETURN";
    public static final String GROUP_SCRAP = "SCRAP";

    // 批号分组（API-M25）
    public static final String LOT_GROUP_ATTENTION = "ATTENTION";
    public static final String LOT_GROUP_NORMAL = "NORMAL";
    public static final String LOT_GROUP_ALL = "ALL";

    // 用途参数
    public static final String PURPOSE_INBOUND = "INBOUND";
    public static final String PURPOSE_OTHER_OUT = "OTHER_OUT";
    public static final String PURPOSE_OUTBOUND = "OUTBOUND";
    public static final String PURPOSE_USE = "USE";
    public static final String PURPOSE_RETURN = "RETURN";
    public static final String PARTNER_SUPPLIER = "SUPPLIER";
    public static final String PARTNER_RETURN_TO = "RETURN_TO";

    // 流水 biz_type（05 号 6.3 节 BIZ_TYPE）
    public static final String BIZ_FEED_RECORD = "FEED_RECORD";
    public static final String BIZ_IMMUNE_RECORD = "IMMUNE_RECORD";
    public static final String BIZ_MEDICATION_RECORD = "MEDICATION_RECORD";
    public static final String BIZ_DISINFECT_RECORD = "DISINFECT_RECORD";
    public static final String BIZ_INBOUND = "INBOUND";
    public static final String BIZ_OUTBOUND = "OUTBOUND";
    public static final String BIZ_LOSS = "LOSS";
    public static final String BIZ_STOCKTAKE = "STOCKTAKE";
    public static final String BIZ_CHANGE_REQUEST = "CHANGE_REQUEST";
    /** U14 修改记录的档案类 biz_type */
    public static final String BIZ_FEED = "FEED";
    public static final String BIZ_DRUG = "DRUG";
    public static final String BIZ_STOCK_SETTING = "STOCK_SETTING";

    /** 记账单位文字 */
    public static String unitText(@Nullable String unit) {
        if (unit == null) {
            return "";
        }
        switch (unit) {
            case UNIT_KG:
                return "千克";
            case UNIT_DOSE:
                return "头份";
            case UNIT_G:
                return "克";
            case UNIT_ML:
                return "毫升";
            case UNIT_L:
                return "升";
            default:
                return unit;
        }
    }

    /** 药品类别文字 */
    public static String categoryText(@Nullable String category) {
        if (category == null) {
            return "";
        }
        switch (category) {
            case CAT_VET_DRUG:
                return "兽药";
            case CAT_VACCINE:
                return "疫苗";
            case CAT_DISINFECTANT:
                return "消毒剂";
            default:
                return category;
        }
    }

    public static boolean isVaccine(@Nullable String category) {
        return CAT_VACCINE.equals(category);
    }
}
