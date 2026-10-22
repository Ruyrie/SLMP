package com.zhimu.slmp.breed.ui.archive;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedSheetExitBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.BreedCodes;

import java.time.LocalDate;

/**
 * “登记死亡 / 淘汰”底部面板（B06 更多菜单）：复用 AnimalArchiveViewModel；说明“0161 · 长大二元 · 第 2 胎”；
 * 确认后返回 B01 并提示“已登记淘汰，0161 不再出现在配种选择中”。
 */
public class AnimalExitSheet extends BaseBottomSheetDialogFragment {

    private static final String REQ_DATE = "breed_exit_date";

    private AnimalArchiveViewModel vm;

    public static void open(@NonNull FragmentManager fm, @Nullable CharSequence description) {
        AnimalExitSheet s = new AnimalExitSheet();
        s.setArguments(baseArgs(null, null, description, null));
        s.showSafely(fm, AnimalExitSheet.class.getName());
    }

    public static void dismissIn(@NonNull FragmentManager fm) {
        AnimalExitSheet s = (AnimalExitSheet) fm.findFragmentByTag(AnimalExitSheet.class.getName());
        if (s != null) {
            s.dismissAllowingStateLoss();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(requireActivity(), BreedInjector.factory()).get(AnimalArchiveViewModel.class);
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.breed_exit_title);
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.breed_exit_confirm);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        BreedSheetExitBinding b = BreedSheetExitBinding.inflate(inflater, container, false);
        b.setLifecycleOwner(this);
        b.setVm(vm);
        b.breedExitType.check(BreedCodes.EXIT_DEATH.equals(vm.getExitType().getValue())
                ? R.id.breed_exit_death : R.id.breed_exit_cull);
        b.breedExitType.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                vm.onExitTypePicked(id == R.id.breed_exit_death ? BreedCodes.EXIT_DEATH : BreedCodes.EXIT_CULL);
            }
        });
        ClickThrottle.setOnClickListener(b.breedExitDateInput, v -> pickDate());
        b.breedExitDateLayout.setEndIconOnClickListener(v -> pickDate());
        getChildFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onExitDatePicked(DateTimePicker.getDate(result)));
        b.breedExitReasonInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.onReasonEdited();
            }
        });
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm.getSubmitting().observe(getViewLifecycleOwner(), s -> setPrimaryLoading(Boolean.TRUE.equals(s)));
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm.submitExit();
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getExitDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(vm.minExitDate(), DateFormatter.today())
                .show(getChildFragmentManager());
    }
}
