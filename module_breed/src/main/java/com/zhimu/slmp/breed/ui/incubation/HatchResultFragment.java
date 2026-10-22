package com.zhimu.slmp.breed.ui.incubation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.breed.databinding.BreedFragmentIncResultBinding;
import com.zhimu.slmp.breed.model.vo.IncubationDetailVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * B07 出雏登记完成后的只读结果卡：批次号可点击进入 L06；底部操作栏隐藏。
 */
public class HatchResultFragment extends IncubationStepFragment {

    private BreedFragmentIncResultBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BreedFragmentIncResultBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        ClickThrottle.setOnClickListener(binding.breedResultBatch.getRoot(), v -> {
            IncubationDetailVO d = vm.getDetail().getValue();
            if (d != null && d.getBatchId() != null) {
                Bundle b = new Bundle();
                b.putLong(RouterPath.EXTRA_BATCH_ID, d.getBatchId());
                Router.open(requireContext(), RouterPath.L06, b);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
