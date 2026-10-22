package com.zhimu.slmp.common.app;

import android.app.Dialog;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * 隐私政策对话框（第 5 级）：不可点遮罩或返回键关闭；《用户协议》《隐私政策》可点开协议全文页；
 * 结果经 FragmentResult 交给 PrivacyManager。
 */
public class PrivacyDialogFragment extends DialogFragment {

    /** 协议全文页的 EXTRA_MODE 取值 */
    public static final String MODE_USER_AGREEMENT = "USER_AGREEMENT";
    public static final String MODE_PRIVACY_POLICY = "PRIVACY_POLICY";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setCancelable(false);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.common_core_privacy_title)
                .setMessage(buildMessage())
                .setPositiveButton(R.string.common_core_privacy_agree, (d, w) -> deliver(true))
                .setNegativeButton(R.string.common_core_privacy_disagree, (d, w) -> deliver(false))
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        TextView message = d == null ? null : d.findViewById(android.R.id.message);
        if (message != null) {
            message.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private CharSequence buildMessage() {
        String text = getString(R.string.common_core_privacy_message);
        SpannableString s = new SpannableString(text);
        link(s, text, getString(R.string.common_core_privacy_user_agreement), MODE_USER_AGREEMENT);
        link(s, text, getString(R.string.common_core_privacy_policy), MODE_PRIVACY_POLICY);
        return s;
    }

    private void link(SpannableString s, String text, String word, String mode) {
        int start = text.indexOf(word);
        if (start < 0) {
            return;
        }
        s.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Bundle extras = new Bundle();
                extras.putString(RouterPath.EXTRA_MODE, mode);
                Router.open(widget.getContext(), RouterPath.A07_AGREEMENT, extras);
            }
        }, start, start + word.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void deliver(boolean agreed) {
        Bundle result = new Bundle();
        result.putBoolean(PrivacyManager.RESULT_AGREED, agreed);
        getParentFragmentManager().setFragmentResult(PrivacyManager.REQUEST_KEY, result);
    }
}
