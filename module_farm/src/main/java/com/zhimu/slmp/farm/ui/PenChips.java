package com.zhimu.slmp.farm.ui;

import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.model.vo.PenOptionVO;

import java.util.List;
import java.util.Set;

/**
 * 负责栏舍多选 Chip 的重建（F08 邀请成员、F09 调整栏舍面板）。
 */
public final class PenChips {

    private PenChips() {
    }

    /** 按选项重建 ChipGroup 并设置选中；点按时回调 penId（页面不保存选中状态，由 ViewModel 决定） */
    public static void render(@NonNull ChipGroup group, @Nullable List<PenOptionVO> options,
                              @Nullable Set<Long> selected, @NonNull Consumer<Long> onToggle) {
        if (options == null) {
            return;
        }
        boolean rebuild = group.getChildCount() != options.size();
        if (!rebuild) {
            for (int i = 0; i < options.size(); i++) {
                Object tag = group.getChildAt(i).getTag();
                if (!(tag instanceof Long) || (Long) tag != options.get(i).getPenId()) {
                    rebuild = true;
                    break;
                }
            }
        }
        if (rebuild) {
            group.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(group.getContext());
            for (PenOptionVO o : options) {
                Chip chip = (Chip) inflater.inflate(R.layout.farm_item_pen_chip, group, false);
                chip.setText(o.getName());
                chip.setTag(o.getPenId());
                chip.setCheckable(true);
                chip.setOnClickListener(v -> onToggle.accept(o.getPenId()));
                group.addView(chip);
            }
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            Object tag = chip.getTag();
            chip.setChecked(selected != null && tag instanceof Long && selected.contains(tag));
        }
    }
}
