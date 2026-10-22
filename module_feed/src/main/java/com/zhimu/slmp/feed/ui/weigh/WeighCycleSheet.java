package com.zhimu.slmp.feed.ui.weigh;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedSheetInputBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.model.vo.WeighCycleVO;

/**
 * G05“称重周期”面板（所有者、场长）：称重周期（次 / 月，1–8，默认品种建议值）、“恢复品种建议值”、底部“保存”（API-G23）。
 * 取 Activity 范围的 WeighRegisterViewModel，保存成功后关闭。
 */
public class WeighCycleSheet extends BaseBottomSheetDialogFragment {

    private FeedSheetInputBinding binding;
    private WeighRegisterViewModel vm;

    @NonNull
    public static WeighCycleSheet newInstance(@NonNull CharSequence title) {
        WeighCycleSheet s = new WeighCycleSheet();
        s.setArguments(baseArgs(null, title, null, null));
        return s;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), FeedInjector.factory()).get(WeighRegisterViewModel.class);
        binding = FeedSheetInputBinding.inflate(inflater, container, false);
        binding.feedSheetLayout.setHint(getString(R.string.feed_weigh_cycle));
        binding.feedSheetLayout.setSuffixText(getString(R.string.feed_unit_per_month));
        binding.feedSheetLayout.setHelperText(getString(R.string.feed_weigh_cycle_helper));
        binding.feedSheetInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        binding.feedSheetInput.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789"));
        binding.feedSheetTextAction.setText(R.string.feed_weigh_cycle_reset);
        ClickThrottle.setOnClickListener(binding.feedSheetTextAction, v -> vm.resetCycle());
        if (savedInstanceState == null) {
            WeighCycleVO c = vm.getCycle().getValue();
            Integer value = c == null ? null : c.getPerMonth() != null ? c.getPerMonth() : c.getSuggestedPerMonth();
            binding.feedSheetInput.setText(value == null ? "" : String.valueOf(value));
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm.getCycleSaving().observe(getViewLifecycleOwner(), s -> setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCycleSaved().observe(getViewLifecycleOwner(), v -> dismissAllowingStateLoss());
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.feed_action_save);
    }

    @Override
    protected void onPrimaryButtonClick() {
        String text = binding.feedSheetInput.getText() == null ? "" : binding.feedSheetInput.getText().toString().trim();
        Integer value = null;
        try {
            value = text.isEmpty() ? null : Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            // 非数字按未填处理
        }
        String error = WeighRegisterViewModel.checkCycle(value);
        if (error != null) {
            binding.feedSheetLayout.setError(error);
            return;
        }
        binding.feedSheetLayout.setError(null);
        vm.saveCycle(value);
    }
}
