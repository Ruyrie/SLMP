package com.zhimu.slmp.common.session;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.RecoveryInfoVO;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.auth.TokenProvider;
import com.zhimu.slmp.network.model.ApiResponse;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * 会话管理（模块01 2.6.1 节）：保存与刷新令牌、当前用户、登录状态；实现 lib_network 的 TokenProvider。
 * <ul>
 * <li>saveToken：A03、A04、A05、A11、A17 成功后调用</li>
 * <li>clear(reason)：任意接口 40104 或刷新失败时调用，清除令牌、FarmContext 与 Room 三张表，发出“需要重新登录”</li>
 * <li>logout：用户主动退出（A07），清除同上但不发事件，由调用方跳转登录页</li>
 * </ul>
 */
@Singleton
public class SessionManager implements TokenProvider {

    private static final String TAG = "SessionManager";

    private final SessionStore store;
    private final FarmContext farmContext;
    private final AppDatabase database;
    private final Gson gson;
    private final Provider<SessionApi> sessionApi;

    private final MutableLiveData<AccountUserVO> user;
    private final MutableLiveData<Boolean> loginState;

    @Nullable
    private volatile String accessToken;
    @Nullable
    private volatile String refreshToken;
    @Nullable
    private volatile RecoveryInfoVO recoveryInfo;
    private final Object refreshLock = new Object();

    @Inject
    public SessionManager(@NonNull SessionStore store, @NonNull FarmContext farmContext,
                          @NonNull AppDatabase database, @NonNull Gson gson,
                          @NonNull Provider<SessionApi> sessionApi) {
        this.store = store;
        this.farmContext = farmContext;
        this.database = database;
        this.gson = gson;
        this.sessionApi = sessionApi;
        this.accessToken = store.getAccessToken();
        this.refreshToken = store.getRefreshToken();
        // 可能在非主线程首次创建，用构造方法赋初值
        this.user = new MutableLiveData<>(parseUser(store.getUserJson()));
        this.loginState = new MutableLiveData<>(refreshToken != null);
    }

    // ===== 状态 =====

    /** 当前用户；未登录为 null */
    public LiveData<AccountUserVO> getUser() {
        return user;
    }

    @Nullable
    public AccountUserVO currentUser() {
        return user.getValue();
    }

    /** 是否已登录（本机保存有刷新令牌） */
    public LiveData<Boolean> getLoginState() {
        return loginState;
    }

    public boolean isLoggedIn() {
        return refreshToken != null;
    }

    /** 须先完成的强制步骤（40309 的 next_step），由 GlobalEvents 转发 */
    public LiveData<String> getForcedStep() {
        return GlobalEvents.forcedStep();
    }

    /** 最近一次登录返回的找回信息（只在内存） */
    @Nullable
    public RecoveryInfoVO getRecoveryInfo() {
        return recoveryInfo;
    }

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }

    // ===== 写入 =====

    /** 保存登录结果：令牌加密写入 zmt_session，user 写入 user_json */
    public void saveToken(@NonNull AuthTokenVO token) {
        accessToken = token.getAccessToken();
        if (token.getRefreshToken() != null) {
            refreshToken = token.getRefreshToken();
        }
        store.saveTokens(token.getAccessToken(), token.getRefreshToken());
        if (token.getUser() != null) {
            updateUser(token.getUser());
        }
        if (token.getRecoveryInfo() != null) {
            recoveryInfo = token.getRecoveryInfo();
        }
        publish(loginState, refreshToken != null);
    }

    public void updateUser(@NonNull AccountUserVO newUser) {
        store.saveUserJson(gson.toJson(newUser));
        publish(user, newUser);
    }

    /** 用户主动退出：清除本机登录态（调用方先调 API-A07，失败不阻断） */
    public void logout() {
        clearInternal();
    }

    /** 登录失效：清除本机登录态并发出“需要重新登录”事件，reason 为 40104 的 data.reason */
    public void clear(@Nullable String reason) {
        boolean wasLoggedIn = isLoggedIn();
        clearInternal();
        if (wasLoggedIn) {
            GlobalEvents.postRelogin(reason);
        }
    }

    private void clearInternal() {
        IPushService push = ServiceHub.get(IPushService.class);
        if (push != null) {
            try {
                push.unbindCurrent();
            } catch (RuntimeException e) {
                Log.w(TAG, "解绑推送失败", e);
            }
        }
        accessToken = null;
        refreshToken = null;
        recoveryInfo = null;
        store.clear();
        farmContext.clear();
        Schedulers.io().scheduleDirect(database::clearAllTables);
        publish(user, null);
        publish(loginState, false);
    }

    // ===== TokenProvider =====

    @Nullable
    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Nullable
    @Override
    public String refreshBlocking() throws IOException {
        synchronized (refreshLock) {
            String rt = refreshToken;
            if (rt == null) {
                return null;
            }
            JsonObject body = new JsonObject();
            body.addProperty("refresh_token", rt);
            Response<ApiResponse<AuthTokenVO>> resp = sessionApi.get().refresh(body).execute();
            ApiException error;
            if (resp.isSuccessful() && resp.body() != null) {
                ApiResponse<AuthTokenVO> r = resp.body();
                if (r.isSuccess() && r.getData() != null && r.getData().getAccessToken() != null) {
                    saveToken(r.getData());
                    return r.getData().getAccessToken();
                }
                error = RxResult.fromResponse(r);
            } else {
                ResponseBody eb = resp.errorBody();
                error = RxResult.parseErrorBody(resp.code(), eb == null ? null : eb.string(),
                        resp.headers().get("X-Trace-Id"), null);
            }
            if (error.getCode() == ErrorCodes.SESSION_INVALID) {
                clear(error.dataString("reason"));
                return null;
            }
            throw new IOException("刷新令牌失败：" + error.getCode() + " " + error.getMessage());
        }
    }

    @Override
    public void onSessionInvalid(@Nullable String reason) {
        clear(reason);
    }

    // ===== 工具 =====

    @Nullable
    private AccountUserVO parseUser(@Nullable String json) {
        if (json == null) {
            return null;
        }
        try {
            return gson.fromJson(json, AccountUserVO.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static <T> void publish(MutableLiveData<T> live, T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            live.setValue(value);
        } else {
            live.postValue(value);
        }
    }
}
