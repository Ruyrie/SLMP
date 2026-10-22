package com.zhimu.slmp.breed.ui.incubation;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.di.BreedInjector;

import java.time.LocalDate;

/**
 * B07 步骤 Fragment 的基类：取 Activity 范围的 IncubationViewModel，提供日期选择与输入监听。
 */
public abstract class IncubationStepFragment extends BaseFragment {

    protected IncubationViewModel vm;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainActivityViewModel(BreedInjector.factory(), IncubationViewModel.class);
    }

    /** 日期框：点击打开选择器（上限今天，下限按字段），结果写回 ViewModel */
    protected void bindDate(@NonNull TextInputLayout layout, @NonNull EditText input, @NonNull String field) {
        String key = "breed_inc_" + field;
        View.OnClickListener open = v -> {
            LocalDate selected = DateFormatter.parseDate(currentDate(field));
            DateTimePicker.date(key)
                    .selection(selected != null ? selected : DateFormatter.today())
                    .bounds(vm.minDate(field), DateFormatter.today())
                    .show(getChildFragmentManager());
        };
        ClickThrottle.setOnClickListener(input, open);
        layout.setEndIconOnClickListener(open);
        getChildFragmentManager().setFragmentResultListener(key, this,
                (k, result) -> vm.onDatePicked(field, DateTimePicker.getDate(result)));
    }

    @Nullable
    private String currentDate(@NonNull String field) {
        switch (field) {
            case "set_date":
                return vm.getSetDate().getValue();
            case "candle_date":
                return vm.getCandleDate().getValue();
            default:
                return vm.getHatchDate().getValue();
        }
    }

    /** 用户输入后清除该字段错误 */
    protected void watch(@NonNull EditText input, @NonNull String field) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (input.hasFocus()) {
                    vm.onFieldEdited(field);
                }
            }
        });
    }
}
