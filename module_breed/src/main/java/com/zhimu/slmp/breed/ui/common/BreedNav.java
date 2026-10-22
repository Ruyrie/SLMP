package com.zhimu.slmp.breed.ui.common;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;

/**
 * 页面跳转与登记结果：本组件页面之间以“需要结果”打开（EXTRA_FOR_RESULT），成功后 setResult 带回提示，由上一页显示
 * Snackbar 并只刷新受影响数据；从其他组件（T04 等）打开时用全局提示，返回后在前台页面显示。
 */
public final class BreedNav {

    /** Snackbar“查看批次”的操作 ID 前缀，后接 batch_id */
    public static final String ACTION_VIEW_BATCH = "breed_view_batch:";

    private BreedNav() {
    }

    /** 本组件内打开登记页、档案页时附加 EXTRA_FOR_RESULT */
    @NonNull
    public static Bundle forResult(@Nullable Bundle extras) {
        Bundle b = extras == null ? new Bundle() : new Bundle(extras);
        b.putBoolean(BreedExtras.EXTRA_FOR_RESULT, true);
        return b;
    }

    @NonNull
    public static Bundle id(@NonNull String key, long value) {
        Bundle b = new Bundle();
        b.putLong(key, value);
        return b;
    }

    /**
     * 关闭当前页面并交回提示：上一页为本组件页面时 setResult，由上一页显示（可带“查看批次”）；否则全局提示。
     */
    public static void finishWithMessage(@NonNull Activity activity, @Nullable String text, @Nullable Long batchId) {
        finish(activity, text, batchId, false);
    }

    /** 死亡淘汰后关闭档案并回到 B01 */
    public static void finishToList(@NonNull Activity activity, @Nullable String text) {
        finish(activity, text, null, true);
    }

    private static void finish(@NonNull Activity activity, @Nullable String text, @Nullable Long batchId,
                               boolean toList) {
        boolean forResult = activity.getIntent() != null
                && activity.getIntent().getBooleanExtra(BreedExtras.EXTRA_FOR_RESULT, false);
        Intent data = new Intent();
        data.putExtra(BreedExtras.RESULT_MESSAGE, text);
        if (batchId != null) {
            data.putExtra(BreedExtras.RESULT_BATCH_ID, batchId.longValue());
        }
        data.putExtra(BreedExtras.RESULT_TO_LIST, toList);
        activity.setResult(Activity.RESULT_OK, data);
        activity.finish();
        if (!forResult && text != null) {
            if (batchId != null) {
                Bundle extras = new Bundle();
                extras.putLong(RouterPath.EXTRA_BATCH_ID, batchId);
                GlobalEvents.postMessage(UiMessage.withPage(text, "查看批次", RouterPath.L06, extras));
            } else {
                GlobalEvents.postMessage(UiMessage.of(text));
            }
        }
    }

    /** 上一页收到结果：返回提示（带“查看批次”操作），没有结果时为 null */
    @Nullable
    public static UiMessage messageOf(@NonNull Context context, @NonNull ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return null;
        }
        Intent data = result.getData();
        String text = data.getStringExtra(BreedExtras.RESULT_MESSAGE);
        if (text == null) {
            return null;
        }
        if (data.hasExtra(BreedExtras.RESULT_BATCH_ID)) {
            long batchId = data.getLongExtra(BreedExtras.RESULT_BATCH_ID, 0);
            return UiMessage.withAction(text, context.getString(R.string.breed_view_batch), ACTION_VIEW_BATCH + batchId);
        }
        return UiMessage.of(text);
    }

    public static boolean isToList(@NonNull ActivityResult result) {
        return result.getData() != null && result.getData().getBooleanExtra(BreedExtras.RESULT_TO_LIST, false);
    }

    /** 处理本组件的 Snackbar 操作；已处理返回 true */
    public static boolean handleAction(@NonNull Context context, @NonNull String actionId) {
        if (actionId.startsWith(ACTION_VIEW_BATCH)) {
            try {
                long batchId = Long.parseLong(actionId.substring(ACTION_VIEW_BATCH.length()));
                Router.open(context, RouterPath.L06, id(RouterPath.EXTRA_BATCH_ID, batchId));
            } catch (NumberFormatException ignored) {
                // 忽略格式错误的操作 ID
            }
            return true;
        }
        return false;
    }

    /** 登记完成后通知首页与日历刷新待办（ITodoService 不存在时跳过） */
    public static void notifyTodo(@Nullable Long todoId, @NonNull String bizType, @Nullable Long bizId, long farmId) {
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(todoId, bizType, bizId, farmId));
        }
    }

    /** “放弃本次登记？”对话框 */
    public static void showDiscard(@NonNull AppCompatActivity activity, @NonNull String requestKey) {
        ConfirmDialogFragment.builder(requestKey)
                .title(activity.getString(R.string.breed_discard_title))
                .message(activity.getString(R.string.breed_discard_message))
                .negative(activity.getString(R.string.breed_action_keep_editing))
                .positive(activity.getString(R.string.breed_action_discard))
                .build()
                .showSafely(activity.getSupportFragmentManager());
    }

    /** 确认放弃后关闭页面 */
    public static void listenDiscard(@NonNull AppCompatActivity activity, @NonNull String requestKey) {
        activity.getSupportFragmentManager().setFragmentResultListener(requestKey, activity, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                activity.finish();
            }
        });
    }
}
