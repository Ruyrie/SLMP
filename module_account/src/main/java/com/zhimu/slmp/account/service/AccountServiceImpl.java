package com.zhimu.slmp.account.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.util.NextStepRouter;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.SessionVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IAccountService;
import com.zhimu.slmp.common.session.SessionManager;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * IAccountService 实现（2.6.2 节）：
 * <ul>
 * <li>checkSession：没有刷新令牌打开 A01；有则调 API-A08（访问令牌过期由认证器刷新），按 next_step 分发：
 * NONE 时恢复上次或首个可用养殖场后进入 MainActivity；加载失败留在启动页并提供重试，避免空上下文进入主页；</li>
 * <li>openNextStep：40309 的 next_step（BaseApplication 转发）；</li>
 * <li>logout：IPushService.unbindCurrent → API-A07（失败不阻断）→ 清除会话 → 回到 A01。</li>
 * </ul>
 */
@FeatureScope
public class AccountServiceImpl implements IAccountService {

    private static final String TAG = "AccountService";
    private static final String REQUEST_STARTUP_RETRY = "account_startup_retry";

    private final Context context;
    private final AccountRepository repository;
    private final SessionManager sessionManager;

    @Inject
    public AccountServiceImpl(@NonNull Context context, @NonNull AccountRepository repository,
                              @NonNull SessionManager sessionManager) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.sessionManager = sessionManager;
    }

    @Override
    public void checkSession(@NonNull Activity splash) {
        if (!sessionManager.isLoggedIn()) {
            NextStepRouter.openLogin(splash, null, null);
            return;
        }
        if (splash instanceof FragmentActivity) {
            FragmentActivity host = (FragmentActivity) splash;
            host.getSupportFragmentManager().setFragmentResultListener(REQUEST_STARTUP_RETRY, host,
                    (key, result) -> {
                        if (ConfirmDialogFragment.isConfirmed(result)) {
                            checkSession(host);
                        } else {
                            NextStepRouter.openLogin(host, null, null);
                        }
                    });
        }
        WeakReference<Activity> ref = new WeakReference<>(splash);
        //noinspection ResultOfMethodCallIgnored
        repository.getSession()
                .flatMap(this::restoreIfNone)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> dispatch(ref, s.getNextStep()), e -> onSessionError(ref, ApiException.from(e)));
    }

    @NonNull
    private Single<SessionVO> restoreIfNone(@NonNull SessionVO s) {
        if (s.getUser() != null) {
            sessionManager.updateUser(s.getUser());
        }
        String step = s.getNextStep();
        if (step != null && !AuthTokenVO.NEXT_NONE.equals(step)) {
            return Single.just(s);
        }
        return repository.restoreFarm(s.getLastFarmId()).toSingleDefault(s);
    }

    private void dispatch(@NonNull WeakReference<Activity> ref, @Nullable String nextStep) {
        Activity a = ref.get();
        if (a == null || a.isFinishing()) {
            openFromApp(NextStepRouter.actionOf(nextStep));
            return;
        }
        NextStepRouter.dispatch(a, nextStep);
    }

    private void onSessionError(@NonNull WeakReference<Activity> ref, @NonNull ApiException e) {
        Log.w(TAG, "冷启动会话检查失败：" + e.getCode());
        if (e.getCode() == ErrorCodes.SESSION_INVALID || !sessionManager.isLoggedIn()) {
            // 40104：网络层已清除会话并发出“需要重新登录”，BaseApplication 在前台页面打开 A01（带原因）
            return;
        }
        if (e.getCode() == ErrorCodes.FORCED_STEP_REQUIRED) {
            dispatch(ref, e.dataString("next_step"));
            return;
        }
        Activity activity = ref.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (!(activity instanceof FragmentActivity)) {
            NextStepRouter.openLogin(activity, null, e.getMessage());
            return;
        }
        FragmentActivity host = (FragmentActivity) activity;
        Runnable showError = () -> {
            ConfirmDialogFragment dialog = ConfirmDialogFragment.builder(REQUEST_STARTUP_RETRY)
                    .title(host.getString(R.string.account_startup_failed_title))
                    .message(host.getString(R.string.account_startup_failed_message, e.getMessage()))
                    .positive(host.getString(R.string.account_startup_retry))
                    .negative(host.getString(R.string.account_startup_relogin))
                    .cancelOnTouchOutside(false)
                    .build();
            dialog.setCancelable(false);
            dialog.showSafely(host.getSupportFragmentManager());
        };
        if (host.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                && !host.getSupportFragmentManager().isStateSaved()) {
            showError.run();
        } else {
            host.getLifecycle().addObserver(new LifecycleEventObserver() {
                @Override
                public void onStateChanged(@NonNull LifecycleOwner owner, @NonNull Lifecycle.Event event) {
                    if (event == Lifecycle.Event.ON_RESUME) {
                        owner.getLifecycle().removeObserver(this);
                        if (!host.isFinishing()) {
                            showError.run();
                        }
                    } else if (event == Lifecycle.Event.ON_DESTROY) {
                        owner.getLifecycle().removeObserver(this);
                    }
                }
            });
        }
    }

    private void openFromApp(@NonNull String action) {
        Intent intent = new Intent(action).setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            context.startActivity(intent);
        } catch (RuntimeException ex) {
            Log.w(TAG, "无法打开页面：" + action, ex);
        }
    }

    @Override
    public void openNextStep(@NonNull Activity from, @NonNull String nextStep) {
        String action = NextStepRouter.actionOf(nextStep);
        if (RouterPath.MAIN.equals(action)) {
            return;
        }
        Intent current = from.getIntent();
        if (current != null && action.equals(current.getAction())) {
            return;
        }
        NextStepRouter.dispatch(from, nextStep);
    }

    @Override
    public void logout() {
        //noinspection ResultOfMethodCallIgnored
        repository.logoutAndClear()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> NextStepRouter.openLogin(context, null, "已退出登录"),
                        e -> NextStepRouter.openLogin(context, null, "已退出登录"));
    }
}
