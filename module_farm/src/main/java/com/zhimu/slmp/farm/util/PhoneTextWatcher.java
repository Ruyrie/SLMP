package com.zhimu.slmp.farm.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 手机号输入框按 3-4-4 自动插入空格（“138 0471 6620”），最多 11 位数字，保持光标位置。
 * 提交时用 {@link #digits(CharSequence)} 去掉空格。
 */
public class PhoneTextWatcher implements TextWatcher {

    public static final int MAX_DIGITS = 11;

    private final EditText editText;
    private boolean selfChange;

    public PhoneTextWatcher(@NonNull EditText editText) {
        this.editText = editText;
    }

    /** 给输入框加上格式化 */
    public static void attach(@NonNull EditText editText) {
        editText.addTextChangedListener(new PhoneTextWatcher(editText));
    }

    /** 只保留数字（最多 11 位） */
    @NonNull
    public static String digits(@Nullable CharSequence value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length() && sb.length() < MAX_DIGITS; i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 数字按 3-4-4 分组 */
    @NonNull
    public static String format(@Nullable CharSequence value) {
        String d = digits(value);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            if (i == 3 || i == 7) {
                sb.append(' ');
            }
            sb.append(d.charAt(i));
        }
        return sb.toString();
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
        if (formatted.contentEquals(s)) {
            return;
        }
        int cursor = editText.getSelectionEnd();
        int digitsBefore = digits(s.subSequence(0, Math.max(0, Math.min(cursor, s.length())))).length();
        selfChange = true;
        s.replace(0, s.length(), formatted);
        selfChange = false;
        int pos = 0;
        int seen = 0;
        while (pos < formatted.length() && seen < digitsBefore) {
            if (formatted.charAt(pos) != ' ') {
                seen++;
            }
            pos++;
        }
        editText.setSelection(Math.min(pos, formatted.length()));
    }
}
