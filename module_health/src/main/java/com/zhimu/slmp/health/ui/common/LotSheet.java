package com.zhimu.slmp.health.ui.common;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.health.util.LotPicker;

import java.math.BigDecimal;

/**
 * 选择库存批号底部面板（图 8-5，D03 疫苗 / D04 药品 / D08 消毒剂共用）：可选批号单选，最早到期的一项标“推荐”；
 * 结存不足标“结存不足”、临期标“临期”；已过期、已停用的批号在“不可选择”分组（禁用态，点击无反应）；
 * 管理视图在辅助文字末尾追加单价，员工不渲染。基于 lib_base 的 OptionSheetDialogFragment，结果 value 为 lot_id。
 */
public final class LotSheet {

    private LotSheet() {
    }

    /**
     * @param title       “选择疫苗批号”
     * @param drugName    “猪瘟活疫苗”，副标题为“猪瘟活疫苗 · 按先到期先出排序”
     * @param purposeText 底部说明中的用途（“登记接种”“登记用药”“登记消毒”）
     */
    public static void show(@NonNull FragmentManager fm, @NonNull String requestKey, @NonNull String title,
                            @Nullable String drugName, @Nullable DrugLotOptionsVO options, @Nullable Long selectedLotId,
                            @Nullable String unit, boolean showPrice, @Nullable BigDecimal need,
                            @NonNull String purposeText) {
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(requestKey)
                .title(title)
                .description((drugName == null ? "" : drugName + " · ") + "按先到期先出排序")
                .footnote("已过期和已停用的批号不能用于" + purposeText + "，只能由所有者或场长登记报废。")
                .selected(selectedLotId == null ? null : String.valueOf(selectedLotId));
        for (LotPicker.Row row : LotPicker.rows(options, unit, showPrice, need)) {
            OptionSheetDialogFragment.Option o = new OptionSheetDialogFragment.Option(String.valueOf(row.lotId),
                    row.label, row.hint).disabled(row.disabled);
            if (row.tagText != null) {
                o.tag(row.tagText, row.tagType);
            }
            b.option(o);
        }
        b.build().showSafely(fm);
    }

    /** 结果中的 lot_id；没有时返回 null */
    @Nullable
    public static Long resultLotId(@NonNull Bundle result) {
        String v = OptionSheetDialogFragment.getValue(result);
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
