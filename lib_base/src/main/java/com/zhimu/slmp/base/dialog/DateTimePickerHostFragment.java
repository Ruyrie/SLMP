package com.zhimu.slmp.base.dialog;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.zhimu.slmp.base.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link DateTimePicker} 的无界面宿主：选择器作为它的子 Fragment 显示。宿主由 FragmentManager 自动恢复，
 * 恢复后在 {@link #onCreate} 中重新挂上选择器的监听，确定后经 FragmentResult 回传给所在 FragmentManager 并移除自身。
 * 只供 DateTimePicker 使用（需要 public 无参构造方法以便系统恢复）。
 */
public class DateTimePickerHostFragment extends Fragment {

    static final String ARG_MODE = "base_picker_mode";
    static final String ARG_REQUEST_KEY = "base_picker_request_key";
    static final String ARG_TITLE = "base_picker_title";
    static final String ARG_SELECTION = "base_picker_selection";
    static final String ARG_START = "base_picker_start";
    static final String ARG_END = "base_picker_end";
    static final String ARG_HOUR = "base_picker_hour";
    static final String ARG_MINUTE = "base_picker_minute";
    static final String ARG_EXTRAS = "base_picker_extras";

    static final int MODE_DATE = 0;
    static final int MODE_TIME = 1;

    private static final String PICKER_TAG = "base_picker";
    private boolean finished;

    static boolean show(@NonNull FragmentManager fm, @NonNull Bundle args) {
        String tag = "base_picker_host_" + args.getString(ARG_REQUEST_KEY);
        if (!DialogGuard.canShowIgnoringOthers(fm, tag)) {
            return false;
        }
        DateTimePickerHostFragment host = new DateTimePickerHostFragment();
        host.setArguments(args);
        fm.beginTransaction().add(host, tag).commitNow();
        return true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = requireArguments();
        FragmentManager child = getChildFragmentManager();
        Fragment existing = child.findFragmentByTag(PICKER_TAG);
        if (args.getInt(ARG_MODE) == MODE_TIME) {
            MaterialTimePicker picker = existing instanceof MaterialTimePicker
                    ? (MaterialTimePicker) existing : null;
            if (picker == null) {
                if (savedInstanceState != null) {
                    finish();
                    return;
                }
                CharSequence title = args.getCharSequence(ARG_TITLE);
                picker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_24H)
                        .setHour(args.getInt(ARG_HOUR, 8))
                        .setMinute(args.getInt(ARG_MINUTE, 0))
                        .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                        .setTitleText(title != null ? title : getString(R.string.base_picker_time_title))
                        .build();
                picker.show(child, PICKER_TAG);
            }
            attachTime(picker);
        } else {
            @SuppressWarnings("unchecked")
            MaterialDatePicker<Long> picker = existing instanceof MaterialDatePicker
                    ? (MaterialDatePicker<Long>) existing : null;
            if (picker == null) {
                if (savedInstanceState != null) {
                    finish();
                    return;
                }
                CharSequence title = args.getCharSequence(ARG_TITLE);
                MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                        .setTitleText(title != null ? title : getString(R.string.base_picker_date_title))
                        .setSelection(args.containsKey(ARG_SELECTION) ? args.getLong(ARG_SELECTION)
                                : MaterialDatePicker.todayInUtcMilliseconds());
                CalendarConstraints constraints = buildConstraints(args);
                if (constraints != null) {
                    builder.setCalendarConstraints(constraints);
                }
                picker = builder.build();
                picker.show(child, PICKER_TAG);
            }
            attachDate(picker);
        }
    }

    @Nullable
    private static CalendarConstraints buildConstraints(@NonNull Bundle args) {
        boolean hasStart = args.containsKey(ARG_START);
        boolean hasEnd = args.containsKey(ARG_END);
        if (!hasStart && !hasEnd) {
            return null;
        }
        CalendarConstraints.Builder builder = new CalendarConstraints.Builder();
        List<CalendarConstraints.DateValidator> validators = new ArrayList<>();
        if (hasStart) {
            long start = args.getLong(ARG_START);
            builder.setStart(start);
            validators.add(DateValidatorPointForward.from(start));
        }
        if (hasEnd) {
            long end = args.getLong(ARG_END);
            builder.setEnd(end);
            validators.add(DateValidatorPointBackward.before(end + 1));
        }
        builder.setValidator(CompositeDateValidator.allOf(validators));
        if (args.containsKey(ARG_SELECTION)) {
            builder.setOpenAt(args.getLong(ARG_SELECTION));
        }
        return builder.build();
    }

    private void attachDate(@NonNull MaterialDatePicker<Long> picker) {
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) {
                return;
            }
            Bundle result = new Bundle();
            result.putLong(DateTimePicker.RESULT_DATE_MILLIS, selection);
            result.putString(DateTimePicker.RESULT_DATE, DateTimePicker.fromUtcMillis(selection).toString());
            deliver(result);
        });
        picker.addOnDismissListener(dialog -> onPickerDismissed());
    }

    private void attachTime(@NonNull MaterialTimePicker picker) {
        picker.addOnPositiveButtonClickListener(v -> {
            Bundle result = new Bundle();
            result.putInt(DateTimePicker.RESULT_HOUR, picker.getHour());
            result.putInt(DateTimePicker.RESULT_MINUTE, picker.getMinute());
            result.putString(DateTimePicker.RESULT_TIME,
                    String.format(Locale.ROOT, "%02d:%02d", picker.getHour(), picker.getMinute()));
            deliver(result);
        });
        picker.addOnDismissListener(dialog -> onPickerDismissed());
    }

    private void deliver(@NonNull Bundle result) {
        if (finished || !isAdded()) {
            return;
        }
        Bundle extras = requireArguments().getBundle(ARG_EXTRAS);
        if (extras != null) {
            result.putBundle(DateTimePicker.RESULT_EXTRAS, extras);
        }
        String key = requireArguments().getString(ARG_REQUEST_KEY);
        if (key != null) {
            getParentFragmentManager().setFragmentResult(key, result);
        }
        finish();
    }

    private void onPickerDismissed() {
        // 旋转等配置变更时选择器也会收到 dismiss，此时保留宿主以便恢复
        FragmentActivity activity = getActivity();
        if (activity != null && activity.isChangingConfigurations()) {
            return;
        }
        if (isStateSaved()) {
            return;
        }
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (isAdded() && !getParentFragmentManager().isStateSaved()) {
            getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
}
