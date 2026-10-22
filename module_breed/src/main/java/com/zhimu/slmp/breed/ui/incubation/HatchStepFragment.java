package com.zhimu.slmp.breed.ui.incubation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.breed.databinding.BreedFragmentIncHatchBinding;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.OptionAdapter;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * B07 第 3 步“出雏登记”：孵化率、弱雏数、转批次只数随输入实时更新；入栏鸡舍下拉列出“在栏 n 只 · 容量 n 只”，
 * 入栏后超容量时显示提示与“我已确认，仍要入栏”。
 */
public class HatchStepFragment extends IncubationStepFragment {

    private BreedFragmentIncHatchBinding binding;
    private OptionAdapter penAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BreedFragmentIncHatchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        BreedUi.applyTone(binding.breedTransferIconBox, BreedUi.TONE_SECONDARY, binding.breedTransferIcon);
        bindDate(binding.breedHatchDateLayout, binding.breedHatchDateInput, BreedFormRules.F_HATCH_DATE);
        watch(binding.breedHatchedInput, BreedFormRules.F_HATCHED);
        watch(binding.breedHealthyInput, BreedFormRules.F_HEALTHY);
        binding.breedTransferSwitch.setOnCheckedChangeListener((b, checked) -> vm.onTransferChanged(checked));
        penAdapter = OptionAdapter.bind(binding.breedPenInput, vm::onPenPicked);
        vm.getPenOptions().observe(getViewLifecycleOwner(), this::renderPens);
        vm.getTargetPenId().observe(getViewLifecycleOwner(),
                id -> OptionAdapter.showSelected(binding.breedPenInput, penAdapter, id));
        vm.getCapacityText().observe(getViewLifecycleOwner(), text -> {
            binding.breedCapacityNotice.setVisibility(text == null ? View.GONE : View.VISIBLE);
            if (text != null) {
                binding.breedCapacityNotice.setNotice(null, text);
            }
        });
        binding.breedCapacityNotice.setOnCheckedChangeListener((b, checked) -> vm.onCapacityChecked(checked));
        vm.getCapacityConfirmed().observe(getViewLifecycleOwner(), c -> {
            boolean v = Boolean.TRUE.equals(c);
            if (binding.breedCapacityNotice.isChecked() != v) {
                binding.breedCapacityNotice.setChecked(v);
            }
        });
    }

    private void renderPens(@Nullable List<PenOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (PenOptionVO p : list) {
                String hint = p.getHint() != null ? p.getHint() : "在栏 " + BreedTextFormatter.qty(p.getStockCount(),
                        BreedTextFormatter.UNIT_BIRD) + BreedTextFormatter.DOT + "容量 "
                        + BreedTextFormatter.qty(p.getCapacity(), BreedTextFormatter.UNIT_BIRD);
                items.add(new OptionAdapter.Item(p.getPenId(), BreedTextFormatter.nz(p.getPenName()), hint, null,
                        StatusTagView.NEUTRAL, p.isDisabled()));
            }
        }
        penAdapter.setItems(items);
        OptionAdapter.showSelected(binding.breedPenInput, penAdapter, vm.getTargetPenId().getValue());
    }

    /** 校验失败时滚动的目标 */
    @Nullable
    View viewFor(@Nullable String field) {
        if (binding == null || field == null) {
            return null;
        }
        if (BreedFormRules.F_CAPACITY.equals(field)) {
            return binding.breedCapacityNotice;
        }
        if (BreedFormRules.F_TARGET_PEN.equals(field) || BreedFormRules.F_TRANSFER.equals(field)) {
            return binding.breedTransferCard;
        }
        if (BreedFormRules.F_HEALTHY.equals(field)) {
            return binding.breedHealthyLayout;
        }
        if (BreedFormRules.F_HATCHED.equals(field)) {
            return binding.breedHatchedLayout;
        }
        return binding.breedHatchDateLayout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
