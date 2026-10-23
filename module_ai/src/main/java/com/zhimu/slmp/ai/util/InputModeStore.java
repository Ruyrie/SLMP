package com.zhimu.slmp.ai.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.zhimu.slmp.ai.model.InputMode;

/**
 * K01 记住的输入方式（本机，与账号无关）：只记用户最后一次主动点麦克风或键盘的选择，没有记录时为文字输入。
 */
public class InputModeStore {

    private static final String FILE = "ai_prefs";
    private static final String KEY = "input_mode";

    private final SharedPreferences prefs;

    public InputModeStore(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @NonNull
    public InputMode get() {
        String v = prefs.getString(KEY, null);
        return InputMode.VOICE.name().equals(v) ? InputMode.VOICE : InputMode.TEXT;
    }

    public void set(@NonNull InputMode mode) {
        prefs.edit().putString(KEY, mode.name()).apply();
    }
}
