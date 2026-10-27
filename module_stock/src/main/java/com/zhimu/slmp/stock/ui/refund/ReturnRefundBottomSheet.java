package com.zhimu.slmp.stock.ui.refund;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockSheetRefundBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import java.util.Map;

public class ReturnRefundBottomSheet extends BaseBottomSheetDialogFragment {
    private static final String TAG = "stock_return_refund";
    private static final String DATE = "stock_refund_date";
    private StockSheetRefundBinding binding;
    private ReturnRefundViewModel vm;

    public static void show(FragmentManager manager) {
        if (manager.findFragmentByTag(TAG) == null) new ReturnRefundBottomSheet().showSafely(manager, TAG);
    }
    public static void close(FragmentManager manager) {
        Fragment fragment = manager.findFragmentByTag(TAG);
        if (fragment instanceof ReturnRefundBottomSheet) ((ReturnRefundBottomSheet) fragment).dismissAllowingStateLoss();
    }
    private ReturnRefundViewModel model() {
        if (vm == null) vm = new ViewModelProvider(requireActivity(), StockInjector.factory()).get(ReturnRefundViewModel.class);
        return vm;
    }
    @Override protected CharSequence getSheetTitle() {
        return getString(model().isEditing() ? R.string.stock_refund_edit : R.string.stock_refund_register);
    }
    @Override protected CharSequence getPrimaryButtonText() {
        return getString(model().isEditing() ? R.string.stock_refund_save : R.string.stock_refund_confirm);
    }
    @Override protected void onPrimaryButtonClick() { model().submit(); }

    @Override protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle state) {
        binding = StockSheetRefundBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(model());
        binding.stockRefundSummary.setText(vm.summaryText());
        binding.stockRefundConfirmNotice.setText(vm.isEditing() ? R.string.stock_refund_edit_notice : R.string.stock_refund_confirm_notice);
        vm.refundDate.observe(getViewLifecycleOwner(), value -> binding.stockRefundDate.setText(DateFormatter.formatDate(value)));
        View.OnClickListener selectDate = unused -> {
            StockOutboundVO record = vm.getRecord().getValue();
            DateTimePicker.date(DATE).selection(DateFormatter.parseDate(vm.refundDate.getValue()))
                    .bounds(record == null ? null : DateFormatter.parseDate(record.outDate), DateFormatter.today())
                    .show(getChildFragmentManager());
        };
        binding.stockRefundDate.setOnClickListener(selectDate);
        binding.stockRefundDateLayout.setEndIconOnClickListener(selectDate);
        getChildFragmentManager().setFragmentResultListener(DATE, getViewLifecycleOwner(),
                (key, result) -> vm.refundDate.setValue(DateTimePicker.getDate(result)));
        vm.getErrors().observe(getViewLifecycleOwner(), this::showErrors);
        vm.getSubmitting().observe(getViewLifecycleOwner(), value -> {
            boolean busy = Boolean.TRUE.equals(value);
            setPrimaryLoading(busy);
            binding.stockRefundAmount.setEnabled(!busy);
            binding.stockRefundDate.setEnabled(!busy);
            binding.stockRefundDateLayout.setEnabled(!busy);
            binding.stockRefundRemark.setEnabled(!busy);
        });
    }
    private void showErrors(Map<String, String> errors) {
        binding.stockRefundAmountLayout.setError(errors.get("amount"));
        binding.stockRefundDateLayout.setError(errors.get("refund_date"));
        binding.stockRefundRemarkLayout.setError(errors.get("remark"));
        StringBuilder message = new StringBuilder();
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            if (!"amount".equals(entry.getKey()) && !"refund_date".equals(entry.getKey()) && !"remark".equals(entry.getKey())) {
                if (message.length() > 0) message.append('\n');
                message.append(entry.getValue());
            }
        }
        binding.stockRefundError.setText(message);
        binding.stockRefundError.setVisibility(message.length() > 0 ? View.VISIBLE : View.GONE);
    }
    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
