package com.zhimu.slmp.today.ui.push;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.TaskStackBuilder;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.app.PrivacyManager;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.vo.PushExtras;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.TodayNavigator;

/**
 * 推送中转页（透明、不进最近任务、不显示界面）：点击通知统一打开本页，读取附加字段后按 03 号 11.3 节跳转。
 * 是除启动页外唯一导出的页面，只处理 {@link #ACTION_OPEN} 与极光、厂商通道的 Intent。
 * 冷启动时先确认隐私政策；未登录先进入 A01（以 Activity Result 打开，登录成功后继续）；
 * MainActivity 不在任务栈时用 TaskStackBuilder 先放主页，返回键回到今日首页。
 */
public class PushRouterActivity extends BaseActivity {

    /** 约定的打开推送 action（厂商通道 intent_uri 也使用本 action） */
    public static final String ACTION_OPEN = "com.zhimu.slmp.action.PUSH_OPEN";

    private static final long TIP_DELAY_MS = 350;

    private PushRouterViewModel vm;
    private ActivityResultLauncher<Intent> loginLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(TodayInjector.factory(), PushRouterViewModel.class);
        loginLauncher = Router.registerForResult(this,
                result -> vm.onLoginResult(result.getResultCode() == Activity.RESULT_OK));
        vm.getRequireLogin().observe(this, v -> {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_MODE, "push");
            if (!Router.openForResult(this, loginLauncher, RouterPath.A01, extras)) {
                finish();
            }
        });
        vm.getNavEvent().observe(this, this::openTarget);
        vm.getFinish().observe(this, v -> finish());
        if (savedInstanceState == null) {
            PushExtras extras = PushExtras.fromBundle(getIntent().getExtras());
            PrivacyManager.ensureAgreed(this, () -> vm.handle(extras));
        }
    }

    @Override
    protected boolean applyWindowInsetsToRoot() {
        return false;
    }

    private void openTarget(@Nullable NavTarget target) {
        if (target == null) {
            finish();
            return;
        }
        String action = TodayNavigator.actionOf(this, target.getRoute());
        if (action == null || !Router.canOpen(this, action)) {
            GlobalEvents.postMessage(Router.MSG_NOT_READY);
            finish();
            return;
        }
        Intent intent = new Intent(action).setPackage(getPackageName())
                .putExtras(TodayNavigator.toBundle(target.getParams()));
        boolean toMain = RouterPath.MAIN.equals(action);
        if (isTaskRoot() && !toMain && Router.canOpen(this, RouterPath.MAIN)) {
            Intent main = new Intent(RouterPath.MAIN).setPackage(getPackageName());
            TaskStackBuilder.create(this).addNextIntent(main).addNextIntent(intent).startActivities();
        } else {
            if (toMain) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            if (isTaskRoot()) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            startActivity(intent);
        }
        postTip(target.getSwitchTip());
        finish();
    }

    private static void postTip(@Nullable String tip) {
        if (tip != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> GlobalEvents.postMessage(tip), TIP_DELAY_MS);
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        vm.handle(PushExtras.fromBundle(intent.getExtras()));
    }
}
