package com.zhimu.slmp.finance.ui.common;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.util.NavTarget;

/**
 * 模块10 页面跳转：NavTarget → Router（forResult 时用页面注册的 launcher；replace 时打开后关闭当前页）。
 */
public final class FinanceNav {

    private FinanceNav() {
    }

    public static void open(@NonNull Context context, @NonNull NavTarget t,
                            @Nullable ActivityResultLauncher<Intent> launcher) {
        boolean opened = t.isForResult() && launcher != null
                ? Router.openForResult(context, launcher, t.getAction(), t.toBundle())
                : Router.open(context, t.getAction(), t.toBundle());
        if (opened && t.isFinishSelf() && context instanceof Activity) {
            ((Activity) context).finish();
        }
    }

    /** 子页面保存成功：被以 Activity Result 打开时回传提示文字，否则用全局提示（返回后显示） */
    public static void finishWithMessage(@NonNull Activity activity, @Nullable String message) {
        if (activity.getCallingActivity() != null) {
            Intent data = new Intent().putExtra(FinanceExtras.RESULT_MESSAGE, message);
            activity.setResult(Activity.RESULT_OK, data);
        } else if (message != null) {
            GlobalEvents.postMessage(message);
        }
        activity.finish();
    }

    /** 读取子页面回传的提示文字 */
    @Nullable
    public static String messageOf(@NonNull ActivityResult result) {
        Intent data = result.getData();
        return result.getResultCode() == Activity.RESULT_OK && data != null
                ? data.getStringExtra(FinanceExtras.RESULT_MESSAGE) : null;
    }
}
