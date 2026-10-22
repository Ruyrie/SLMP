package com.zhimu.slmp.account.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 恢复码输入格式化（2.6.6 节）：转大写、去掉非字母数字（粘贴时去掉换行与短横线）、每 4 位插入空格，满 16 位停止。
 */
public class RecoveryCodeWatcher implements TextWatcher {

    public static final int CODE_LENGTH = 16;

    private boolean selfChange;

    public static void attach(@NonNull EditText editText) {
        editText.addTextChangedListener(new RecoveryCodeWatcher());
    }

    /** 规范化为 16 位以内的大写字母数字（不含空格） */
    @NonNull
    public static String normalize(@Nullable CharSequence raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String upper = raw.toString().toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 显示格式：每 4 位一组，组间一个空格 */
    @NonNull
    public static String format(@Nullable CharSequence raw) {
        String code = normalize(raw);
        if (code.length() > CODE_LENGTH) {
            code = code.substring(0, CODE_LENGTH);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                out.append(' ');
            }
            out.append(code.charAt(i));
        }
        return out.toString();
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
