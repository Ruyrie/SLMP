package com.zhimu.slmp.ai.ui.play;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiSheetPlaySettingBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.vo.CoursePlayVO;
import com.zhimu.slmp.ai.model.vo.VideoSourceVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;

/**
 * K05 播放设置底部面板（点“1.0x”或 more_vert）：倍速、清晰度（尾部写该档大小）、仅在 Wi-Fi 下播放。选中即生效，复用 Activity 的 CoursePlayViewModel。
 */
public class PlaySettingSheet extends BaseBottomSheetDialogFragment {

    private CoursePlayViewModel vm;

    public static void show(@NonNull FragmentManager fm) {
        PlaySettingSheet s = new PlaySettingSheet();
        s.setArguments(baseArgs(null, null, null, null));
        s.showSafely(fm);
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.ai_play_settings);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), AiInjector.factory()).get(CoursePlayViewModel.class);
        AiSheetPlaySettingBinding b = AiSheetPlaySettingBinding.inflate(inflater, container, false);
        Float current = vm.getSpeed().getValue();
        for (float s : CoursePlayViewModel.SPEEDS) {
            Chip chip = chip(inflater, b.aiSettingSpeed, AiText.speed(s));
            chip.setChecked(current != null && Math.abs(current - s) < 0.01f);
            chip.setOnClickListener(v -> vm.setSpeed(s));
            b.aiSettingSpeed.addView(chip);
        }
        CoursePlayVO play = vm.getPlay().getValue();
        VideoSourceVO src = vm.getCurrentSource().getValue();
        if (play != null) {
            for (VideoSourceVO s : play.getSources()) {
                String label = (AiCodes.QUALITY_480.equals(s.getQuality()) ? "480P" : "720P")
                        + "  " + AiText.sizeMb(s.getSizeBytes()) + " MB";
                Chip chip = chip(inflater, b.aiSettingQuality, label);
                chip.setChecked(src != null && s.getQuality() != null && s.getQuality().equals(src.getQuality()));
                chip.setOnClickListener(v -> vm.setQuality(s.getQuality()));
                b.aiSettingQuality.addView(chip);
            }
        }
        b.aiSettingWifi.setChecked(Boolean.TRUE.equals(vm.getWifiOnly().getValue()));
        b.aiSettingWifi.setOnCheckedChangeListener((v, on) -> vm.setWifiOnly(on));
        return b.getRoot();
    }

    @NonNull
    private static Chip chip(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent, @NonNull String text) {
        Chip chip = (Chip) inflater.inflate(R.layout.ai_item_filter_chip, parent, false);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setId(View.generateViewId());
        return chip;
    }
}
