package com.zhimu.slmp.inspect.ui.common;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.router.Router;

/**
 * 页面按 NavTarget 跳转；需要结果时用已注册的 launcher（返回后刷新当前页）。
 */
public final class InspectNav {

    private InspectNav() {
    }

    public static void open(@NonNull Activity activity, @Nullable ActivityResultLauncher<Intent> launcher,
                            @Nullable NavTarget target) {
        if (target == null) {
            GlobalEvents.postMessage(Router.MSG_NOT_READY);
            return;
        }
        boolean opened = target.isForResult() && launcher != null
                ? Router.openForResult(activity, launcher, target.getAction(), target.toBundle())
                : Router.open(activity, target.getAction(), target.toBundle());
        if (opened && target.isFinishCurrent()) {
            activity.finish();
        }
    }

    /**
     * 关闭当前页面并在上一页显示提示（全局提示在下一个前台页面显示），同时 setResult(RESULT_OK) 让上一页刷新。
     */
    public static void finishWithMessage(@NonNull Activity activity, @Nullable UiMessage message) {
        activity.setResult(Activity.RESULT_OK);
        activity.finish();
        if (message != null) {
            GlobalEvents.postMessage(message);
        }
    }
}
