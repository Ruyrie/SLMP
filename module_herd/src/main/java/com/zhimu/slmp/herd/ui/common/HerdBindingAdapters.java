package com.zhimu.slmp.herd.ui.common;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import com.google.android.material.textfield.TextInputLayout;

/**
 * 本组件的布局绑定（属性名带 herd 前缀，避免与其他组件冲突）。
 */
public final class HerdBindingAdapters {

    private HerdBindingAdapters() {
    }

    /** 输入框错误文字：为空时恢复辅助文字 */
    @BindingAdapter("herdError")
    public static void setError(TextInputLayout layout, @Nullable CharSequence error) {
        CharSequence old = layout.getError();
        if (TextUtils.equals(old, error)) {
            return;
        }
        layout.setError(TextUtils.isEmpty(error) ? null : error);
        if (TextUtils.isEmpty(error)) {
            layout.setErrorEnabled(false);
        }
    }
}
