package com.zhimu.slmp.herd.ui.scan;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.di.HerdInjector;

/**
 * L07“输入批次号”底部面板（复用 BatchScanViewModel）：字母自动转大写、等宽显示；输入为空时“打开档案”禁用；
 * 批次不存在或格式不对时输入框错误态。
 */
public class BatchNoInputBottomSheet extends BaseBottomSheetDialogFragment {

    private BatchScanViewModel vm;
    private TextInputLayout layout;
    private TextInputEditText input;

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.herd_scan_manual);
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.herd_scan_open);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), HerdInjector.factory()).get(BatchScanViewModel.class);
        layout = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setHint(R.string.herd_scan_input_label);
        layout.setPlaceholderText(getString(R.string.herd_scan_input_placeholder));
        input = new TextInputEditText(layout.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(20)});
        input.setTypeface(Typeface.MONOSPACE);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setSingleLine(true);
        layout.addView(input);
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_sheet_padding_horizontal);
        box.setPadding(pad, 0, pad, 0);
        box.addView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String current = vm.getBatchNoInput().getValue();
        input.setText(current);
        updatePrimary(current);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.onBatchNoChanged(s.toString());
                updatePrimary(s.toString());
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            onPrimaryButtonClick();
            return true;
        });
        vm.getBatchNoError().observe(getViewLifecycleOwner(), error -> layout.setError(error));
        vm.getLookupLoading().observe(getViewLifecycleOwner(), loading -> setPrimaryLoading(Boolean.TRUE.equals(loading)));
        input.requestFocus();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void updatePrimary(@Nullable String text) {
        if (getPrimaryButton() != null) {
            getPrimaryButton().setEnabled(text != null && !text.trim().isEmpty());
        }
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm.submitBatchNo();
    }
}
