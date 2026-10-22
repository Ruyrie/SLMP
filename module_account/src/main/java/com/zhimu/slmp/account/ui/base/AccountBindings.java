package com.zhimu.slmp.account.ui.base;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import com.zhimu.slmp.base.widget.NoticeView;

/**
 * 模块01 的 DataBinding 适配：
 * <ul>
 * <li>{@code app:accountNotice="@{vm.banner}"}：横幅文案的第一句（到第一个“。”）作为加粗结论句，其余为正文；为空时隐藏</li>
 * </ul>
 */
public final class AccountBindings {

    private AccountBindings() {
    }

    @BindingAdapter("accountNotice")
    public static void setAccountNotice(@NonNull NoticeView view, @Nullable String text) {
        if (text == null || text.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        String[] parts = splitConclusion(text);
        view.setNotice(parts[0], parts[1]);
        view.setVisibility(View.VISIBLE);
    }

    /** 拆分为 [结论句, 其余]；没有“。”时整句为结论 */
    @NonNull
    public static String[] splitConclusion(@NonNull String text) {
        int idx = text.indexOf('。');
        if (idx < 0 || idx == text.length() - 1) {
            return new String[]{text, null};
        }
        return new String[]{text.substring(0, idx + 1), text.substring(idx + 1)};
    }
}
