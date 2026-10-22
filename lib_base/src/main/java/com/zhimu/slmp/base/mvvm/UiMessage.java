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
