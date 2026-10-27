package com.zhimu.slmp.today.push;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationManagerCompat;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.today.BuildConfig;
import com.zhimu.slmp.today.data.PushDeviceRepository;
import com.zhimu.slmp.today.model.request.PushDeviceRequest;

import javax.inject.Inject;

import cn.jiguang.api.utils.JCollectionAuth;
import cn.jpush.android.api.JPushInterface;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * IPushService 实现（极光 JPush 6.0.1，技术文档 2.7.1）：
 * <ul>
 * <li>init：用户同意隐私政策后调用；先创建三个通知渠道，AppKey 为空时不初始化极光；否则授权采集并初始化，已登录时上报设备；</li>
 * <li>syncDevice：API-T20 上报 RegistrationID、品牌、型号、系统与 App 版本、通知权限，同一用户成功绑定后参数未变不重复上报；
 * 别名 u{用户 ID} 由服务端绑定，App 不调用 SDK 设置别名（技术文档待定事项 2）；</li>
 * <li>unbindCurrent：退出或登录失效时 API-T21，失败只记日志、不阻塞。</li>
 * </ul>
 */
@FeatureScope
public class PushDeviceManager implements IPushService {

    private static final String TAG = "ZmtPush";

    private final Context context;
    private final PushDeviceRepository repository;
    private final SessionManager sessionManager;

    private volatile boolean jpushReady;
    @Nullable
    private volatile PushDeviceRequest lastReported;
    private long lastReportedUserId;
    @Nullable
    private PushDeviceRequest inFlight;
    private long inFlightUserId;
    private long registrationSequence;
    private long locallyClearedUserId;
    @Nullable
    private Disposable registration;

    @Inject
    public PushDeviceManager(@NonNull Context context, @NonNull PushDeviceRepository repository,
                             @NonNull SessionManager sessionManager) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.sessionManager = sessionManager;
    }

    /** 是否配置了极光 AppKey */
    public static boolean hasAppKey() {
        return !TextUtils.isEmpty(BuildConfig.JPUSH_APPKEY);
    }

    /** Application.onCreate 中调用：未同意隐私政策前不允许极光采集（SDK 的 InitProvider 随应用启动） */
    public void beforePrivacyAgreed(@NonNull Application app) {
        try {
            JCollectionAuth.setAuth(app, false);
        } catch (RuntimeException e) {
            Log.w(TAG, "极光采集授权设置失败", e);
        }
    }

    @Override
    public void init(@NonNull Application app) {
        NotificationChannels.createAll(app);
        if (!hasAppKey()) {
            Log.i(TAG, "未配置 jpush.appKey，推送不初始化");
            return;
        }
        if (jpushReady) {
            return;
        }
        JCollectionAuth.setAuth(app, true);
        JPushInterface.setDebugMode(BuildConfig.DEBUG);
        JPushInterface.init(app);
        jpushReady = true;
        syncDevice();
    }

    @Override
    public synchronized void syncDevice() {
        if (!jpushReady || !sessionManager.isLoggedIn()) {
            return;
        }
        AccountUserVO user = sessionManager.currentUser();
        if (user == null) {
            return;
        }
        long userId = user.getUserId();
        if (userId == locallyClearedUserId) return;
        resumeLocalPush();
        PushDeviceRequest req = currentDevice();
        if (req == null) {
            // 注册完成后 ZhimuPushReceiver.onRegister 再次调用
            return;
        }
        if ((userId == lastReportedUserId && req.equals(lastReported))
                || (userId == inFlightUserId && req.equals(inFlight))) {
            return;
        }
        inFlight = req;
        inFlightUserId = userId;
        long sequence = ++registrationSequence;
        // 结果不影响界面，订阅随请求结束释放
        //noinspection ResultOfMethodCallIgnored
        registration = repository.register(req)
                .subscribeOn(Schedulers.io())
                .subscribe(vo -> {
                    synchronized (PushDeviceManager.this) {
                        if (sequence != registrationSequence) {
                            return;
                        }
                        inFlight = null;
                        AccountUserVO current = sessionManager.currentUser();
                        if (vo.isAliasBound() && sessionManager.isLoggedIn()
                                && current != null && current.getUserId() == userId) {
                            lastReported = req;
                            lastReportedUserId = userId;
                        } else {
                            // API 登记成功不代表极光别名已绑定；下次登录/回到前台时必须能重试。
                            lastReported = null;
                            Log.w(TAG, "推送设备别名尚未绑定，将在下次同步时重试");
                        }
                    }
                }, t -> {
                    synchronized (PushDeviceManager.this) {
                        if (sequence == registrationSequence) {
                            inFlight = null;
                        }
                    }
                    Log.w(TAG, "推送设备登记失败：" + t.getMessage());
                });
    }

    @VisibleForTesting
    @Nullable
    PushDeviceRequest currentDevice() {
        String regId = JPushInterface.getRegistrationID(context);
        if (TextUtils.isEmpty(regId)) {
            return null;
        }
        return new PushDeviceRequest(regId, Build.BRAND, Build.MODEL,
                "Android " + Build.VERSION.RELEASE, BuildConfig.APP_VERSION_NAME,
                NotificationManagerCompat.from(context).areNotificationsEnabled());
    }

    @Override
    public synchronized void unbindCurrent() {
        clearRegistrationState();
        if (!jpushReady) {
            return;
        }
        String regId = JPushInterface.getRegistrationID(context);
        if (TextUtils.isEmpty(regId)) {
            return;
        }
        // 会话随后被清除，先取出访问令牌
        String token = sessionManager.getAccessToken();
        //noinspection ResultOfMethodCallIgnored
        repository.unbind(regId, token)
                .subscribeOn(Schedulers.io())
                .subscribe(() -> Log.i(TAG, "推送设备已解绑"), t -> Log.w(TAG, "推送设备解绑失败：" + t.getMessage()));
    }

    public boolean isJpushReady() {
        return jpushReady;
    }

    /** 注销成功后令牌已失效，只执行SDK本地清理；全设备解绑由A20服务端完成。 */
    @Override
    public synchronized void clearLocalBinding() {
        AccountUserVO user = sessionManager.currentUser();
        locallyClearedUserId = user == null ? lastReportedUserId : user.getUserId();
        clearRegistrationState();
        if (!jpushReady) return;
        // One failing SDK call must not prevent the other cleanup operations or stopPush.
        runLocalCleanup(this::deleteLocalAlias);
        runLocalCleanup(this::cleanLocalTags);
        runLocalCleanup(this::clearLocalNotifications);
        runLocalCleanup(this::stopLocalPush);
    }

    private void clearRegistrationState() {
        registrationSequence++;
        if (registration != null) registration.dispose();
        registration = null;
        lastReported = null;
        lastReportedUserId = 0;
        inFlight = null;
        inFlightUserId = 0;
    }

    private void runLocalCleanup(Runnable action) {
        try { action.run(); }
        catch (RuntimeException e) { Log.w(TAG, "本机推送清理失败", e); }
    }

    @VisibleForTesting void deleteLocalAlias() { JPushInterface.deleteAlias(context, (int) ++registrationSequence); }
    @VisibleForTesting void cleanLocalTags() { JPushInterface.cleanTags(context, (int) ++registrationSequence); }
    @VisibleForTesting void clearLocalNotifications() { JPushInterface.clearAllNotifications(context); }
    @VisibleForTesting void stopLocalPush() { JPushInterface.stopPush(context); }
    @VisibleForTesting void resumeLocalPush() {
        if (JPushInterface.isPushStopped(context)) JPushInterface.resumePush(context);
    }
}
