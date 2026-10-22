package com.zhimu.slmp.breed.ui.common;

import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

/**
 * 本组件的布局绑定（属性名带 breed 前缀，避免与其他组件冲突）。
 */
public final class BreedBindingAdapters {

    /** 只标红、不显示文字（活仔 / 死胎和校验的两个输入框，文案横跨两栏另行显示） */
    public static final String MARK_ONLY = "​";

    private BreedBindingAdapters() {
    }

    /** 输入框错误文字：为空时恢复辅助文字 */
    @BindingAdapter("breedError")
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

    /** 母猪状态标签：文字 + 按状态配色；文字为空时隐藏 */
    @BindingAdapter(value = {"breedStatusText", "breedStatus"}, requireAll = false)
    public static void setStatus(StatusTagView view, @Nullable String text, @Nullable String status) {
        view.setVisibility(TextUtils.isEmpty(text) ? android.view.View.GONE : android.view.View.VISIBLE);
        view.setStatus(text, BreedUi.statusTagType(status));
    }

    /** 推算卡等处的完整日期：“2027年2月10日 周三” */
    @BindingAdapter("breedFullDate")
    public static void setFullDate(TextView view, @Nullable String date) {
        view.setText(date == null ? null : BreedTextFormatter.fullDate(date));
    }
}
