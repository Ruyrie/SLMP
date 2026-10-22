package com.zhimu.slmp.breed.ui.incubation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.databinding.BreedFragmentIncEditBinding;
import com.zhimu.slmp.breed.util.BreedFormRules;

/**
 * B07 更多菜单“修改入孵 / 照蛋记录”（API-B29）：入孵日期、入孵蛋数，已照蛋时另含照蛋日期、受精蛋数；底部“保存修改”。
 */
public class EditStepFragment extends IncubationStepFragment {

    private BreedFragmentIncEditBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BreedFragmentIncEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        bindDate(binding.breedSetDateLayout, binding.breedSetDateInput, BreedFormRules.F_SET_DATE);
        bindDate(binding.breedCandleDateLayout, binding.breedCandleDateInput, BreedFormRules.F_CANDLE_DATE);
        watch(binding.breedSetCountInput, BreedFormRules.F_SET_COUNT);
        watch(binding.breedFertileInput, BreedFormRules.F_FERTILE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
