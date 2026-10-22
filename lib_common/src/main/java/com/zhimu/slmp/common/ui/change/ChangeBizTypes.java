package com.zhimu.slmp.common.ui.change;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.R;

/**
 * 修改申请的记录类型（05 号 6.3 节 BIZ_TYPE）对应的显示文字与前置图标（02 号 11.3 节）。
 * 服务端返回 biz_type_text 时优先用服务端文字。
 */
public final class ChangeBizTypes {

    private ChangeBizTypes() {
    }

    @NonNull
    public static String textOf(@Nullable String bizType, @Nullable String serverText) {
        if (serverText != null && !serverText.isEmpty()) {
            return serverText;
        }
        if (bizType == null) {
            return "记录";
        }
        switch (bizType) {
            case "FEED_RECORD":
                return "投喂记录";
            case "INBOUND":
                return "饲料入库";
            case "SALE_ORDER":
                return "出栏单";
            case "BATCH_DEATH":
                return "死淘记录";
            case "BATCH_ENTRY":
                return "入栏记录";
            case "BATCH_TRANSFER":
                return "转栏记录";
            case "WEIGH_RECORD":
                return "称重记录";
            case "INSPECTION":
                return "巡检记录";
            case "IMMUNE_RECORD":
                return "接种记录";
            case "MEDICATION_RECORD":
                return "用药记录";
            case "DISINFECT_RECORD":
                return "消毒记录";
            default:
                return "记录";
        }
    }

    @DrawableRes
    public static int iconOf(@Nullable String bizType) {
        if (bizType == null) {
            return R.drawable.ic_edit_note;
        }
        switch (bizType) {
            case "FEED_RECORD":
                return R.drawable.ic_rice_bowl;
            case "INBOUND":
            case "STOCKTAKE":
            case "LOSS":
                return R.drawable.ic_inventory_2;
            case "SALE_ORDER":
                return R.drawable.ic_local_shipping;
            case "BATCH_DEATH":
                return R.drawable.ic_heart_minus;
            case "BATCH_ENTRY":
            case "BATCH_TRANSFER":
                return R.drawable.ic_fence;
            case "WEIGH_RECORD":
                return R.drawable.ic_scale;
            case "INSPECTION":
                return R.drawable.ic_fact_check;
            case "IMMUNE_RECORD":
                return R.drawable.ic_vaccines;
            case "MEDICATION_RECORD":
                return R.drawable.ic_medication;
            case "DISINFECT_RECORD":
                return R.drawable.ic_sanitizer;
            default:
                return R.drawable.ic_edit_note;
        }
    }
}
