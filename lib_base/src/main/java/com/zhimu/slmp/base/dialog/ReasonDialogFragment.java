package com.zhimu.slmp.base.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.R;

/**
 * 第 5 级弹窗：必须写一句说明的对话框（02 号《UI设计规范》7.11 节“带说明的对话框”），例如 T04“忽略这条待办？”
 * 填写忽略原因（100 字）、D05“删除这条用药记录？”填写申请原因（200 字）。
 * <p>
 * 只放一个输入框（宽 264 dp），浮动标签写字段名（“忽略原因”），辅助文字“必填”，右下角字数，到上限后不能继续输入；
 * 输入框为空（去掉首尾空白后）时确认按钮不可用。点遮罩不关闭（避免误触丢掉已写的说明），只能用按钮或返回键关闭。
 * 已填写的内容在旋转、进程回收后保留。
 * <p>
 * 结果经 FragmentResult 回传：{@link #isConfirmed(Bundle)}、{@link #getReason(Bundle)}（已去掉首尾空白）、{@link #getExtras(Bundle)}。
 * <pre>
 * ReasonDialogFragment.builder(REQ_IGNORE)
 *         .title("忽略这条待办？")
 *         .message("忽略后不再提醒，也不计入完成率。请写明原因。")
 *         .label("忽略原因")
 *         .maxLength(100)
 *         .positive("忽略").negative("取消")
 *         .build()
 *         .showSafely(getSupportFragmentManager());
 * </pre>
 */
public class ReasonDialogFragment extends BaseAlertDialogFragment {

    public static final String RESULT_REASON = "reason";

    private static final String ARG_LABEL = "base_reason_label";
    private static final String ARG_HELPER = "base_reason_helper";
    private static final String ARG_PLACEHOLDER = "base_reason_placeholder";
    private static final String ARG_MAX_LENGTH = "base_reason_max_length";
    private static final String ARG_PREFILL = "base_reason_prefill";

    /** 默认字数上限 */
    public static final int DEFAULT_MAX_LENGTH = 200;

    @Nullable
    private TextInputEditText input;

    @NonNull
    public static Builder builder(@NonNull String requestKey) {
        return new Builder(requestKey);
    }

    @Nullable
    public static String getReason(@NonNull Bundle result) {
        return result.getString(RESULT_REASON);
    }

    /** 对话框构建器 */
    public static final class Builder {
        private final Bundle args = new Bundle();

        Builder(@NonNull String requestKey) {
            args.putString(ARG_REQUEST_KEY, requestKey);
            args.putBoolean(ARG_CANCEL_ON_OUTSIDE, false);
            args.putInt(ARG_MAX_LENGTH, DEFAULT_MAX_LENGTH);
        }

        @NonNull
        public Builder title(@Nullable CharSequence title) {
            args.putCharSequence(ARG_TITLE, title);
            return this;
        }

        @NonNull
        public Builder message(@Nullable CharSequence message) {
            args.putCharSequence(ARG_MESSAGE, message);
            return this;
        }

        /** 输入框浮动标签（字段名，如“忽略原因”“申请原因”） */
        @NonNull
        public Builder label(@Nullable CharSequence label) {
            args.putCharSequence(ARG_LABEL, label);
            return this;
        }

        /** 辅助文字，默认“必填” */
        @NonNull
        public Builder helper(@Nullable CharSequence helper) {
            args.putCharSequence(ARG_HELPER, helper);
            return this;
        }

        /** 占位文字 */
        @NonNull
        public Builder placeholder(@Nullable CharSequence placeholder) {
            args.putCharSequence(ARG_PLACEHOLDER, placeholder);
            return this;
        }

        /** 字数上限，默认 200 */
        @NonNull
        public Builder maxLength(int maxLength) {
            args.putInt(ARG_MAX_LENGTH, maxLength);
            return this;
        }

        /** 预填内容（例如重新提交时带入上次的原因） */
        @NonNull
        public Builder prefill(@Nullable CharSequence text) {
            args.putCharSequence(ARG_PREFILL, text);
            return this;
        }

        /** 确认按钮（在右），写具体动作 */
        @NonNull
        public Builder positive(@NonNull CharSequence text) {
            args.putCharSequence(ARG_POSITIVE, text);
            return this;
        }

        /** 次按钮（在左），默认“取消” */
        @NonNull
        public Builder negative(@Nullable CharSequence text) {
            args.putCharSequence(ARG_NEGATIVE, text);
            return this;
        }

        /** 危险操作：确认按钮 error 色 */
        @NonNull
        public Builder danger(boolean danger) {
            args.putBoolean(ARG_DANGER, danger);
            return this;
        }

        /** 顶部 24 dp 图标（secondary 色），图标与标题居中 */
        @NonNull
        public Builder icon(@DrawableRes int icon) {
            args.putInt(ARG_ICON, icon);
            return this;
        }

        /** 原样透传到结果中的数据 */
        @NonNull
        public Builder extras(@Nullable Bundle extras) {
            args.putBundle(ARG_EXTRAS, extras);
            return this;
        }

        @NonNull
        public ReasonDialogFragment build() {
            ReasonDialogFragment f = new ReasonDialogFragment();
            f.setArguments(new Bundle(args));
            return f;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        MaterialAlertDialogBuilder builder = newBuilder();

        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.base_dialog_reason, null, false);
        TextInputLayout layout = view.findViewById(R.id.base_reason_layout);
        input = view.findViewById(R.id.base_reason_input);
        int maxLength = args.getInt(ARG_MAX_LENGTH, DEFAULT_MAX_LENGTH);
        layout.setHint(args.getCharSequence(ARG_LABEL));
        CharSequence helper = args.getCharSequence(ARG_HELPER);
        layout.setHelperText(helper != null ? helper : getString(R.string.base_reason_helper_required));
        layout.setPlaceholderText(args.getCharSequence(ARG_PLACEHOLDER));
        layout.setCounterMaxLength(maxLength);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        if (savedInstanceState == null) {
            CharSequence prefill = args.getCharSequence(ARG_PREFILL);
            if (prefill != null) {
                input.setText(prefill);
                input.setSelection(input.length());
            }
        }
        builder.setView(view);

        CharSequence positive = args.getCharSequence(ARG_POSITIVE);
        builder.setPositiveButton(positive != null ? positive : getString(R.string.base_action_got_it), (d, which) -> {
            Bundle result = new Bundle();
            result.putString(RESULT_REASON, currentReason());
            sendResult(true, result);
        });
        CharSequence negative = args.getCharSequence(ARG_NEGATIVE);
        builder.setNegativeButton(negative != null ? negative : getString(R.string.base_action_cancel),
                (d, which) -> sendResult(false));

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePositiveEnabled();
            }
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        updatePositiveEnabled();
        if (input != null) {
            input.requestFocus();
        }
    }

    @NonNull
    private String currentReason() {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void updatePositiveEnabled() {
        Dialog dialog = getDialog();
        if (dialog instanceof AlertDialog) {
            Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
            if (button != null) {
                button.setEnabled(!currentReason().isEmpty());
            }
        }
    }
}
