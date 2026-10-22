package com.zhimu.slmp.breed.ui.incubation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedFragmentIncSetBinding;
import com.zhimu.slmp.breed.model.vo.FlockOptionVO;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.OptionAdapter;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * B07 第 1 步“入孵登记”：种禽群下拉列出本场用途为种用的在栏禽类批次（“1 号种鸡舍 · ZM2603-015”，
 * 说明“{品种} · 在栏 {n} 只”），为空时显示“没有可以入孵的种禽群……”；推算卡按该批次品种的孵化期刷新。
 */
public class SetStepFragment extends IncubationStepFragment {

    private BreedFragmentIncSetBinding binding;
    private OptionAdapter flockAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BreedFragmentIncSetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        BreedUi.applyTone(binding.breedHatchIconBox, BreedUi.TONE_SECONDARY, binding.breedHatchIcon);
        bindDate(binding.breedSetDateLayout, binding.breedSetDateInput, BreedFormRules.F_SET_DATE);
        watch(binding.breedSetCountInput, BreedFormRules.F_SET_COUNT);
        flockAdapter = OptionAdapter.bind(binding.breedFlockInput, vm::onFlockPicked);
        flockAdapter.setEmptyText(getString(R.string.breed_flock_empty));
        vm.getFlockOptions().observe(getViewLifecycleOwner(), this::renderFlocks);
        vm.getSelectedFlockId().observe(getViewLifecycleOwner(),
                id -> OptionAdapter.showSelected(binding.breedFlockInput, flockAdapter, id));
    }

    private void renderFlocks(@Nullable List<FlockOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (FlockOptionVO f : list) {
                items.add(new OptionAdapter.Item(f.getBatchId(), f.flockText(), BreedTextFormatter.nz(f.getBreedName())
                        + BreedTextFormatter.DOT + "在栏 " + BreedTextFormatter.qty(f.getCurrentCount(),
                        BreedTextFormatter.UNIT_BIRD)));
            }
        }
        flockAdapter.setItems(items);
        OptionAdapter.showSelected(binding.breedFlockInput, flockAdapter, vm.getSelectedFlockId().getValue());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
