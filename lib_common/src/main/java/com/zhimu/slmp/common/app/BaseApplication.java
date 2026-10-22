package com.zhimu.slmp.common.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.ui.SnackbarHelper;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.CoreComponentProvider;
import com.zhimu.slmp.common.di.DaggerCoreComponent;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IAccountService;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.ServiceHub;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

/**
 * Application 基类（壳工程 ZhimuApp 与各组件单独运行的 XxxAloneApp 继承）：
 * <ol>
 * <li>创建 CoreComponent；RxJava 全局错误只记录日志；</li>
 * <li>按 moduleEntryClassNames() 反射加载 IModuleEntry 并调用 onCreate（找不到的类跳过）；</li>
 * <li>已同意隐私政策时调用各入口 onPrivacyAgreed（每个进程一次）；</li>
 * <li>ActivityLifecycleCallbacks 处理全局事件：重新登录（打开 A01 并清空返回栈）、全局提示（Snackbar）、
 * 需要更新（第 5 级对话框）、强制步骤（IAccountService.openNextStep）。</li>
 * </ol>
 */
public abstract class BaseApplication extends Application implements CoreComponentProvider {

    private static final String TAG = "ZmtApp";

    private CoreComponent coreComponent;
    private final List<IModuleEntry> entries = new ArrayList<>();
    private boolean privacyDispatched;

    @Nullable
    private WeakReference<Activity> resumed;
    private final List<Consumer<Activity>> pending = new ArrayList<>();

    /** 组件入口类名（全限定名），按顺序加载；不存在的类跳过 */
    @NonNull
    protected abstract List<String> moduleEntryClassNames();

    @NonNull
    public static BaseApplication from(@NonNull Context context) {
        return (BaseApplication) context.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        RxJavaPlugins.setErrorHandler(e -> {
            Throwable t = e instanceof UndeliverableException && e.getCause() != null ? e.getCause() : e;
            Log.w(TAG, "RxJava 未处理的错误", t);
        });
        coreComponent = DaggerCoreComponent.factory().create(this);
        registerActivityLifecycleCallbacks(new ForegroundCallbacks());
        loadEntries();
        for (IModuleEntry entry : entries) {
            try {
                entry.onCreate(this, coreComponent);
            } catch (RuntimeException e) {
                Log.e(TAG, "组件初始化失败：" + entry.getClass().getName(), e);
            }
        }
        observeGlobalEvents();
        if (PrivacyManager.isAgreed(coreComponent.appSettings())) {
            dispatchPrivacyAgreed();
        }
    }

    @NonNull
    @Override
    public CoreComponent getCoreComponent() {
        return coreComponent;
    }

    /** 已加载的组件入口 */
    @NonNull
    public List<IModuleEntry> getModuleEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** 用户同意隐私政策后调用（PrivacyManager 调用）；每个进程只分发一次 */
    public void dispatchPrivacyAgreed() {
        if (privacyDispatched) {
            return;
        }
        privacyDispatched = true;
        for (IModuleEntry entry : entries) {
            try {
                entry.onPrivacyAgreed(this);
            } catch (RuntimeException e) {
                Log.e(TAG, "隐私同意后初始化失败：" + entry.getClass().getName(), e);
            }
        }
    }

    private void loadEntries() {
        for (String name : moduleEntryClassNames()) {
            try {
                Class<?> cls = Class.forName(name);
                Object o = cls.getDeclaredConstructor().newInstance();
                if (o instanceof IModuleEntry) {
                    entries.add((IModuleEntry) o);
                } else {
                    Log.w(TAG, "不是 IModuleEntry：" + name);
                }
            } catch (ClassNotFoundException e) {
                Log.i(TAG, "组件未包含，跳过：" + name);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.e(TAG, "组件入口创建失败：" + name, e);
            }
        }
    }

    // ===== 全局事件 =====

    private void observeGlobalEvents() {
        GlobalEvents.relogin().observeForever(reason -> runOnForeground(a -> onRelogin(a, reason)));
        GlobalEvents.message().observeForever(msg -> {
            if (msg != null) {
                runOnForeground(a -> showMessage(a, msg));
            }
        });
        GlobalEvents.updateRequired().observeForever(info -> {
            if (info != null) {
                runOnForeground(a -> {
                    if (a instanceof FragmentActivity) {
                        UpdateDialogFragment.show((FragmentActivity) a, info.getMinVersion(), info.getDownloadUrl());
                    }
                });
            }
        });
        GlobalEvents.forcedStep().observeForever(step -> {
            IAccountService account = ServiceHub.get(IAccountService.class);
            if (step != null && account != null) {
                runOnForeground(a -> account.openNextStep(a, step));
            }
        });
    }

    /** 登录失效：在前台页面打开 A01 并清空返回栈；A01 未实现时（单独运行）回到调试启动页 */
    protected void onRelogin(@NonNull Activity activity, @Nullable String reason) {
        Intent current = activity.getIntent();
        if (current != null && RouterPath.A01.equals(current.getAction())) {
            return;
        }
        Bundle extras = new Bundle();
        extras.putString(RouterPath.EXTRA_REASON, reason);
        if (Router.canOpen(activity, RouterPath.A01)) {
            Router.openClearTask(activity, RouterPath.A01, extras);
            return;
        }
        Intent alone = new Intent(activity, AloneLauncherActivity.class);
        if (alone.resolveActivityInfo(getPackageManager(), 0) != null) {
            activity.startActivity(alone.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            return;
        }
        showMessage(activity, UiMessage.of(getString(R.string.common_core_relogin_message)));
    }

    private static void showMessage(@NonNull Activity activity, @NonNull UiMessage msg) {
        if (activity instanceof BaseActivity) {
            ((BaseActivity) activity).showMessage(msg);
        } else {
            SnackbarHelper.show(activity, msg);
        }
    }

    /** 在前台页面执行；当前没有前台页面时等下一个页面 onResume 后执行 */
    private void runOnForeground(@NonNull Consumer<Activity> action) {
        Activity a = resumed == null ? null : resumed.get();
        if (a != null && !a.isFinishing()) {
            accept(action, a);
        } else {
            pending.add(action);
        }
    }

    private static void accept(Consumer<Activity> action, Activity a) {
        try {
            action.accept(a);
        } catch (Throwable t) {
            Log.w(TAG, "全局事件处理失败", t);
        }
    }

    private final class ForegroundCallbacks implements ActivityLifecycleCallbacks {

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            resumed = new WeakReference<>(activity);
            if (!pending.isEmpty()) {
                List<Consumer<Activity>> run = new ArrayList<>(pending);
                pending.clear();
                for (Consumer<Activity> c : run) {
                    accept(c, activity);
                }
            }
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            if (resumed != null && resumed.get() == activity) {
                resumed = null;
            }
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
}
