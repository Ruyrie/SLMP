package com.zhimu.slmp.today.push;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.today.BuildConfig;
import com.zhimu.slmp.today.data.PushDeviceRepository;
import com.zhimu.slmp.today.model.request.PushDeviceRequest;

import javax.inject.Inject;

import cn.jiguang.api.utils.JCollectionAuth;
import cn.jpush.android.api.JPushInterface;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * IPushService 实现（极光 JPush 6.0.1，技术文档 2.7.1）：
 * <ul>
 * <li>init：用户同意隐私政策后调用；先创建三个通知渠道，AppKey 为空时不初始化极光；否则授权采集并初始化，已登录时上报设备；</li>
 * <li>syncDevice：API-T20 上报 RegistrationID、品牌、型号、系统与 App 版本、通知权限，参数未变不重复上报；
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
    public void syncDevice() {
        if (!jpushReady || !sessionManager.isLoggedIn()) {
            return;
        }
        String regId = JPushInterface.getRegistrationID(context);
        if (TextUtils.isEmpty(regId)) {
            // 注册完成后 ZhimuPushReceiver.onRegister 再次调用
            return;
        }
        PushDeviceRequest req = new PushDeviceRequest(regId, Build.BRAND, Build.MODEL,
                "Android " + Build.VERSION.RELEASE, BuildConfig.APP_VERSION_NAME,
                NotificationManagerCompat.from(context).areNotificationsEnabled());
        if (req.equals(lastReported)) {
            return;
        }
        // 结果不影响界面，订阅随请求结束释放
        //noinspection ResultOfMethodCallIgnored
        repository.register(req)
                .subscribeOn(Schedulers.io())
                .subscribe(vo -> lastReported = req, t -> Log.w(TAG, "推送设备登记失败：" + t.getMessage()));
    }

    @Override
    public void unbindCurrent() {
        lastReported = null;
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
}
