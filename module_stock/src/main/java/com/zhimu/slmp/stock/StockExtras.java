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
