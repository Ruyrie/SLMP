package com.zhimu.slmp.finance.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceSheetReceiptBinding;
import com.zhimu.slmp.finance.util.ReceiptFormDelegate;

import java.time.LocalDate;
import java.util.Map;

/**
 * 登记收款底部面板（E07、E09 共用，图 10-11）：表单在宿主 ViewModel 的 {@link ReceiptFormDelegate} 中，
 * 面板只负责显示与回调“确认收款”；成功或 40903 后宿主关闭面板。
 */
public class ReceiptBottomSheet extends BaseBottomSheetDialogFragment {

    /** 宿主 Activity 提供表单与提交 */
    public interface Provider {
        @NonNull
        ReceiptHost receiptHost();
    }

    private static final String TAG = "finance_receipt";
    private static final String REQ_DATE = "finance_receipt_date";

    private FinanceSheetReceiptBinding binding;

    public static void show(@NonNull FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) != null) {
            return;
        }
        ReceiptBottomSheet s = new ReceiptBottomSheet();
        s.setArguments(baseArgs(null, null, null, null));
        s.showSafely(fm, TAG);
    }

    public static void dismiss(@NonNull FragmentManager fm) {
        androidx.fragment.app.Fragment f = fm.findFragmentByTag(TAG);
        if (f instanceof ReceiptBottomSheet) {
            ((ReceiptBottomSheet) f).dismissAllowingStateLoss();
        }
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.finance_receipt_title);
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @NonNull
    private ReceiptHost host() {
        return ((Provider) requireActivity()).receiptHost();
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = FinanceSheetReceiptBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ReceiptFormDelegate form = host().getReceipt();
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setForm(form);
        binding.financeReceiptSummary.setText(form.summaryText());
        binding.financeReceiptAmountLayout.setHelperText(form.helperText());
        form.receiptDate.observe(getViewLifecycleOwner(), d ->
                binding.financeReceiptDate.setText(DateFormatter.formatDate(d)));
        View.OnClickListener pickDate = v -> {
            ReceiptFormDelegate.Target t = form.getTarget().getValue();
            LocalDate min = t == null ? null : DateFormatter.parseDate(t.saleDate);
            DateTimePicker.date(REQ_DATE).selection(DateFormatter.parseDate(form.receiptDate.getValue()))
                    .bounds(min, DateFormatter.today()).show(getChildFragmentManager());
        };
        binding.financeReceiptDate.setOnClickListener(pickDate);
        binding.financeReceiptDateLayout.setEndIconOnClickListener(pickDate);
        getChildFragmentManager().setFragmentResultListener(REQ_DATE, getViewLifecycleOwner(),
                (k, r) -> form.receiptDate.setValue(DateTimePicker.getDate(r)));
        buildMethods(form);
        form.getPaymentMethods().observe(getViewLifecycleOwner(), l -> buildMethods(form));
        form.getErrors().observe(getViewLifecycleOwner(), this::renderErrors);
        form.getSubmitting().observe(getViewLifecycleOwner(), s -> {
            boolean loading = Boolean.TRUE.equals(s);
            com.zhimu.slmp.base.widget.ButtonLoading.setLoading(binding.financeReceiptConfirm, loading);
            binding.financeReceiptCancel.setEnabled(!loading);
        });
        form.getShowSheet().observe(getViewLifecycleOwner(), show -> {
            if (!Boolean.TRUE.equals(show)) {
                dismissAllowingStateLoss();
            }
        });
        ClickThrottle.setOnClickListener(binding.financeReceiptConfirm, v -> host().confirmReceipt());
        binding.financeReceiptCancel.setOnClickListener(v -> dismiss());
        binding.financeReceiptAmount.requestFocus();
    }

    private void buildMethods(@NonNull ReceiptFormDelegate form) {
        binding.financeReceiptMethods.removeAllViews();
        for (Map.Entry<String, String> e : form.methodOptions().entrySet()) {
            Chip c = FinanceViews.filterChip(requireContext(), binding.financeReceiptMethods, e.getValue(),
                    e.getKey());
            c.setChecked(e.getKey().equals(form.paymentMethod.getValue()));
            c.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    form.paymentMethod.setValue(e.getKey());
                }
            });
        }
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        String amount = errors == null ? null : errors.get(ReceiptFormDelegate.F_AMOUNT);
        binding.financeReceiptAmountLayout.setError(amount);
        if (amount == null) {
            binding.financeReceiptAmountLayout.setErrorEnabled(false);
            binding.financeReceiptAmountLayout.setHelperText(host().getReceipt().helperText());
        }
        FinanceViews.setError(binding.financeReceiptDateLayout, errors, ReceiptFormDelegate.F_DATE);
    }
}
