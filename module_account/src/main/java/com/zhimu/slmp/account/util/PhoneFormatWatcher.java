package com.zhimu.slmp.account.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 手机号输入格式化（2.6.3 节）：只收数字，按 3-4-4 插入空格（“138 0471 6620”），满 11 位停止。
 * 提交时由 ViewModel 去掉空格。
 */
public class PhoneFormatWatcher implements TextWatcher {

    public static final int MAX_DIGITS = 11;

    private boolean selfChange;

    /** 绑定到输入框（同时把最大长度设为 13） */
    public static void attach(@NonNull EditText editText) {
        editText.addTextChangedListener(new PhoneFormatWatcher());
    }

    /** 把任意输入格式化为 3-4-4 */
    @NonNull
    public static String format(@Nullable CharSequence raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length() && digits.length() < MAX_DIGITS; i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i == 3 || i == 7) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    /** 去掉空格后的号码 */
    @NonNull
    public static String digits(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().replaceAll("[^0-9]", "");
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (selfChange) {
            return;
        }
        String formatted = format(s);
        if (!formatted.contentEquals(s)) {
            selfChange = true;
            s.replace(0, s.length(), formatted);
            selfChange = false;
        }
    }
}
