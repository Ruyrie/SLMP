package com.zhimu.slmp.inspect.ui.today;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.databinding.InspectSheetDeadlineBinding;
import com.zhimu.slmp.inspect.di.InspectInjector;
import com.zhimu.slmp.inspect.model.vo.InspectionSettingVO;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;

/**
 * I01 巡检截止时间面板（只有所有者、场长能打开）：截止时间输入框（点击在面板内展开时、分两列滚轮，不另弹对话框）、
 * 快捷时间 Chip（滚轮选了其他时刻时全部不选中）、提醒规则两行随所选时刻联动；保存 API-I05，失败时面板不关闭。
 * 以宿主 Activity 的 InspectTodayViewModel 读写。
 */
public class DeadlineBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String REQUEST_KEY = "inspect_deadline_sheet";

    private InspectSheetDeadlineBinding b;
    private InspectTodayViewModel vm;
    private boolean syncing;

    @NonNull
    public static DeadlineBottomSheet newInstance(@Nullable String farmName) {
        DeadlineBottomSheet f = new DeadlineBottomSheet();
        String desc = (farmName == null ? "本场" : farmName) + "每天的巡检截止时间，对全场栏舍生效。";
        f.setArguments(baseArgs(REQUEST_KEY, "巡检截止时间", desc, null));
        return f;
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), InspectInjector.factory()).get(InspectTodayViewModel.class);
        b = InspectSheetDeadlineBinding.inflate(inflater, container, false);
        b.inspectDeadlineHour.setMinValue(0);
        b.inspectDeadlineHour.setMaxValue(23);
        b.inspectDeadlineMinute.setMinValue(0);
        b.inspectDeadlineMinute.setMaxValue(59);
        NumberFormatterHolder.apply(b.inspectDeadlineHour);
        NumberFormatterHolder.apply(b.inspectDeadlineMinute);
        b.inspectDeadlineInput.setFocusable(false);
        ClickThrottle.setOnClickListener(b.inspectDeadlineInput, v -> toggleWheel());
        b.inspectDeadlineLayout.setEndIconOnClickListener(v -> toggleWheel());
        b.inspectDeadlineHour.setOnValueChangedListener((p, o, n) -> onWheel());
        b.inspectDeadlineMinute.setOnValueChangedListener((p, o, n) -> onWheel());
        ClickThrottle.setOnClickListener(b.inspectDeadlineCancel, v -> dismiss());
        ClickThrottle.setOnClickListener(b.inspectDeadlineSave, v -> vm.saveDeadline());
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InspectionSettingVO s = vm.getSetting().getValue();
        if (s != null) {
            buildChips(s);
            b.inspectDeadlineLayout.setHelperText(getString(R.string.inspect_deadline_helper, s.getDefaultDeadline()));
            String names = InspectFormat.join(s.getOwnerManagerNames(), "所有者和场长");
            b.inspectRuleNames.setText("到点仍有栏舍未巡检时发送给" + names);
        }
        vm.getDraftDeadline().observe(getViewLifecycleOwner(), this::renderDraft);
        vm.getDraftRemind().observe(getViewLifecycleOwner(), r ->
                b.inspectRuleRemind.setText(r + " 提醒负责员工"));
        vm.getDeadlineSaving().observe(getViewLifecycleOwner(), saving ->
                ButtonLoading.setLoading(b.inspectDeadlineSave, Boolean.TRUE.equals(saving)));
        vm.getDeadlineError().observe(getViewLifecycleOwner(), err -> {
            b.inspectDeadlineLayout.setError(err);
            if (err == null) {
                b.inspectDeadlineLayout.setErrorEnabled(false);
            }
        });
        vm.getDismissDeadlinePanel().observe(getViewLifecycleOwner(), v -> dismissAllowingStateLoss());
    }

    private void buildChips(@NonNull InspectionSettingVO s) {
        b.inspectDeadlineChips.removeAllViews();
        for (String t : s.getQuickOptions()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.inspect_item_option_chip,
                    b.inspectDeadlineChips, false);
            chip.setId(View.generateViewId());
            chip.setText(t);
            chip.setTag(t);
            ClickThrottle.setOnClickListener(chip, v -> vm.setDraftDeadline(t));
            b.inspectDeadlineChips.addView(chip);
        }
    }

    private void renderDraft(@Nullable String value) {
        if (value == null) {
            return;
        }
        b.inspectDeadlineInput.setText(value);
        b.inspectRuleDeadline.setText(value + " 提醒所有者和场长");
        for (int i = 0; i < b.inspectDeadlineChips.getChildCount(); i++) {
            Chip c = (Chip) b.inspectDeadlineChips.getChildAt(i);
            c.setChecked(value.equals(c.getTag()));
        }
        int[] t = InspectFormat.parseTime(value);
        if (t != null) {
            syncing = true;
            b.inspectDeadlineHour.setValue(t[0]);
            b.inspectDeadlineMinute.setValue(t[1]);
            syncing = false;
        }
    }

    private void toggleWheel() {
        b.inspectDeadlineWheel.setVisibility(b.inspectDeadlineWheel.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE);
    }

    private void onWheel() {
        if (!syncing) {
            vm.setDraftDeadline(InspectFormat.hhmm(b.inspectDeadlineHour.getValue(), b.inspectDeadlineMinute.getValue()));
        }
    }

    /** 滚轮显示两位数字 */
    private static final class NumberFormatterHolder {
        static void apply(@NonNull android.widget.NumberPicker picker) {
            picker.setFormatter(v -> String.format(java.util.Locale.ROOT, "%02d", v));
        }
    }
}
