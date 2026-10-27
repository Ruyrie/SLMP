package com.zhimu.slmp.account.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.zhimu.slmp.account.BuildConfig;
import com.zhimu.slmp.account.model.request.AccountSmsCodeRequest;
import com.zhimu.slmp.account.model.request.SmsCodeSendRequest;
import com.zhimu.slmp.account.model.vo.SmsCodeSendVO;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.common.sms.AliyunSmsClient;
import com.zhimu.slmp.common.sms.AliyunSmsTemplates;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 短信验证码测试直连（2026-10-22 用户确认，服务端完成前只在 debug 包使用）：
 * <ul>
 * <li>发送：API-A01、A02 的场景改由 {@link AliyunSmsClient} 直接发送，返回与 SmsCodeSendVO 相同的界面数据
 * （打码手机号、60 秒倒计时、5 分钟有效）；</li>
 * <li>提交：先 CheckSmsVerifyCode，通过后再调原接口；不通过为 40103（ViewModel 写入验证码框）；
 * 原接口为 Mock 时把验证码换成 Mock 统一的 123456，使 Mock 规则照常命中；</li>
 * <li>A02 场景发到账号绑定的手机号：App 只有打码手机号，取 AppSettings 的 last_phone 且打码后与当前用户的
 * phone_masked 一致才直连，否则发送与提交都走原接口。</li>
 * </ul>
 * 直连关闭（release 包、未配置 Key）时所有方法都直接走原接口，行为与原来完全一致。
 */
@FeatureScope
public class DirectSmsGateway {

    /** Mock 规则统一接受的验证码（05 号统一示例数据） */
    public static final String MOCK_SMS_CODE = "123456";

    /** 原接口调用：参数为提交给原接口的验证码 */
    public interface CodeCall<T> {
        @NonNull
        Single<T> call(@NonNull String smsCode);
    }

    /** 没有返回数据的原接口调用 */
    public interface CodeAction {
        @NonNull
        Completable call(@NonNull String smsCode);
    }

    private final AliyunSmsClient client;
    private final SessionManager sessionManager;
    private final AppSettings settings;
    private final boolean mockBackend;
    /** “手机号|验证码” → 校验通过的有效期截止时间：原接口断网后重试时不再重复校验（阿里云校验通过后码可能已失效） */
    private final Map<String, Long> verified = new ConcurrentHashMap<>();

    @Inject
    public DirectSmsGateway(@NonNull AliyunSmsClient client, @NonNull SessionManager sessionManager,
                            @NonNull AppSettings settings) {
        this(client, sessionManager, settings, BuildConfig.DEBUG && BuildConfig.USE_MOCK);
    }

    @VisibleForTesting
    DirectSmsGateway(@NonNull AliyunSmsClient client, @NonNull SessionManager sessionManager,
                     @NonNull AppSettings settings, boolean mockBackend) {
        this.client = client;
        this.sessionManager = sessionManager;
        this.settings = settings;
        this.mockBackend = mockBackend;
    }

    /** 场景 → 阿里云系统模板；未知场景为 null */
    @Nullable
    public static String templateFor(@Nullable String scene) {
        if (scene == null) {
            return null;
        }
        switch (scene) {
            case SmsCodeSendRequest.SCENE_LOGIN:
            case SmsCodeSendRequest.SCENE_REGISTER:
                return AliyunSmsTemplates.LOGIN_REGISTER;
            case SmsCodeSendRequest.SCENE_RESET_PASSWORD:
                return AliyunSmsTemplates.RESET_PASSWORD;
            case SmsCodeSendRequest.SCENE_RECOVERY_NEW_PHONE:
                return AliyunSmsTemplates.BIND_NEW_PHONE;
            case AccountSmsCodeRequest.SCENE_REGENERATE_RECOVERY_CODE:
            case AccountSmsCodeRequest.SCENE_CANCEL_ACCOUNT:
                return AliyunSmsTemplates.VERIFY_BOUND_PHONE;
            default:
                return null;
        }
    }

    /** 直连是否开启（debug 包且 Key 已配置） */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    /**
     * A02 场景的直连手机号：直连开启、last_phone 是 11 位且打码后与当前用户 phone_masked 相同时返回 last_phone，
     * 否则为 null（发送与提交都走原接口）。
     */
    @Nullable
    public String accountPhone() {
        if (!isEnabled()) {
            return null;
        }
        AccountUserVO user = sessionManager.currentUser();
        String masked = user == null ? null : user.getPhoneMasked();
        String last = settings.getLastPhone();
        if (masked == null || last == null) {
            return null;
        }
        String digits = last.replaceAll("\\s", "");
        if (digits.length() != 11 || !masked.equals(NumberFormatter.maskPhone(digits))) {
            return null;
        }
        return digits;
    }

    /** 直连发送（调用方已判断 {@link #isEnabled()}） */
    @NonNull
    public Single<SmsCodeSendVO> send(@NonNull String phone, @NonNull String scene) {
        String template = templateFor(scene);
        if (template == null) {
            return Single.error(new ApiException(ErrorCodes.SERVICE_UNAVAILABLE, "验证码发送失败，请稍后重试"));
        }
        return client.sendVerifyCode(phone, template)
                .doOnSuccess(r -> forget(phone))
                .map(r -> new SmsCodeSendVO(NumberFormatter.maskPhone(phone), r.getCooldownSeconds(),
                        r.getExpireSeconds()));
    }

    /**
     * 带验证码的提交：直连开启且 phone 不为空时先校验，通过后以 {@link #backendCode(String)} 调原接口；
     * 否则直接以原验证码调原接口。
     */
    @NonNull
    public <T> Single<T> verifyThen(@Nullable String phone, @NonNull String smsCode, @NonNull CodeCall<T> call) {
        if (!isEnabled() || phone == null) {
            return call.call(smsCode);
        }
        return verify(phone, smsCode).andThen(Single.defer(() -> call.call(backendCode(smsCode))));
    }

    /** 同 {@link #verifyThen}，用于没有返回数据的接口 */
    @NonNull
    public Completable verifyThenComplete(@Nullable String phone, @NonNull String smsCode, @NonNull CodeAction call) {
        if (!isEnabled() || phone == null) {
            return call.call(smsCode);
        }
        return verify(phone, smsCode).andThen(Completable.defer(() -> call.call(backendCode(smsCode))));
    }

    /** 校验通过后提交给原接口的验证码：原接口为 Mock 时用 Mock 统一验证码，否则原样提交 */
    @NonNull
    String backendCode(@NonNull String smsCode) {
        return mockBackend ? MOCK_SMS_CODE : smsCode;
    }

    @NonNull
    private Completable verify(@NonNull String phone, @NonNull String smsCode) {
        return Completable.defer(() -> {
            String key = phone + "|" + smsCode;
            Long until = verified.get(key);
            if (until != null && until > System.currentTimeMillis()) {
                return Completable.complete();
            }
            return client.checkVerifyCode(phone, smsCode)
                    .doOnComplete(() -> verified.put(key,
                            System.currentTimeMillis() + AliyunSmsClient.VALID_SECONDS * 1000L));
        });
    }

    /** 重新发送后旧码失效，清除该号码的校验记录 */
    void forget(@NonNull String phone) {
        String prefix = phone + "|";
        verified.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
