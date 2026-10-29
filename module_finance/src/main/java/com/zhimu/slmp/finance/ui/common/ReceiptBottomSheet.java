package com.zhimu.slmp.finance.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
    /** 代码同步勾选状态时为 true，避免把同步当成用户点选 */
    private boolean syncingMethod;

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
        // 上次收款方式（E15）是异步写入的：勾选状态始终跟随表单值，保证看到的就是提交的
        form.paymentMethod.observe(getViewLifecycleOwner(), this::syncMethodChecked);
        form.getErrors().observe(getViewLifecycleOwner(), this::renderErrors);
        form.getSubmitting().observe(getViewLifecycleOwner(), s -> {
            boolean loading = Boolean.TRUE.equals(s);
            com.zhimu.slmp.base.widget.ButtonLoading.setLoading(binding.financeReceiptConfirm, loading);
            binding.financeReceiptCancel.setEnabled(!loading);
        });
        ClickThrottle.setOnClickListener(binding.financeReceiptConfirm, v -> host().confirmReceipt());
        binding.financeReceiptCancel.setOnClickListener(v -> dismiss());
        binding.financeReceiptAmount.requestFocus();
    }

    private void buildMethods(@NonNull ReceiptFormDelegate form) {
        syncingMethod = true;
        binding.financeReceiptMethods.removeAllViews();
        for (Map.Entry<String, String> e : form.methodOptions().entrySet()) {
            Chip c = FinanceViews.filterChip(requireContext(), binding.financeReceiptMethods, e.getValue(),
                    e.getKey());
            c.setChecked(e.getKey().equals(form.paymentMethod.getValue()));
            c.setOnCheckedChangeListener((b, checked) -> {
                if (checked && !syncingMethod) {
                    form.pickPaymentMethod(e.getKey());
                }
            });
        }
        syncingMethod = false;
    }

    /** 表单值变化（含异步写入的上次方式）时只改勾选，不触发“用户已选”的回调 */
    private void syncMethodChecked(@Nullable String code) {
        if (binding == null) {
            return;
        }
        if (code == null) {
            return;
        }
        // 只勾选目标项：单选组会自动取消其他项（必选组不允许直接取消唯一勾选项）
        ChipGroup group = binding.financeReceiptMethods;
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof Chip && code.equals(v.getTag()) && !((Chip) v).isChecked()) {
                syncingMethod = true;
                ((Chip) v).setChecked(true);
                syncingMethod = false;
            }
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
        // 金额和日期有各自的输入框；方式、通用提交错误及未知服务端字段都必须在弹层内可见。
        StringBuilder other = new StringBuilder();
        if (errors != null) {
            for (Map.Entry<String, String> error : errors.entrySet()) {
                if (!ReceiptFormDelegate.F_AMOUNT.equals(error.getKey())
                        && !ReceiptFormDelegate.F_DATE.equals(error.getKey())
                        && error.getValue() != null && !error.getValue().isEmpty()) {
                    if (other.length() > 0) other.append('\n');
                    other.append(error.getValue());
                }
            }
        }
        binding.financeReceiptError.setText(other);
        binding.financeReceiptError.setVisibility(other.length() == 0 ? View.GONE : View.VISIBLE);
    }
}
