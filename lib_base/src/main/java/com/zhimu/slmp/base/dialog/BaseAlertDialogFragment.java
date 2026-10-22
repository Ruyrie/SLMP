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
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.zhimu.slmp.base.widget.ButtonLoading;

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
    static final String ARG_KEEP_OPEN = "base_dialog_keep_open";

    private static final String STATE_RESULT_SENT = "base_dialog_result_sent";
    private static final String STATE_LOADING = "base_dialog_loading";
    private static final String TAG_PREFIX = "base_dialog_";

    private boolean resultSent;
    private boolean loading;

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
            loading = savedInstanceState.getBoolean(STATE_LOADING, false);
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
        if (isKeepOpen() && dialog instanceof AlertDialog) {
            // 确认后不关闭：接管确认按钮，回传结果后由宿主控制加载状态与关闭
            Button positive = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setOnClickListener(v -> {
                    if (!loading) {
                        resultSent = false;
                        sendResult(true, positiveResult());
                    }
                });
            }
        }
        applyLoading();
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
        outState.putBoolean(STATE_LOADING, loading);
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

    /** 点确认按钮时回传的结果（子类补充自己的字段，例如原因） */
    @NonNull
    protected Bundle positiveResult() {
        return new Bundle();
    }

    private boolean isKeepOpen() {
        return getArguments() != null && getArguments().getBoolean(ARG_KEEP_OPEN, false);
    }

    /**
     * 确认后不关闭的对话框（构建时 keepOpenOnConfirm）：提交中确认按钮显示加载圈，取消按钮、返回键、遮罩都不响应。
     * 宿主收到确认结果后调用，也可以在 ViewModel 的 submitting 变化时调用；对话框尚未显示时在显示后生效。
     */
    public static void setLoading(@NonNull FragmentManager fm, @NonNull String requestKey, boolean loading) {
        Fragment f = fm.findFragmentByTag(TAG_PREFIX + requestKey);
        if (f instanceof BaseAlertDialogFragment) {
            ((BaseAlertDialogFragment) f).setLoading(loading);
        }
    }

    /** 关闭对话框（提交成功后调用；对话框不存在时忽略） */
    public static void dismiss(@NonNull FragmentManager fm, @NonNull String requestKey) {
        Fragment f = fm.findFragmentByTag(TAG_PREFIX + requestKey);
        if (f instanceof DialogFragment) {
            ((DialogFragment) f).dismissAllowingStateLoss();
        }
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        applyLoading();
    }

    private void applyLoading() {
        Dialog dialog = getDialog();
        if (!(dialog instanceof AlertDialog)) {
            return;
        }
        setCancelable(!loading);
        Button positive = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive instanceof MaterialButton) {
            ButtonLoading.setLoading((MaterialButton) positive, loading);
        }
        Button negative = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setEnabled(!loading);
        }
    }

    /** 回传结果（每个对话框只回传一次；确认后不关闭的对话框每次点确认都回传） */
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
        return DialogGuard.show(this, fm, TAG_PREFIX + key);
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
