package com.zhimu.slmp.breed.ui.incubation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.databinding.BreedFragmentIncCandleBinding;
import com.zhimu.slmp.breed.util.BreedFormRules;

/**
 * B07 第 2 步“照蛋登记”：照蛋日期、受精蛋数（不能多于入孵蛋数），受精率在进度卡中实时显示。
 */
public class CandleStepFragment extends IncubationStepFragment {

    private BreedFragmentIncCandleBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BreedFragmentIncCandleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        bindDate(binding.breedCandleDateLayout, binding.breedCandleDateInput, BreedFormRules.F_CANDLE_DATE);
        watch(binding.breedFertileInput, BreedFormRules.F_FERTILE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
