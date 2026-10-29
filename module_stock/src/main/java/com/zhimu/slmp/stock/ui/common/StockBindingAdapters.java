package com.zhimu.slmp.stock.ui.common;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import com.google.android.material.textfield.TextInputLayout;

/** 本组件的布局绑定（属性名带 stock 前缀，避免与其他组件冲突）。 */
public final class StockBindingAdapters {

    private StockBindingAdapters() {
    }

    /** 输入框错误文字：为空时清除错误态 */
    @BindingAdapter("stockError")
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

    /**
     * 辅助文字：为空时隐藏。MDC 设置辅助文字时会把正在显示的错误文字换下去，所以有错误时更新完辅助文字再把错误显示回来
     * （例如库存冲突后重拉结存，“最多 …”变了，但“库存已变化…”要一直显示到用户改数量或重新提交）。
     */
    @BindingAdapter("stockHelper")
    public static void setHelper(TextInputLayout layout, @Nullable CharSequence helper) {
        if (TextUtils.equals(layout.getHelperText(), helper)) {
            return;
        }
        CharSequence error = layout.getError();
        layout.setHelperTextEnabled(!TextUtils.isEmpty(helper));
        layout.setHelperText(TextUtils.isEmpty(helper) ? null : helper);
        if (!TextUtils.isEmpty(error)) {
            layout.setError(error);
        }
    }
}
