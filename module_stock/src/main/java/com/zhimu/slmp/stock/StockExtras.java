package com.zhimu.slmp.stock;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.router.RouterPath;

/**
 * 本组件页面参数键。RouterPath 中已有的直接引用（feed_id、record_id、category）；
 * material_type、drug_id、lot_id、record_kind、keyword 为本组件新增，交付说明中列出，待主会话合并进 RouterPath。
 */
public final class StockExtras {

    private StockExtras() {
    }

    /** 物资类型（String：FEED / DRUG，流水页） */
    public static final String EXTRA_MATERIAL_TYPE = "material_type";
    /** 饲料编号（long，M02 编辑、M03 预选、流水页筛选） */
    public static final String EXTRA_FEED_ID = RouterPath.EXTRA_FEED_ID;
    /** 药品编号（long，M08 预选、M09 编辑、M11 预选、流水页筛选） */
    public static final String EXTRA_DRUG_ID = "drug_id";
    /** 批号编号（long，M06、M07、M11 预选） */
    public static final String EXTRA_LOT_ID = "lot_id";
    /** 单据编号（long，入库单、出库单、报损报废、盘点的只读页） */
    public static final String EXTRA_RECORD_ID = RouterPath.EXTRA_RECORD_ID;
    /** 单据类型（String：LOSS / STOCKTAKE，单据只读页） */
    public static final String EXTRA_RECORD_KIND = "record_kind";
    /** 搜索词（String，M08 → M09 名称面板） */
    public static final String EXTRA_KEYWORD = "keyword";
    /** 药品类别（String：VET_DRUG / VACCINE / DISINFECTANT，M05 → M09 默认类别） */
    public static final String EXTRA_CATEGORY = RouterPath.EXTRA_CATEGORY;
    /** 页面标题（String，流水页“育肥全价料 · 流水”） */
    public static final String EXTRA_TITLE = RouterPath.EXTRA_TITLE;
    /** M09 保存后回传的药品编号（long，Activity Result） */
    public static final String RESULT_DRUG_ID = "drug_id";

    /** 单据只读页类型 */
    public static final String KIND_LOSS = "LOSS";
    public static final String KIND_STOCKTAKE = "STOCKTAKE";

    /** 页面参数中的编号：long / int / 数字字符串都接受，0 与非法值返回 null */
    @Nullable
    public static Long readId(@Nullable Object v) {
        long id = 0;
        if (v instanceof Number) {
            id = ((Number) v).longValue();
        } else if (v instanceof String) {
            try {
                id = Long.parseLong(((String) v).trim());
            } catch (NumberFormatException e) {
                id = 0;
            }
        }
        return id > 0 ? id : null;
    }
}
