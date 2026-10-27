package com.zhimu.slmp.today;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.zhimu.slmp.common.app.PrivacyManager;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.today.di.TodayComponent;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.vo.PushExtras;
import com.zhimu.slmp.today.push.NotificationChannels;
import com.zhimu.slmp.today.push.PushDeviceManager;
import com.zhimu.slmp.today.ui.home.TodayFragment;
import com.zhimu.slmp.today.ui.push.PushRouterActivity;
import com.zhimu.slmp.today.util.CalendarMode;
import com.zhimu.slmp.today.util.NavTarget;

import java.util.Arrays;
import java.util.List;

/**
 * 模块03 今日首页、日历待办与消息中心组件入口：创建 TodayComponent；向 ServiceHub 注册 ITodoService（唯一的未读数）与
 * IPushService（极光推送）；向 TabRegistry 注册“今日”标签；创建三个通知渠道；应用回到前台时刷新未读数。
 * 用户同意隐私政策后（onPrivacyAgreed）才初始化极光。
 */
public class TodayModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        TodayInjector.init(core);
        TodayComponent component = TodayInjector.component();
        ServiceHub.register(ITodoService.class, component.todoService());
        PushDeviceManager push = component.pushDeviceManager();
        ServiceHub.register(IPushService.class, push);
        TabRegistry.register(TabRegistry.TODAY, TodayFragment::new);

        NotificationChannels.createAll(app);
        if (!PrivacyManager.isAgreed(core.appSettings())) {
            push.beforePrivacyAgreed(app);
        }
        // 登录可发生在同一个前台进程内，此时不会再次收到 ProcessLifecycleOwner.onStart。
        core.sessionManager().getLoginState().observe(ProcessLifecycleOwner.get(), loggedIn -> {
            if (Boolean.TRUE.equals(loggedIn)) {
                push.syncDevice();
            }
        });
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                if (core.sessionManager().isLoggedIn()) {
                    component.todoService().refreshUnread();
                    push.syncDevice();
                }
            }
        });
    }

    @Override
    public void onPrivacyAgreed(@NonNull Application app) {
        TodayInjector.component().pushDeviceManager().init(app);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        return Arrays.asList(
                new DebugPage("T01 今日首页", RouterPath.T01),
                new DebugPage("T03 养殖日历（月视图）", RouterPath.T03,
                        extras(RouterPath.EXTRA_MODE, CalendarMode.MONTH.name(), RouterPath.EXTRA_DATE, "2026-10-19")),
                new DebugPage("T03 养殖日历（周视图）", RouterPath.T03,
                        extras(RouterPath.EXTRA_MODE, CalendarMode.WEEK.name(), RouterPath.EXTRA_DATE, "2026-10-19")),
                new DebugPage("T03 养殖日历（日视图）", RouterPath.T03,
                        extras(RouterPath.EXTRA_MODE, CalendarMode.DAY.name(), RouterPath.EXTRA_DATE, "2026-10-19")),
                new DebugPage("T04 待办详情（作业类 · 已逾期）", RouterPath.T04, longExtra(RouterPath.EXTRA_TODO_ID, 90112)),
                new DebugPage("T04 待办详情（经营类 · 休药期到期）", RouterPath.T04, longExtra(RouterPath.EXTRA_TODO_ID, 90201)),
                new DebugPage("T04 待办详情（已删除 → T08）", RouterPath.T04, longExtra(RouterPath.EXTRA_TODO_ID, 90999)),
                new DebugPage("T05 提醒设置", RouterPath.T05),
                new DebugPage("T06 消息中心", RouterPath.T06),
                new DebugPage("T07 通知详情（重要通知）", RouterPath.T07, longExtra(RouterPath.EXTRA_MESSAGE_ID, 507)),
                new DebugPage("T07 通知详情（已撤回）", RouterPath.T07, longExtra(RouterPath.EXTRA_MESSAGE_ID, 512)),
                new DebugPage("T07 通知详情（所有权转让）", RouterPath.T07, longExtra(RouterPath.EXTRA_MESSAGE_ID, 513)),
                new DebugPage("T08 关联记录已失效（批次已删除）", RouterPath.T08,
                        extras(RouterPath.EXTRA_PAGE_TITLE, "批次档案",
                                NavTarget.RecordInvalidKeys.DELETED_TITLE, "该批次记录已被删除，无法查看",
                                NavTarget.RecordInvalidKeys.DELETED_DESC,
                                "这条消息关联的批次已被删除，相关的待办和记录不再显示。消息本身仍保留在消息中心。")),
                new DebugPage("推送中转（AI 诊断消息 501）", PushRouterActivity.ACTION_OPEN,
                        longExtra(PushExtras.KEY_MESSAGE_ID, 501)),
                new DebugPage("推送中转（关联批次已删除 515）", PushRouterActivity.ACTION_OPEN,
                        longExtra(PushExtras.KEY_MESSAGE_ID, 515)));
    }

    @NonNull
    private static Bundle extras(@NonNull String... kv) {
        Bundle b = new Bundle();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            b.putString(kv[i], kv[i + 1]);
        }
        return b;
    }

    @NonNull
    private static Bundle longExtra(@NonNull String key, long value) {
        Bundle b = new Bundle();
        b.putLong(key, value);
        return b;
    }
}
