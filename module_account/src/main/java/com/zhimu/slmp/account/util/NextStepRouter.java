package com.zhimu.slmp.account.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.common.app.AloneLauncherActivity;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * 按 next_step 分发页面（2.6.2 节）：NONE 进入 MainActivity；SAVE_RECOVERY_CODE 进入 A04；
 * RECOVERY_CHANGE_PASSWORD 进入 A06。均清空返回栈。
 * 组件单独运行时没有 MainActivity，改为回到调试启动页。
 */
public final class NextStepRouter {

    private NextStepRouter() {
    }

    /** next_step 对应的页面 action */
    @NonNull
    public static String actionOf(@Nullable String nextStep) {
        if (AuthTokenVO.NEXT_SAVE_RECOVERY_CODE.equals(nextStep)) {
            return RouterPath.A04;
        }
        if (AuthTokenVO.NEXT_RECOVERY_CHANGE_PASSWORD.equals(nextStep)) {
            return RouterPath.A06;
        }
        return RouterPath.MAIN;
    }

    /** 分发并关闭当前页 */
    public static void dispatch(@NonNull Activity from, @Nullable String nextStep) {
        String action = actionOf(nextStep);
        if (RouterPath.MAIN.equals(action)) {
            openMain(from, null);
            return;
        }
        Router.openClearTask(from, action, null);
        from.finish();
    }

    /**
     * 进入主页（T01）并清空返回栈；message 不为空时在主页底部用 Snackbar 显示
     * （当前页先 finish，全局提示会排队到下一个前台页面）。
     */
    public static void openMain(@NonNull Activity from, @Nullable String message) {
        openClearTaskOrAlone(from, RouterPath.MAIN, null);
        from.finish();
        if (message != null) {
            GlobalEvents.postMessage(message);
        }
    }

    /** 回到 A01 登录页并清空返回栈（手机号预填、Snackbar 提示由 A01 显示） */
    public static void openLogin(@NonNull Context from, @Nullable String phone, @Nullable String message) {
        Bundle extras = new Bundle();
        if (phone != null) {
            extras.putString(RouterPath.EXTRA_PHONE, phone);
        }
        if (message != null) {
            extras.putString(RouterPath.EXTRA_MESSAGE, message);
        }
        openClearTaskOrAlone(from, RouterPath.A01, extras);
        if (from instanceof Activity) {
            ((Activity) from).finish();
        }
    }

    private static void openClearTaskOrAlone(@NonNull Context from, @NonNull String action, @Nullable Bundle extras) {
        if (Router.canOpen(from, action)) {
            Router.openClearTask(from, action, extras);
            return;
        }
        Intent alone = new Intent(from, AloneLauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (alone.resolveActivityInfo(from.getPackageManager(), 0) != null) {
            from.startActivity(alone);
        } else {
            Router.openClearTask(from, action, extras);
        }
    }
}
