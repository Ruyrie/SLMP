package com.zhimu.slmp.base.mvvm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一次性提示（第 1 级弹窗 Snackbar）：文案、最多一个操作。
 * 操作被点击时，BaseActivity / BaseFragment 回调 onMessageAction(actionId)。
 */
public final class UiMessage {

    /** 默认 4 秒 */
    public static final int DURATION_DEFAULT = 4000;

    /** 页面 action 前缀（与 RouterPath 一致）：actionId 为页面 action 时，点击操作由基类直接打开该页面 */
    public static final String PAGE_ACTION_PREFIX = "com.zhimu.slmp.page.";

    @NonNull
    private final String text;
    @Nullable
    private final String actionText;
    @Nullable
    private final String actionId;
    private final int durationMs;

    private UiMessage(@NonNull String text, @Nullable String actionText, @Nullable String actionId, int durationMs) {
        this.text = text;
        this.actionText = actionText;
        this.actionId = actionId;
        this.durationMs = durationMs;
    }

    public static UiMessage of(@NonNull String text) {
        return new UiMessage(text, null, null, DURATION_DEFAULT);
    }

    public static UiMessage withAction(@NonNull String text, @NonNull String actionText, @NonNull String actionId) {
        return new UiMessage(text, actionText, actionId, DURATION_DEFAULT);
    }

    /**
     * 操作为“打开某个页面”的提示，例如“已提交修改申请，等待管理员审核”+“查看”打开 U02。
     * 可用于全局提示（GlobalEvents.postMessage），提示显示在哪个页面都能正确跳转。
     *
     * @param pageAction 页面 action，即 RouterPath 中的常量
     */
    public static UiMessage withPage(@NonNull String text, @NonNull String actionText, @NonNull String pageAction) {
        return new UiMessage(text, actionText, pageAction, DURATION_DEFAULT);
    }

    /** actionId 是否为页面 action */
    public static boolean isPageAction(@Nullable String actionId) {
        return actionId != null && actionId.startsWith(PAGE_ACTION_PREFIX);
    }

    @NonNull
    public String getText() {
        return text;
    }

    @Nullable
    public String getActionText() {
        return actionText;
    }

    @Nullable
    public String getActionId() {
        return actionId;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public boolean hasAction() {
        return actionText != null && actionId != null;
    }
}
