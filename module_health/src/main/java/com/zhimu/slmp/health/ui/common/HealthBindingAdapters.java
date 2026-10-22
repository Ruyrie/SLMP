package com.zhimu.slmp.health.ui.common;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import com.google.android.material.textfield.TextInputLayout;

/**
 * 本组件的布局绑定（属性名带 health 前缀，避免与其他组件冲突）。
 */
public final class HealthBindingAdapters {

    private HealthBindingAdapters() {
    }

    /** 输入框错误文字：为空时恢复辅助文字 */
    @BindingAdapter("healthError")
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

    /** 辅助文字（为空时不显示） */
    @BindingAdapter("healthHelper")
    public static void setHelper(TextInputLayout layout, @Nullable CharSequence helper) {
        if (TextUtils.equals(layout.getHelperText(), helper)) {
            return;
        }
        layout.setHelperTextEnabled(!TextUtils.isEmpty(helper));
        layout.setHelperText(TextUtils.isEmpty(helper) ? null : helper);
    }

    /** 框内单位 */
    @BindingAdapter("healthSuffix")
    public static void setSuffix(TextInputLayout layout, @Nullable CharSequence suffix) {
        if (!TextUtils.equals(layout.getSuffixText(), suffix)) {
            layout.setSuffixText(TextUtils.isEmpty(suffix) ? null : suffix);
        }
    }
}
