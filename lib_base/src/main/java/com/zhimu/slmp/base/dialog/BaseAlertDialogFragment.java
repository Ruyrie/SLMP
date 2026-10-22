package com.zhimu.slmp.base.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zhimu.slmp.base.R;

/**
 * 第 5 级对话框的公共部分（{@link ConfirmDialogFragment}、{@link ReasonDialogFragment}）：
 * 参数读取、外观叠加层（危险 / 居中图标）、312 dp 宽度、结果经 FragmentResult 只回传一次、按 {@link DialogGuard} 显示。
 */
public abstract class BaseAlertDialogFragment extends DialogFragment {

    /** 结果：是否点了确认按钮 */
    public static final String RESULT_CONFIRMED = "confirmed";
    /** 结果：原样透传的 Bundle */
    public static final String RESULT_EXTRAS = "extras";

    static final String ARG_REQUEST_KEY = "base_dialog_request_key";
    static final String ARG_TITLE = "base_dialog_title";
    static final String ARG_MESSAGE = "base_dialog_message";
    static final String ARG_POSITIVE = "base_dialog_positive";
    static final String ARG_NEGATIVE = "base_dialog_negative";
    static final String ARG_DANGER = "base_dialog_danger";
    static final String ARG_ICON = "base_dialog_icon";
    static final String ARG_EXTRAS = "base_dialog_extras";
    static final String ARG_CANCEL_ON_OUTSIDE = "base_dialog_cancel_outside";

    private static final String STATE_RESULT_SENT = "base_dialog_result_sent";

    private boolean resultSent;

    public static boolean isConfirmed(@NonNull Bundle result) {
        return result.getBoolean(RESULT_CONFIRMED, false);
    }

    @Nullable
    public static Bundle getExtras(@NonNull Bundle result) {
        return result.getBundle(RESULT_EXTRAS);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            resultSent = savedInstanceState.getBoolean(STATE_RESULT_SENT, false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            // 对话框宽 312 dp（窗口背景两侧各有 24 dp inset），屏幕较窄时占满
            Resources res = dialog.getContext().getResources();
            int width = res.getDimensionPixelSize(R.dimen.base_dialog_width)
                    + 2 * res.getDimensionPixelSize(R.dimen.base_dialog_padding);
            int screen = res.getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.min(width, screen), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        sendResult(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESULT_SENT, resultSent);
    }

    /** 按参数创建带标题、正文、图标的 MaterialAlertDialogBuilder（按钮由子类添加） */
    @NonNull
    protected MaterialAlertDialogBuilder newBuilder() {
        Bundle args = requireArguments();
        int icon = args.getInt(ARG_ICON, 0);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(),
                overlayFor(icon != 0, args.getBoolean(ARG_DANGER, false)));
        builder.setTitle(args.getCharSequence(ARG_TITLE));
        CharSequence message = args.getCharSequence(ARG_MESSAGE);
        if (!TextUtils.isEmpty(message)) {
            builder.setMessage(message);
        }
        if (icon != 0) {
            builder.setIcon(icon);
        }
        return builder;
    }

    /** 回传结果（每个对话框只回传一次） */
    protected void sendResult(boolean confirmed) {
        sendResult(confirmed, new Bundle());
    }

    protected void sendResult(boolean confirmed, @NonNull Bundle result) {
        if (resultSent || !isAdded()) {
            return;
        }
        resultSent = true;
        Bundle args = requireArguments();
        result.putBoolean(RESULT_CONFIRMED, confirmed);
        Bundle extras = args.getBundle(ARG_EXTRAS);
        if (extras != null) {
            result.putBundle(RESULT_EXTRAS, extras);
        }
        String key = args.getString(ARG_REQUEST_KEY);
        if (key != null) {
            getParentFragmentManager().setFragmentResult(key, result);
        }
    }

    /** 按 {@link DialogGuard} 规则显示，标签为“base_dialog_ + requestKey” */
    public boolean showSafely(@NonNull FragmentManager fm) {
        String key = requireArguments().getString(ARG_REQUEST_KEY);
        return DialogGuard.show(this, fm, "base_dialog_" + key);
    }

    static int overlayFor(boolean centered, boolean danger) {
        if (centered) {
            return danger ? R.style.ThemeOverlay_Zmt_MaterialAlertDialog_Centered_Danger
                    : R.style.ThemeOverlay_Zmt_MaterialAlertDialog_Centered;
        }
        return danger ? R.style.ThemeOverlay_Zmt_MaterialAlertDialog_Danger
                : R.style.ThemeOverlay_Zmt_MaterialAlertDialog;
    }
}
