package com.zhimu.slmp.stock.ui.common;

import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

/** 只关心 afterTextChanged 的 TextWatcher。 */
public class SimpleTextWatcher implements TextWatcher {

    private final Consumer<String> onChanged;

    public SimpleTextWatcher(@NonNull Consumer<String> onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        onChanged.accept(s == null ? "" : s.toString());
    }
}
