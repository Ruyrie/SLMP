package com.zhimu.slmp.today.ui.setting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.today.databinding.TodaySheetTimePickerBinding;
import com.zhimu.slmp.today.di.TodayInjector;

import java.util.Locale;

/**
 * 时间选择面板（第 4 级）：标题为所点的行名；“时”“分”两列（24 小时制，分钟步长 5），当前值居中；
 * 免打扰开始与结束时间相同时提示“开始时间和结束时间不能相同”并禁用“确定”。当前值与另一端的值从 ReminderSettingViewModel 读取。
 */
public class TimePickerSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_FIELD = "today_time_field";
    private static final int MINUTE_STEP = 5;

    private ReminderSettingViewModel vm;
    @Nullable
    private TodaySheetTimePickerBinding binding;

    public static void show(@NonNull FragmentManager fm, @NonNull String field, @NonNull CharSequence title) {
        Bundle args = baseArgs(null, title, null, null);
        args.putString(ARG_FIELD, field);
        TimePickerSheet sheet = new TimePickerSheet();
        sheet.setArguments(args);
        sheet.showSafely(fm);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), TodayInjector.factory()).get(ReminderSettingViewModel.class);
        super.onCreate(savedInstanceState);
    }

    @NonNull
    private String field() {
        String f = requireArguments().getString(ARG_FIELD);
        return f == null ? "" : f;
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        TodaySheetTimePickerBinding b = TodaySheetTimePickerBinding.inflate(inflater, container, false);
        binding = b;
        b.hour.setMinValue(0);
        b.hour.setMaxValue(23);
        b.hour.setFormatter(v -> String.format(Locale.ROOT, "%02d", v));
        String[] minutes = new String[60 / MINUTE_STEP];
        for (int i = 0; i < minutes.length; i++) {
            minutes[i] = String.format(Locale.ROOT, "%02d", i * MINUTE_STEP);
        }
        b.minute.setMinValue(0);
        b.minute.setMaxValue(minutes.length - 1);
        b.minute.setDisplayedValues(minutes);

        String current = vm.timeValue(field());
        int h = parse(current, 0);
        int m = parse(current, 3) / MINUTE_STEP;
        if (savedInstanceState == null) {
            b.hour.setValue(Math.max(0, Math.min(23, h)));
            b.minute.setValue(Math.max(0, Math.min(minutes.length - 1, m)));
        }
        b.hour.setOnValueChangedListener((p, o, n) -> validate());
        b.minute.setOnValueChangedListener((p, o, n) -> validate());
        ClickThrottle.setOnClickListener(b.cancel, v -> dismiss());
        ClickThrottle.setOnClickListener(b.ok, v -> {
            vm.onTimePicked(field(), selected());
            dismiss();
        });
        validate();
        return b.getRoot();
    }

    private static int parse(@Nullable String hhmm, int offset) {
        if (hhmm == null || hhmm.length() < offset + 2) {
            return 0;
        }
        try {
            return Integer.parseInt(hhmm.substring(offset, offset + 2));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @NonNull
    private String selected() {
        TodaySheetTimePickerBinding b = binding;
        if (b == null) {
            return "00:00";
        }
        return String.format(Locale.ROOT, "%02d:%02d", b.hour.getValue(), b.minute.getValue() * MINUTE_STEP);
    }

    private void validate() {
        TodaySheetTimePickerBinding b = binding;
        if (b == null) {
            return;
        }
        String other = vm.otherEnd(field());
        boolean same = other != null && other.equals(selected());
        b.error.setVisibility(same ? View.VISIBLE : View.GONE);
        b.ok.setEnabled(!same);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
