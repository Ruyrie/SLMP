package com.zhimu.slmp.health.ui.immuneplan;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.dialog.DialogGuard;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthDialogPostponeBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.HealthFormat;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * 调整计划日期对话框（图 8-2）：说明（原计划与提醒对象）、新日期（MaterialDatePicker，只能选今天及以后，辅助文字“届时 99 日龄”）、
 * 延期原因（必填，最多 50 字，“必填，会记入修改记录”）、权限说明；“确认延期”提交中显示加载，成功后由页面关闭。
 * 字段与提交状态保存在页面的 ImmunePlanViewModel 中（旋转后保留）。
 * 技术文档写作“继承 ReasonDialogFragment”，lib_base 对话框参数键为包内可见，改为直接继承 DialogFragment，外观同对话框规范。
 */
public class PostponeDialogFragment extends DialogFragment {

    public static final String TAG = "health_postpone_dialog";
    private static final String REQ_DATE = "health_postpone_date";

    private ImmunePlanViewModel vm;
    private HealthDialogPostponeBinding binding;

    public static void show(@NonNull FragmentManager fm) {
        DialogGuard.show(new PostponeDialogFragment(), fm, TAG);
    }

    public static void dismiss(@NonNull FragmentManager fm) {
        DialogFragment f = (DialogFragment) fm.findFragmentByTag(TAG);
        if (f != null) {
            f.dismissAllowingStateLoss();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), HealthInjector.factory()).get(ImmunePlanViewModel.class);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(),
                com.zhimu.slmp.base.R.style.ThemeOverlay_Zmt_MaterialAlertDialog);
        binding = HealthDialogPostponeBinding.inflate(LayoutInflater.from(builder.getContext()));
        builder.setTitle(R.string.health_postpone_title)
                .setView(binding.getRoot())
                .setNegativeButton(com.zhimu.slmp.base.R.string.base_action_cancel, (d, w) -> vm.cancelPostpone())
                .setPositiveButton(R.string.health_postpone_confirm, null);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        binding.healthPostponeMessage.setText(vm.postponeMessage());
        binding.healthReasonInput.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(HealthFormRules.POSTPONE_REASON_MAX)});
        if (savedInstanceState == null) {
            binding.healthReasonInput.setText(vm.getPostponeReason().getValue());
        }
        binding.healthReasonInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (!Objects.equals(text, vm.getPostponeReason().getValue())) {
                    vm.getPostponeReason().setValue(text);
                    vm.clearError(ImmunePlanViewModel.F_REASON);
                }
                updatePositive();
            }
        });
        ClickThrottle.setOnClickListener(binding.healthDateInput, v -> pickDate());
        binding.healthDateLayout.setEndIconOnClickListener(v -> pickDate());
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            // 对话框宽 312 dp（与 lib_base 对话框一致），屏幕较窄时占满
            android.content.res.Resources res = d.getContext().getResources();
            int width = res.getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_dialog_width)
                    + 2 * res.getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_dialog_padding);
            d.getWindow().setLayout(Math.min(width, res.getDisplayMetrics().widthPixels),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if (d instanceof AlertDialog) {
            Button positive = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
            ClickThrottle.setOnClickListener(positive, v -> vm.submitPostpone());
        }
        updatePositive();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getParentFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onPostponeDateSelected(DateTimePicker.getDate(result)));
    }

    @Override
    public void onResume() {
        super.onResume();
        observe();
    }

    private boolean observing;

    /** 对话框没有 viewLifecycleOwner（onCreateDialog 创建），以 Fragment 本身观察 */
    private void observe() {
        if (observing) {
            return;
        }
        observing = true;
        vm.getPostponeDate().observe(this, date -> {
            binding.healthDateInput.setText(HealthFormat.dateWeek(date));
            updatePositive();
        });
        vm.getPostponeAgeText().observe(this, text -> binding.healthDateLayout.setHelperText(
                TextUtils.isEmpty(text) ? null : text));
        vm.getReminderNames().observe(this, n -> binding.healthPostponeMessage.setText(vm.postponeMessage()));
        vm.getFieldErrors().observe(this, this::renderErrors);
        vm.getPostponing().observe(this, loading -> {
            boolean l = Boolean.TRUE.equals(loading);
            setCancelable(!l);
            Dialog d = getDialog();
            if (d instanceof AlertDialog) {
                Button positive = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive instanceof MaterialButton) {
                    ButtonLoading.setLoading((MaterialButton) positive, l);
                }
                Button negative = ((AlertDialog) d).getButton(AlertDialog.BUTTON_NEGATIVE);
                if (negative != null) {
                    negative.setEnabled(!l);
                }
            }
        });
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        String dateError = errors == null ? null : errors.get(ImmunePlanViewModel.F_NEW_DATE);
        String reasonError = errors == null ? null : errors.get(ImmunePlanViewModel.F_REASON);
        binding.healthDateLayout.setError(dateError);
        if (dateError == null) {
            binding.healthDateLayout.setErrorEnabled(false);
        }
        binding.healthReasonLayout.setError(reasonError);
        if (reasonError == null) {
            binding.healthReasonLayout.setErrorEnabled(false);
        }
    }

    /** 未选新日期或原因为空时“确认延期”不可点 */
    private void updatePositive() {
        Dialog d = getDialog();
        if (!(d instanceof AlertDialog)) {
            return;
        }
        Button positive = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive == null) {
            return;
        }
        String reason = vm.getPostponeReason().getValue();
        String date = vm.getPostponeDate().getValue();
        positive.setEnabled(reason != null && !reason.trim().isEmpty() && date != null && !date.isEmpty());
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getPostponeDate().getValue());
        LocalDate today = DateFormatter.today();
        DateTimePicker.date(REQ_DATE)
                .title(getString(R.string.health_postpone_new_date))
                .selection(selected != null && !selected.isBefore(today) ? selected : today)
                .bounds(today, null)
                .show(getParentFragmentManager());
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        super.onCancel(dialog);
        vm.cancelPostpone();
    }
}
