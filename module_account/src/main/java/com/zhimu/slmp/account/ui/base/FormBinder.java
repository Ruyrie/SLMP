package com.zhimu.slmp.account.ui.base;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.util.PasswordInputFilter;
import com.zhimu.slmp.account.util.PhoneFormatWatcher;
import com.zhimu.slmp.account.util.RecoveryCodeWatcher;
import com.zhimu.slmp.base.util.ClickThrottle;

import java.util.Map;

/**
 * 表单页的通用绑定（2.4 节“所有页面都观察 fieldErrors、submitting、smsCountdown 与 smsSentHint”）：
 * 字段错误写入对应 TextInputLayout、focusField 聚焦并弹出键盘、验证码框尾部“获取验证码 / 52 秒后重新获取 / 重新获取”。
 */
public final class FormBinder {

    public static final String SMS_SEND = "获取验证码";
    public static final String SMS_RESEND = "重新获取";

    private FormBinder() {
    }

    /** fieldErrors → TextInputLayout.error（空则清除） */
    public static void bindFieldErrors(@NonNull LifecycleOwner owner, @NonNull LiveData<Map<String, String>> errors,
                                       @NonNull Map<String, TextInputLayout> fields) {
        errors.observe(owner, map -> {
            for (Map.Entry<String, TextInputLayout> e : fields.entrySet()) {
                String msg = map == null ? null : map.get(e.getKey());
                TextInputLayout til = e.getValue();
                if (msg == null) {
                    if (til.getError() != null) {
                        til.setError(null);
                        til.setErrorEnabled(false);
                    }
                } else {
                    til.setError(msg);
                }
            }
        });
    }

    /** focusField → 对应输入框获得焦点并弹出键盘 */
    public static void bindFocus(@NonNull LifecycleOwner owner, @NonNull LiveData<String> focus,
                                 @NonNull Map<String, TextInputLayout> fields) {
        focus.observe(owner, key -> {
            TextInputLayout til = key == null ? null : fields.get(key);
            EditText et = til == null ? null : til.getEditText();
            if (et != null) {
                et.requestFocus();
                et.setSelection(et.length());
                showKeyboard(et);
            }
        });
    }

    public static void showKeyboard(@NonNull View view) {
        view.post(() -> {
            InputMethodManager imm = (InputMethodManager) view.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /**
     * 验证码框尾部文字按钮（TextInputLayout 的 suffix）：未发送“获取验证码”（primary）；倒计时“52 秒后重新获取”
     * （on-surface-variant，不可点）；归零后“重新获取”（primary）。框下辅助文字为 smsSentHint。
     */
    public static void bindSmsField(@NonNull TextInputLayout til, @NonNull LifecycleOwner owner,
                                    @NonNull LiveData<Integer> countdown, @NonNull LiveData<String> sentHint,
                                    @NonNull Runnable onSend) {
        til.setExpandedHintEnabled(false);
        TextView suffix = til.getSuffixTextView();
        suffix.setClickable(true);
        suffix.setFocusable(false);
        suffix.setPadding(suffix.getPaddingLeft(), suffix.getPaddingTop(), suffix.getPaddingRight(),
                suffix.getPaddingBottom());
        int primary = MaterialColors.getColor(til, androidx.appcompat.R.attr.colorPrimary);
        int variant = MaterialColors.getColor(til, com.google.android.material.R.attr.colorOnSurfaceVariant);
        Runnable render = () -> {
            Integer c = countdown.getValue();
            boolean counting = c != null && c > 0;
            String text = counting ? c + " 秒后重新获取" : (sentHint.getValue() == null ? SMS_SEND : SMS_RESEND);
            til.setSuffixText(text);
            til.setSuffixTextColor(ColorStateList.valueOf(counting ? variant : primary));
            suffix.setEnabled(!counting);
        };
        countdown.observe(owner, v -> render.run());
        sentHint.observe(owner, hint -> {
            til.setHelperText(hint);
            render.run();
        });
        ClickThrottle.setOnClickListener(suffix, v -> {
            Integer c = countdown.getValue();
            if (c == null || c <= 0) {
                onSend.run();
            }
        });
        EditText et = til.getEditText();
        if (et != null) {
            et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        }
    }

    /** 手机号框：3-4-4 格式化，最多 13 个字符（含 2 个空格） */
    public static void setupPhone(@NonNull TextInputLayout til) {
        EditText et = til.getEditText();
        if (et != null) {
            et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(13)});
            PhoneFormatWatcher.attach(et);
        }
    }

    /** 密码框：阻止空格与非 ASCII，最多 20 位 */
    public static void setupPassword(@NonNull TextInputLayout til) {
        EditText et = til.getEditText();
        if (et != null) {
            PasswordInputFilter.attach(et);
        }
    }

    /** 恢复码框：大写、去非字母数字、每 4 位空格，最多 19 个字符 */
    public static void setupRecoveryCode(@NonNull TextInputLayout til) {
        EditText et = til.getEditText();
        if (et != null) {
            et.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(64)});
            RecoveryCodeWatcher.attach(et);
        }
    }

    /** 键盘“完成”等同点主按钮 */
    public static void onImeDone(@Nullable TextInputLayout til, @NonNull Runnable action) {
        EditText et = til == null ? null : til.getEditText();
        if (et != null) {
            et.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    action.run();
                    return true;
                }
                return false;
            });
        }
    }
}
