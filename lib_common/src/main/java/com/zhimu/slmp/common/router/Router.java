package com.zhimu.slmp.common.router;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.event.GlobalEvents;

/**
 * 组件间页面跳转：隐式 Intent（action 见 RouterPath）并限定本应用包名，调用方不引用其他组件的类。
 * 目标页面尚未实现（清单中没有对应 action）时提示“该功能正在开发中”。
 * 目标 Activity 的 intent-filter 需同时声明 action 与 {@code android.intent.category.DEFAULT}，exported="false"。
 */
public final class Router {

    public static final String MSG_NOT_READY = "该功能正在开发中";

    private Router() {
    }

    /** 创建限定本应用包名的隐式 Intent */
    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull String action, @Nullable Bundle extras) {
        Intent intent = new Intent(action).setPackage(context.getPackageName());
        if (extras != null) {
            intent.putExtras(extras);
        }
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    /** 目标页面是否已实现 */
    public static boolean canOpen(@NonNull Context context, @NonNull String action) {
        Intent intent = new Intent(action).setPackage(context.getPackageName());
        return context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    public static boolean open(@NonNull Context context, @NonNull String action) {
        return open(context, action, null);
    }

    /** 打开页面；未实现时提示并返回 false */
    public static boolean open(@NonNull Context context, @NonNull String action, @Nullable Bundle extras) {
        if (!canOpen(context, action)) {
            GlobalEvents.postMessage(MSG_NOT_READY);
            return false;
        }
        context.startActivity(intent(context, action, extras));
        return true;
    }

    /** 按页面编号打开（推送、消息的 route 字段） */
    public static boolean openPage(@NonNull Context context, @Nullable String pageCode, @Nullable Bundle extras) {
        String action = RouterTable.actionOf(pageCode);
        if (action == null) {
            GlobalEvents.postMessage(MSG_NOT_READY);
            return false;
        }
        return open(context, action, extras);
    }

    /**
     * 注册“打开页面并接收结果”（在 Activity / Fragment 初始化时调用，即 onCreate 之前或之中）。
     * <pre>
     * launcher = Router.registerForResult(this, result -&gt; { if (result.getResultCode() == RESULT_OK) ... });
     * Router.openForResult(this, launcher, RouterPath.F04, extras);
     * </pre>
     */
    @NonNull
    public static ActivityResultLauncher<Intent> registerForResult(@NonNull ActivityResultCaller caller,
                                                                   @NonNull ActivityResultCallback<ActivityResult> callback) {
        return caller.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), callback);
    }

    /** 用已注册的 launcher 打开页面；未实现时提示并返回 false */
    public static boolean openForResult(@NonNull Context context, @NonNull ActivityResultLauncher<Intent> launcher,
                                        @NonNull String action, @Nullable Bundle extras) {
        if (!canOpen(context, action)) {
            GlobalEvents.postMessage(MSG_NOT_READY);
            return false;
        }
        Intent intent = new Intent(action).setPackage(context.getPackageName());
        if (extras != null) {
            intent.putExtras(extras);
        }
        launcher.launch(intent);
        return true;
    }

    /** 打开页面并清空返回栈（登录成功进入主页、登录失效回到 A01） */
    public static boolean openClearTask(@NonNull Context context, @NonNull String action, @Nullable Bundle extras) {
        if (!canOpen(context, action)) {
            GlobalEvents.postMessage(MSG_NOT_READY);
            return false;
        }
        Intent intent = intent(context, action, extras)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        return true;
    }
}
