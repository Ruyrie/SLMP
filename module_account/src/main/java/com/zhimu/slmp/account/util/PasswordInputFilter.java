package com.zhimu.slmp.account.util;

import android.text.InputFilter;
import android.text.Spanned;
import android.widget.EditText;

import androidx.annotation.NonNull;

/**
 * 密码输入过滤：阻止空格与非 ASCII 字符（只允许字母、数字与英文符号），最长 20 位。
 */
public class PasswordInputFilter implements InputFilter {

    public static final int MAX_LENGTH = 20;

    /** 给输入框加上本过滤与长度上限 */
    public static void attach(@NonNull EditText editText) {
        editText.setFilters(new InputFilter[]{new PasswordInputFilter(), new InputFilter.LengthFilter(MAX_LENGTH)});
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        StringBuilder sb = null;
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            boolean ok = c > 0x20 && c < 0x7F;
            if (!ok) {
                if (sb == null) {
                    sb = new StringBuilder(source.subSequence(start, i));
                }
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? null : sb.toString();
    }
}
