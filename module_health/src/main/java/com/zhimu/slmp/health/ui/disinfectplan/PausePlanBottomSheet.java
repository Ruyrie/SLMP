package com.zhimu.slmp.health.ui.disinfectplan;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthSheetPausePlanBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.DisinfectPlanVO;

import java.util.Objects;

/**
 * 暂停计划底部面板：标题“暂停消毒计划”，副标题为覆盖的栏舍；“暂停原因”（选填，最多 30 字，“会显示在计划卡片上”）；
 * 说明“暂停期间不生成新的消毒任务，已生成的任务不变。”；主按钮“暂停计划”。字段与提交状态在页面的 ViewModel 中。
 */
public class PausePlanBottomSheet extends BaseBottomSheetDialogFragment {

    private DisinfectPlanListViewModel vm;

    @NonNull
    public static PausePlanBottomSheet newInstance(@NonNull DisinfectPlanVO plan) {
        PausePlanBottomSheet f = new PausePlanBottomSheet();
        f.setArguments(baseArgs(null, "暂停消毒计划", plan.getPenNamesText(), null));
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(requireActivity(), HealthInjector.factory()).get(DisinfectPlanListViewModel.class);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        HealthSheetPausePlanBinding b = HealthSheetPausePlanBinding.inflate(inflater, container, false);
        if (savedInstanceState == null) {
            b.healthPauseReason.setText(vm.getPauseReason().getValue());
        }
        b.healthPauseReason.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!Objects.equals(s.toString(), vm.getPauseReason().getValue())) {
                    vm.getPauseReason().setValue(s.toString());
                }
            }
        });
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm.getPausing().observe(getViewLifecycleOwner(), p -> setPrimaryLoading(Boolean.TRUE.equals(p)));
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.health_pause_plan);
    }

    @Override
    protected int getPrimaryButtonIcon() {
        return com.zhimu.slmp.base.R.drawable.ic_pause;
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm.pause();
    }
}
