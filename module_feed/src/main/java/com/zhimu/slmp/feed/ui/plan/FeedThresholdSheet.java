package com.zhimu.slmp.feed.ui.plan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedSheetInputBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.util.FeedCalc;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.math.BigDecimal;

/**
 * G03“投喂异常判定”面板：偏差比例（%，默认 10），1–100；保存只写本页（随“保存方案”提交）。取 Activity 范围的 FeedPlanViewModel。
 */
public class FeedThresholdSheet extends BaseBottomSheetDialogFragment {

    private FeedSheetInputBinding binding;
    private FeedPlanViewModel vm;

    @NonNull
    public static FeedThresholdSheet newInstance(@NonNull CharSequence title) {
        FeedThresholdSheet s = new FeedThresholdSheet();
        s.setArguments(baseArgs(null, title, null, null));
        return s;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), FeedInjector.factory()).get(FeedPlanViewModel.class);
        binding = FeedSheetInputBinding.inflate(inflater, container, false);
        binding.feedSheetLayout.setHint(getString(R.string.feed_plan_threshold_field));
        binding.feedSheetLayout.setSuffixText(getString(R.string.feed_unit_percent));
        binding.feedSheetLayout.setHelperText(getString(R.string.feed_plan_threshold_helper));
        binding.feedSheetTextAction.setVisibility(View.GONE);
        if (savedInstanceState == null) {
            binding.feedSheetInput.setText(FeedFormat.thresholdPercent(vm.getAbnormalThreshold().getValue()));
        }
        return binding.getRoot();
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.feed_action_save);
    }

    @Override
    protected void onPrimaryButtonClick() {
        BigDecimal percent = FeedCalc.parse(binding.feedSheetInput.getText());
        String error = FeedPlanViewModel.checkPercent(percent);
        if (error != null) {
            binding.feedSheetLayout.setError(error);
            return;
        }
        vm.setThreshold(percent);
        dismiss();
    }
}
