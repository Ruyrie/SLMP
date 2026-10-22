package com.zhimu.slmp.inspect.ui.form;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.databinding.InspectViewItemGroupBinding;
import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;

import java.util.Objects;

/**
 * 巡检项目一行（UI 文档 I02“巡检项目分组”）：左侧项目名（出现异常时下方 error“异常”，漏选时 error“请选择精神状态”），
 * 右侧单选 Chip 组（selectionRequired，放不下时自动换行）。选中“正常”为 secondary-container，选中非正常选项为
 * error-container；只读时不可点。放在 GroupedListLayout 中自动设置首、中、末圆角。
 */
public class InspectItemGroupView extends MaterialCardView {

    /** 选项点击 */
    public interface Listener {
        void onSelected(long itemId, long optionId);
    }

    private final InspectViewItemGroupBinding b;
    private long itemId;

    public InspectItemGroupView(@NonNull Context context) {
        this(context, null);
    }

    public InspectItemGroupView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        b = InspectViewItemGroupBinding.inflate(LayoutInflater.from(context), this, true);
        setCardBackgroundColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerLowest));
        setCardElevation(0f);
        setStrokeWidth(0);
    }

    public long getItemId() {
        return itemId;
    }

    public void bind(@NonNull InspectionFormVO.TemplateItem item, @Nullable Long selected, @Nullable String error,
                     boolean readOnly, @NonNull Listener listener) {
        itemId = item.getItemId();
        b.inspectItemName.setText(item.getItemName());
        if (b.inspectItemChips.getChildCount() != item.getOptions().size()) {
            b.inspectItemChips.removeAllViews();
            for (InspectionFormVO.TemplateOption o : item.getOptions()) {
                Chip chip = (Chip) LayoutInflater.from(getContext())
                        .inflate(R.layout.inspect_item_option_chip, b.inspectItemChips, false);
                chip.setId(View.generateViewId());
                chip.setText(o.getOptionName());
                chip.setTag(o.getOptionId());
                if (o.isAbnormal()) {
                    styleAbnormal(chip);
                }
                ClickThrottle.setOnClickListener(chip, v -> {
                    Object tag = v.getTag();
                    if (tag instanceof Long) {
                        listener.onSelected(itemId, (Long) tag);
                    }
                    // 选中态由 ViewModel 的 selections 刷新；不能取消选中
                    syncChecked(selectedCache);
                });
                b.inspectItemChips.addView(chip);
            }
        }
        selectedCache = selected;
        syncChecked(selected);
        boolean abnormal = false;
        if (selected != null) {
            InspectionFormVO.TemplateOption o = item.findOption(selected);
            abnormal = o != null && o.isAbnormal();
        }
        for (int i = 0; i < b.inspectItemChips.getChildCount(); i++) {
            View c = b.inspectItemChips.getChildAt(i);
            c.setEnabled(!readOnly || Objects.equals(c.getTag(), selected));
            c.setClickable(!readOnly);
        }
        if (!TextUtils.isEmpty(error)) {
            b.inspectItemMark.setText(error);
            b.inspectItemMark.setVisibility(VISIBLE);
            b.inspectItemMark.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        } else if (abnormal) {
            b.inspectItemMark.setText(R.string.inspect_item_abnormal);
            b.inspectItemMark.setVisibility(VISIBLE);
            b.inspectItemMark.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.inspect_ic_error_16, 0, 0, 0);
        } else {
            b.inspectItemMark.setVisibility(GONE);
        }
    }

    @Nullable
    private Long selectedCache;

    private void syncChecked(@Nullable Long selected) {
        for (int i = 0; i < b.inspectItemChips.getChildCount(); i++) {
            Chip c = (Chip) b.inspectItemChips.getChildAt(i);
            c.setChecked(Objects.equals(c.getTag(), selected));
        }
    }

    private void styleAbnormal(@NonNull Chip chip) {
        Context ctx = getContext();
        chip.setChipBackgroundColor(ContextCompat.getColorStateList(ctx, R.color.inspect_color_chip_abnormal_bg));
        ColorStateList fg = ContextCompat.getColorStateList(ctx, R.color.inspect_color_chip_abnormal_text);
        chip.setTextColor(fg);
        chip.setCheckedIconTint(fg);
        chip.setChipStrokeColor(ContextCompat.getColorStateList(ctx, R.color.inspect_color_chip_abnormal_stroke));
    }
}
