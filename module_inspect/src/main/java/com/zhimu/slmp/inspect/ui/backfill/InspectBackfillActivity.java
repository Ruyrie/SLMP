package com.zhimu.slmp.inspect.ui.backfill;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.databinding.InspectActivityFormBinding;
import com.zhimu.slmp.inspect.ui.form.InspectFormActivity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * I03 补录巡检（所有者、场长）：布局与交互见 {@link InspectFormActivity}；补录日期用 MDC 日期选择器，
 * 只开放 backfill_dates 中前 3 天内该栏舍漏巡的日期（{@link AllowedDatesValidator}），其余日期不可选。
 */
public class InspectBackfillActivity extends InspectFormActivity<InspectBackfillViewModel> {

    private static final String TAG_DATE = "inspect_backfill_date";

    @NonNull
    @Override
    protected Class<InspectBackfillViewModel> viewModelClass() {
        return InspectBackfillViewModel.class;
    }

    @Override
    protected void onBindExtra(@NonNull InspectActivityFormBinding b) {
        b.setBackfill(vm);
        ClickThrottle.setOnClickListener(b.inspectDateInput, v -> pickDate());
        b.inspectDateLayout.setEndIconOnClickListener(v -> pickDate());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_DATE);
        if (f instanceof MaterialDatePicker) {
            attach((MaterialDatePicker<?>) f);
        }
    }

    private void pickDate() {
        if (Boolean.TRUE.equals(vm.getDateLocked().getValue())
                || getSupportFragmentManager().findFragmentByTag(TAG_DATE) != null) {
            return;
        }
        List<Long> allowed = new ArrayList<>();
        List<String> dates = vm.getBackfillDates().getValue();
        if (dates != null) {
            for (String d : dates) {
                LocalDate ld = DateFormatter.parseDate(d);
                if (ld != null) {
                    allowed.add(DateTimePicker.toUtcMillis(ld));
                }
            }
        }
        LocalDate selected = DateFormatter.parseDate(vm.getInspectDate().getValue());
        LocalDate today = DateFormatter.today();
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(DateTimePicker.toUtcMillis(today.minusDays(30)))
                .setEnd(DateTimePicker.toUtcMillis(today))
                .setOpenAt(DateTimePicker.toUtcMillis(selected != null ? selected : today))
                .setValidator(new AllowedDatesValidator(allowed))
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.inspect_backfill_date))
                .setSelection(selected != null ? DateTimePicker.toUtcMillis(selected) : null)
                .setCalendarConstraints(constraints)
                .build();
        attach(picker);
        picker.show(getSupportFragmentManager(), TAG_DATE);
    }

    @SuppressWarnings("unchecked")
    private void attach(@NonNull MaterialDatePicker<?> picker) {
        ((MaterialDatePicker<Long>) picker).addOnPositiveButtonClickListener(millis -> {
            if (millis != null) {
                LocalDate d = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
                vm.pickDate(DateFormatter.toDateString(d));
            }
        });
    }
}
