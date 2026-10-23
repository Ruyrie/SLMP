package com.zhimu.slmp.feed.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.FeedExtras;

/** 页面跳转与“返回来源页并提示”。 */
public final class FeedNav {

    private FeedNav() {
    }

    /**
     * 关闭当前页面并在下一个前台页面显示提示（全局提示，BaseApplication 在 onResume 后显示），
     * 同时 setResult(RESULT_OK) 让上一页刷新。
     */
    public static void finishWithMessage(@NonNull Activity activity, @Nullable UiMessage message) {
        activity.setResult(Activity.RESULT_OK);
        activity.finish();
        if (message != null) {
            GlobalEvents.postMessage(message);
        }
    }

    /** 打开页面；有 launcher 时以结果方式打开（返回后刷新） */
    public static void open(@NonNull Activity activity, @Nullable ActivityResultLauncher<Intent> launcher,
                            @NonNull String action, @Nullable Bundle extras) {
        if (launcher != null) {
            Router.openForResult(activity, launcher, action, extras);
        } else {
            Router.open(activity, action, extras);
        }
    }

    /** G02 登记投喂（任务） */
    @NonNull
    public static Bundle taskArgs(long taskId) {
        Bundle b = new Bundle();
        b.putLong(RouterPath.EXTRA_TASK_ID, taskId);
        return b;
    }

    /** G02 投喂记录（修改 / 只读） */
    @NonNull
    public static Bundle recordArgs(long recordId) {
        Bundle b = new Bundle();
        b.putLong(RouterPath.EXTRA_RECORD_ID, recordId);
        return b;
    }

    @NonNull
    public static Bundle batchArgs(long batchId) {
        Bundle b = new Bundle();
        b.putLong(RouterPath.EXTRA_BATCH_ID, batchId);
        return b;
    }

    /** U01 提交修改申请 */
    @NonNull
    public static Bundle changeArgs(@NonNull String bizType, long bizId) {
        Bundle b = new Bundle();
        b.putString(RouterPath.EXTRA_BIZ_TYPE, bizType);
        b.putLong(RouterPath.EXTRA_BIZ_ID, bizId);
        return b;
    }

    /** M03 饲料入库（预填饲料） */
    @NonNull
    public static Bundle stockInArgs(long feedId) {
        Bundle b = new Bundle();
        b.putLong(RouterPath.EXTRA_FEED_ID, feedId);
        return b;
    }
}
