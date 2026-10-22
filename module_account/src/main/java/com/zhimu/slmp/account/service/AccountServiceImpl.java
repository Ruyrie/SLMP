package com.zhimu.slmp.account.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.util.NextStepRouter;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
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
 * NONE 时按 last_farm_id 恢复当前养殖场（IFarmContextService.switchTo，未注册时直接调 API-F02）后进入 MainActivity；
 * 网络失败时用本机 user_json 与 farm_context_json 进入 MainActivity，强制步骤由服务端 40309 兜底；</li>
 * <li>openNextStep：40309 的 next_step（BaseApplication 转发）；</li>
 * <li>logout：IPushService.unbindCurrent → API-A07（失败不阻断）→ 清除会话 → 回到 A01。</li>
 * </ul>
 */
@FeatureScope
public class AccountServiceImpl implements IAccountService {

    private static final String TAG = "AccountService";

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
        return repository.restoreFarm(s.getLastFarmId()).onErrorComplete().toSingleDefault(s);
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
        // 网络失败等：用本机缓存进入主页
        dispatch(ref, AuthTokenVO.NEXT_NONE);
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
