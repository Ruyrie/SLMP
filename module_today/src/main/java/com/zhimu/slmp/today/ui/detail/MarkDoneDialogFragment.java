package com.zhimu.slmp.today.ui.detail;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;

/**
 * “标记完成这条待办？”对话框（第 5 级，图 3-8）：顶部 task_alt 图标，正文为 mark_done_tip，按钮“去登记”“标记完成”。
 * 在 ConfirmDialogFragment 基础上区分“去登记”与取消（返回键、点遮罩）：点“去登记”时结果带 {@link #RESULT_GO_REGISTER}。
 */
public class MarkDoneDialogFragment extends ConfirmDialogFragment {

    public static final String RESULT_GO_REGISTER = "go_register";
    private static final String ARG_REGISTER = "today_mark_done_register";

    @NonNull
    public static MarkDoneDialogFragment newInstance(@NonNull String requestKey, @NonNull CharSequence title,
                                                     @Nullable CharSequence message, @NonNull CharSequence register,
                                                     @NonNull CharSequence confirm, int icon) {
        ConfirmDialogFragment base = ConfirmDialogFragment.builder(requestKey)
                .icon(icon)
                .title(title)
                .message(message)
                .positive(confirm)
                .negative(register)
                .build();
        Bundle args = base.requireArguments();
        args.putCharSequence(ARG_REGISTER, register);
        MarkDoneDialogFragment f = new MarkDoneDialogFragment();
        f.setArguments(args);
        return f;
    }

    public static boolean isGoRegister(@NonNull Bundle result) {
        return result.getBoolean(RESULT_GO_REGISTER, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog instanceof AlertDialog) {
            AlertDialog alert = (AlertDialog) dialog;
            CharSequence negative = requireArguments().getCharSequence(ARG_REGISTER);
            if (negative != null) {
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, negative, (d, which) -> {
                    Bundle result = new Bundle();
                    result.putBoolean(RESULT_GO_REGISTER, true);
                    sendResult(false, result);
                });
            }
        }
        return dialog;
    }
}
