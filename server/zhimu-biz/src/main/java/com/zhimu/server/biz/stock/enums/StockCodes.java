package com.zhimu.server.biz.stock.enums;

import java.util.Map;

/**
 * 模块09 枚举编码与中文名（服务端技术准备文档 2.4 节）：库里只存编码，文字由本类统一给出。
 * 与 App 的 {@code StockEnums} 取值逐字一致，新增编码必须同步 App。
 */
public final class StockCodes {

    private StockCodes() {
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

    // RECORD_STATUS
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_VOID = "VOID";

    // VOID_REASON
    public static final String VOID_SOURCE = "SOURCE_VOID";
    public static final String VOID_CORRECTED = "CORRECTED";

    // LOT_STATUS
    public static final String LOT_NORMAL = "NORMAL";
    public static final String LOT_NEAR_EXPIRY = "NEAR_EXPIRY";
    public static final String LOT_EXPIRED = "EXPIRED";
    public static final String LOT_RECALLED = "RECALLED";
    public static final String LOT_BANNED = "BANNED";

    // BANNER_KIND
    public static final String BANNER_DAYS = "DAYS";
    public static final String BANNER_MIN = "MIN";
    public static final String BANNER_MULTI = "MULTI";

    // LOW_STOCK_REASON / REMAINING_BASIS
    public static final String LOW_EMPTY = "EMPTY";
    public static final String LOW_BELOW_MIN = "BELOW_MIN";
    public static final String LOW_BELOW_DAYS = "BELOW_DAYS";
    public static final String BASIS_PLAN = "PLAN";
    public static final String BASIS_USAGE_7D = "USAGE_7D";
    public static final String BASIS_NONE = "NONE";

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

    // SCRAP_REASON
    public static final String SCRAP_PLATFORM_RECALL = "PLATFORM_RECALL";
    public static final String SCRAP_EXPIRED = "EXPIRED";
    public static final String SCRAP_DAMAGED = "DAMAGED";
    public static final String SCRAP_OTHER = "OTHER";

    // LOSS_TYPE
    public static final String LOSS_TYPE_FEED = "FEED_LOSS";
    public static final String LOSS_TYPE_DRUG = "DRUG_SCRAP";

    // STOCKTAKE_RESULT
    public static final String RESULT_GAIN = "GAIN";
    public static final String RESULT_LOSS = "LOSS";
    public static final String RESULT_MATCH = "MATCH";

    // EDIT_STATE
    public static final String EDIT_EDITABLE = "EDITABLE";
    public static final String EDIT_REQUEST_ONLY = "REQUEST_ONLY";
    public static final String EDIT_PENDING = "PENDING";

    // 用途参数（M03 / M27 / M35）
    public static final String PURPOSE_INBOUND = "INBOUND";
    public static final String PURPOSE_OTHER_OUT = "OTHER_OUT";
    public static final String PURPOSE_OUTBOUND = "OUTBOUND";
    public static final String PURPOSE_USE = "USE";
    public static final String PURPOSE_RETURN = "RETURN";
    public static final String PURPOSE_VACCINATE = "VACCINATE";
    public static final String PURPOSE_MEDICATE = "MEDICATE";
    public static final String PURPOSE_DISINFECT = "DISINFECT";

    // 往来单位
    public static final String PARTNER_SUPPLIER = "SUPPLIER";
    public static final String PARTNER_RETURN_TO = "RETURN_TO";

    // 流水分组（M13 / M28）
    public static final String GROUP_ALL = "ALL";
    public static final String GROUP_INBOUND = "INBOUND";
    public static final String GROUP_FEED_USE = "FEED_USE";
    public static final String GROUP_OTHER_OUT = "OTHER_OUT";
    public static final String GROUP_LOSS = "LOSS";
    public static final String GROUP_STOCKTAKE = "STOCKTAKE";
    public static final String GROUP_USE = "USE";
    public static final String GROUP_RETURN = "RETURN";
    public static final String GROUP_SCRAP = "SCRAP";

    // 批号分组
    public static final String LOT_GROUP_ATTENTION = "ATTENTION";
    public static final String LOT_GROUP_NORMAL = "NORMAL";
    public static final String LOT_GROUP_ALL = "ALL";

    // BIZ_TYPE（05 号 6.3 节）
    public static final String BIZ_FEED_RECORD = "FEED_RECORD";
    public static final String BIZ_IMMUNE_RECORD = "IMMUNE_RECORD";
    public static final String BIZ_MEDICATION_RECORD = "MEDICATION_RECORD";
    public static final String BIZ_DISINFECT_RECORD = "DISINFECT_RECORD";
    public static final String BIZ_INBOUND = "INBOUND";
    public static final String BIZ_OUTBOUND = "OUTBOUND";
    public static final String BIZ_LOSS = "LOSS";
    public static final String BIZ_STOCKTAKE = "STOCKTAKE";
    public static final String BIZ_CHANGE_REQUEST = "CHANGE_REQUEST";
    public static final String BIZ_FEED = "FEED";
    public static final String BIZ_DRUG = "DRUG";
    public static final String BIZ_STOCK_SETTING = "STOCK_SETTING";

    // FARM_ROLE
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_STAFF = "STAFF";

    private static final Map<String, String> FLOW_TYPE_TEXT = Map.ofEntries(
            Map.entry(FLOW_INBOUND, "入库"),
            Map.entry(FLOW_FEED_USE, "饲喂领用"),
            Map.entry(FLOW_VACCINE_USE, "接种领用"),
            Map.entry(FLOW_MEDICATION_USE, "用药领用"),
            Map.entry(FLOW_DISINFECT_USE, "消毒领用"),
            Map.entry(FLOW_MANUAL_USE, "领用"),
            Map.entry(FLOW_RETURN, "退货"),
            Map.entry(FLOW_OTHER_OUT, "其他出库"),
            Map.entry(FLOW_LOSS, "报损"),
            Map.entry(FLOW_SCRAP, "报废"),
            Map.entry(FLOW_STOCKTAKE_GAIN, "盘盈"),
            Map.entry(FLOW_STOCKTAKE_LOSS, "盘亏"));

    private static final Map<String, String> UNIT_TEXT = Map.of(
            UNIT_KG, "千克",
            UNIT_DOSE, "头份",
            UNIT_G, "克",
            UNIT_ML, "毫升",
            UNIT_L, "升");

    private static final Map<String, String> CATEGORY_TEXT = Map.of(
            CAT_VET_DRUG, "兽药",
            CAT_VACCINE, "疫苗",
            CAT_DISINFECTANT, "消毒剂");

    private static final Map<String, String> LOT_STATUS_TEXT = Map.of(
            LOT_NORMAL, "正常",
            LOT_NEAR_EXPIRY, "临期",
            LOT_EXPIRED, "已过期",
            LOT_RECALLED, "已停用",
            LOT_BANNED, "禁用药");

    private static final Map<String, String> OUT_TYPE_TEXT = Map.of(
            OUT_OTHER, "其他出库",
            OUT_USE, "领用",
            OUT_RETURN, "退货");

    private static final Map<String, String> DEST_TEXT = Map.of(
            DEST_RETURN_SUPPLIER, "退回供应商",
            DEST_TRANSFER_FARM, "调往其他养殖场",
            DEST_SALE, "销售",
            DEST_OTHER, "其他");

    private static final Map<String, String> RETURN_REASON_TEXT = Map.of(
            RETURN_QUALITY, "质量问题",
            RETURN_PACKAGE_DAMAGED, "包装破损",
            RETURN_OVER_PURCHASE, "采购过多",
            RETURN_OTHER, "其他");

    private static final Map<String, String> FEED_LOSS_REASON_TEXT = Map.of(
            LOSS_MILDEW, "霉变",
            LOSS_DAMP, "受潮",
            LOSS_RODENT, "鼠害",
            LOSS_EXPIRED, "过期",
            LOSS_OTHER, "其他");

    private static final Map<String, String> SCRAP_REASON_TEXT = Map.of(
            SCRAP_PLATFORM_RECALL, "平台停用",
            SCRAP_EXPIRED, "已过期",
            SCRAP_DAMAGED, "破损",
            SCRAP_OTHER, "其他");

    private static final Map<String, String> STOCKTAKE_RESULT_TEXT = Map.of(
            RESULT_GAIN, "盘盈",
            RESULT_LOSS, "盘亏",
            RESULT_MATCH, "相符");

    private static final Map<String, String> LOW_STOCK_REASON_TEXT = Map.of(
            LOW_EMPTY, "结存为 0",
            LOW_BELOW_MIN, "低于库存下限",
            LOW_BELOW_DAYS, "低于预警天数");

    private static final Map<String, String> ROLE_TEXT = Map.of(
            ROLE_OWNER, "所有者",
            ROLE_MANAGER, "场长",
            ROLE_STAFF, "员工");

    public static String flowTypeText(String flowType) {
        return FLOW_TYPE_TEXT.getOrDefault(flowType, flowType);
    }

    public static String unitText(String unit) {
        return unit == null ? "" : UNIT_TEXT.getOrDefault(unit, unit);
    }

    public static String categoryText(String category) {
        return category == null ? null : CATEGORY_TEXT.get(category);
    }

    public static String lotStatusText(String status) {
        return status == null ? null : LOT_STATUS_TEXT.get(status);
    }

    public static String outTypeText(String outType) {
        return outType == null ? null : OUT_TYPE_TEXT.get(outType);
    }

    public static String destinationText(String destination) {
        return destination == null ? null : DEST_TEXT.get(destination);
    }

    public static String returnReasonText(String reason) {
        return reason == null ? null : RETURN_REASON_TEXT.get(reason);
    }

    public static String feedLossReasonText(String reason) {
        return reason == null ? null : FEED_LOSS_REASON_TEXT.get(reason);
    }

    public static String scrapReasonText(String reason) {
        return reason == null ? null : SCRAP_REASON_TEXT.get(reason);
    }

    public static String stocktakeResultText(String result) {
        return result == null ? null : STOCKTAKE_RESULT_TEXT.get(result);
    }

    public static String lowStockReasonText(String reason) {
        return reason == null ? null : LOW_STOCK_REASON_TEXT.get(reason);
    }

    public static String roleText(String role) {
        return role == null ? null : ROLE_TEXT.get(role);
    }

    public static String statusText(String status) {
        if (STATUS_VOID.equals(status)) {
            return "已作废";
        }
        return STATUS_NORMAL.equals(status) ? "正常" : null;
    }

    public static boolean isVaccine(String category) {
        return CAT_VACCINE.equals(category);
    }

    public static boolean isFeed(String materialType) {
        return MATERIAL_FEED.equals(materialType);
    }
}
