package com.zhimu.slmp.account.data;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.account.model.request.AccountCancelRequest;
import com.zhimu.slmp.account.model.request.AccountRecoveryRequest;
import com.zhimu.slmp.account.model.request.AccountSmsCodeRequest;
import com.zhimu.slmp.account.model.request.AfterRecoveryPasswordRequest;
import com.zhimu.slmp.account.model.request.AvatarBindRequest;
import com.zhimu.slmp.account.model.request.PasswordChangeRequest;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.account.model.request.PasswordResetRequest;
import com.zhimu.slmp.account.model.request.ProfileUpdateRequest;
import com.zhimu.slmp.account.model.request.RecoveryCodeRegenerateRequest;
import com.zhimu.slmp.account.model.request.RegisterRequest;
import com.zhimu.slmp.account.model.request.SmsCodeSendRequest;
import com.zhimu.slmp.account.model.request.SmsLoginRequest;
import com.zhimu.slmp.account.model.vo.AccountOverviewVO;
import com.zhimu.slmp.account.model.vo.AccountRecoverySubmitVO;
import com.zhimu.slmp.account.model.vo.AgreementVO;
import com.zhimu.slmp.account.model.vo.CancellationPreviewVO;
import com.zhimu.slmp.account.model.vo.ProfileVO;
import com.zhimu.slmp.account.model.vo.RecoveryCodeConfirmVO;
import com.zhimu.slmp.account.model.vo.SmsCodeSendVO;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;
import com.zhimu.slmp.common.model.vo.SessionVO;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.di.NetworkModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * 模块01 账号仓库：封装 AccountApi（Retrofit 已在 io 线程执行）；登录类接口（A03、A04、A05、A11、A17）成功后
 * 先交 SessionManager.saveToken，再把结果交给调用方。
 * 短信验证码测试直连开启时（debug 包，见 {@link DirectSmsGateway}），A01、A02 改为直连阿里云发送，
 * 带验证码的提交（A03、A05、A09、A10、A13、A20）先由阿里云校验再调原接口；关闭时与原来完全一致。
 */
@FeatureScope
public class AccountRepository {

    /** 上传头像文件的 scene（服务端按此校验图片类型与大小） */
    public static final String SCENE_AVATAR = "AVATAR";

    private static final String TAG = "AccountRepository";

    /** 退出登录时等待 API-A07 的最长秒数 */
    static final long LOGOUT_API_TIMEOUT_SECONDS = 5;

    private final AccountApi api;
    /** 上传 Retrofit 创建的同一接口（写入超时 120 秒，头像文件可能较大） */
    private final AccountApi uploadApi;
    private final SessionManager sessionManager;
    private final FarmContext farmContext;
    private final DirectSmsGateway directSms;
    private final Context context;

    @Inject
    public AccountRepository(@NonNull AccountApi api,
                             @Named(NetworkModule.UPLOAD) @NonNull AccountApi uploadApi,
                             @NonNull SessionManager sessionManager,
                             @NonNull FarmContext farmContext, @NonNull DirectSmsGateway directSms,
                             @NonNull Context context) {
        this.api = api;
        this.uploadApi = uploadApi;
        this.sessionManager = sessionManager;
        this.farmContext = farmContext;
        this.directSms = directSms;
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
    }

    /** API-A01（直连开启时由阿里云发送） */
    @NonNull
    public Single<SmsCodeSendVO> sendSmsCode(@NonNull SmsCodeSendRequest request) {
        if (directSms.isEnabled()) {
            return directSms.send(request.getPhone(), request.getScene());
        }
        return api.sendSmsCode(request).compose(RxResult.unwrap());
    }

    /** API-A02（直连开启且能确定本人手机号时由阿里云发送） */
    @NonNull
    public Single<SmsCodeSendVO> sendAccountSmsCode(@NonNull AccountSmsCodeRequest request) {
        String phone = directSms.accountPhone();
        if (phone != null) {
            return directSms.send(phone, request.getScene());
        }
        return api.sendAccountSmsCode(request).compose(RxResult.unwrap());
    }

    /** API-A03：成功后保存会话 */
    @NonNull
    public Single<AuthTokenVO> register(@NonNull RegisterRequest request, @NonNull String idemKey) {
        return directSms.verifyThen(request.getPhone(), request.getSmsCode(),
                        code -> api.register(request.withSmsCode(code), idemKey).compose(RxResult.unwrap()))
                .doOnSuccess(sessionManager::saveToken);
    }

    /** API-A04：成功后保存会话 */
    @NonNull
    public Single<AuthTokenVO> loginByPassword(@NonNull PasswordLoginRequest request) {
        return api.loginByPassword(request).compose(RxResult.unwrap()).doOnSuccess(sessionManager::saveToken);
    }

    /** API-A05：成功后保存会话 */
    @NonNull
    public Single<AuthTokenVO> loginBySms(@NonNull SmsLoginRequest request) {
        return directSms.verifyThen(request.getPhone(), request.getSmsCode(),
                        code -> api.loginBySms(request.withSmsCode(code)).compose(RxResult.unwrap()))
                .doOnSuccess(sessionManager::saveToken);
    }

    /** API-A07 */
    @NonNull
    public Completable logout() {
        return RxResult.completable(api.logout());
    }

    /**
     * 退出登录（2.6.7）：退出前回调（IPushService.unbindCurrent，删除别名并调 API-T21；服务未注册时跳过）
     * → API-A07（失败或超过 {@link #LOGOUT_API_TIMEOUT_SECONDS} 秒不阻断）→ SessionManager.logout
     * 清除本机令牌、用户、养殖场上下文与 Room 三张表。服务端不可用时也能尽快回到登录页。
     */
    @NonNull
    public Completable logoutAndClear() {
        return Completable.fromAction(() -> {
                    IPushService push = ServiceHub.get(IPushService.class);
                    if (push != null) {
                        push.unbindCurrent();
                    }
                })
                .onErrorComplete()
                .andThen(logout().timeout(LOGOUT_API_TIMEOUT_SECONDS, TimeUnit.SECONDS).onErrorComplete())
                .andThen(Completable.fromAction(sessionManager::logout));
    }

    /** API-A08 */
    @NonNull
    public Single<SessionVO> getSession() {
        return api.getSession().compose(RxResult.unwrap());
    }

    /** API-A09 */
    @NonNull
    public Completable resetPassword(@NonNull PasswordResetRequest request, @NonNull String idemKey) {
        return directSms.verifyThenComplete(request.getPhone(), request.getSmsCode(),
                code -> RxResult.completable(api.resetPassword(request.withSmsCode(code), idemKey)));
    }

    /** API-A10 */
    @NonNull
    public Single<AccountRecoverySubmitVO> submitRecovery(@NonNull AccountRecoveryRequest request,
                                                          @NonNull String idemKey) {
        return directSms.verifyThen(request.getNewPhone(), request.getNewPhoneSmsCode(),
                code -> api.submitRecovery(request.withNewPhoneSmsCode(code), idemKey).compose(RxResult.unwrap()));
    }

    /** API-A11：成功后先保存新令牌 */
    @NonNull
    public Single<AuthTokenVO> changePasswordAfterRecovery(@NonNull AfterRecoveryPasswordRequest request,
                                                           @NonNull String idemKey) {
        return api.changePasswordAfterRecovery(request, idemKey).compose(RxResult.unwrap())
                .doOnSuccess(sessionManager::saveToken);
    }

    /** API-A12 */
    @NonNull
    public Single<RecoveryCodeVO> regeneratePendingCode() {
        return api.regeneratePendingCode().compose(RxResult.unwrap());
    }

    /** API-A13 */
    @NonNull
    public Single<RecoveryCodeVO> regenerateCode(@NonNull RecoveryCodeRegenerateRequest request,
                                                 @NonNull String idemKey) {
        return directSms.verifyThen(directSms.accountPhone(), request.getSmsCode(),
                code -> api.regenerateCode(request.withSmsCode(code), idemKey).compose(RxResult.unwrap()));
    }

    /** API-A14 */
    @NonNull
    public Single<RecoveryCodeConfirmVO> confirmRecoveryCode(long recoveryCodeId) {
        return api.confirmRecoveryCode(recoveryCodeId).compose(RxResult.unwrap());
    }

    /** API-A15 */
    @NonNull
    public Single<AccountOverviewVO> getOverview() {
        return api.getOverview().compose(RxResult.unwrap());
    }

    /** API-A16 */
    @NonNull
    public Single<ProfileVO> updateProfile(@NonNull ProfileUpdateRequest request) {
        return api.updateProfile(request).compose(RxResult.unwrap());
    }

    /**
     * 上传并绑定头像：把系统图片选择器返回的 Uri 读成缓存临时文件 →
     * POST api/v1/files?scene=AVATAR（multipart file）→ PUT api/v1/account/avatar {"file_id":id}。
     * 读取、上传、绑定都在订阅线程（ViewModel 的 io 调度器）执行；临时文件在结束（成功或失败）后删除。
     */
    @NonNull
    public Single<AccountUserVO> uploadAvatar(@NonNull Uri uri) {
        return Single.defer(() -> {
            PreparedAvatar prepared = prepareAvatar(uri);
            return uploadApi.uploadAvatar(prepared.part, SCENE_AVATAR)
                    .compose(RxResult.unwrap())
                    .flatMap(file -> {
                        long fileId = file == null ? 0 : file.resolvedFileId();
                        if (fileId <= 0) {
                            return Single.error(new ApiException(ErrorCodes.EMPTY_DATA,
                                    "头像上传失败：上传结果缺少文件 ID"));
                        }
                        return api.bindAvatar(new AvatarBindRequest(fileId)).compose(RxResult.unwrap());
                    })
                    .doFinally(prepared::delete);
        });
    }

    /** 恢复默认头像：DELETE api/v1/account/avatar，返回更新后的当前用户 */
    @NonNull
    public Single<AccountUserVO> clearAvatar() {
        return api.clearAvatar().compose(RxResult.unwrap());
    }

    /** API-A17：成功后先保存新令牌 */
    @NonNull
    public Single<AuthTokenVO> changePassword(@NonNull PasswordChangeRequest request, @NonNull String idemKey) {
        return api.changePassword(request, idemKey).compose(RxResult.unwrap()).doOnSuccess(sessionManager::saveToken);
    }

    /** API-A19 */
    @NonNull
    public Single<CancellationPreviewVO> getCancellationPreview() {
        return api.getCancellationPreview().compose(RxResult.unwrap());
    }

    /** API-A20：成功后清除本机全部数据（含解绑推送） */
    @NonNull
    public Completable cancelAccount(@NonNull AccountCancelRequest request, @NonNull String idemKey) {
        return directSms.verifyThenComplete(directSms.accountPhone(), request.getSmsCode(),
                        code -> RxResult.completable(api.cancelAccount(request.withSmsCode(code), idemKey)))
                .andThen(Completable.fromAction(sessionManager::logout));
    }

    /** API-A21 */
    @NonNull
    public Single<AgreementVO> getAgreement() {
        return api.getAgreement().compose(RxResult.unwrap());
    }

    /**
     * 按 last_farm_id 恢复当前养殖场：IFarmContextService.switchTo（模块02，API-F03）；
     * 服务未注册时直接调 API-F02 写入 FarmContext；没有养殖场时清空上下文。
     */
    @NonNull
    public Completable restoreFarm(@Nullable Long lastFarmId) {
        if (lastFarmId == null || lastFarmId <= 0) {
            return Completable.fromAction(farmContext::clear);
        }
        IFarmContextService farmService = ServiceHub.get(IFarmContextService.class);
        if (farmService != null) {
            return farmService.switchTo(lastFarmId).ignoreElement();
        }
        return api.getFarmContext(lastFarmId).compose(RxResult.unwrap())
                .doOnSuccess(farmContext::update)
                .ignoreElement();
    }

    // ===== 头像文件 =====

    /**
     * 把图片 Uri 复制到 cache 下的临时文件，并构造 multipart 的 file 部分。
     * 图片 MIME 取 ContentResolver.getType；取不到或不是图片时按 image/jpeg 处理。
     */
    @NonNull
    private PreparedAvatar prepareAvatar(@NonNull Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String mime = normalizeImageMime(resolver.getType(uri));
        File dir = context.getCacheDir();
        if (dir == null) {
            throw new IOException("无法创建头像临时文件");
        }
        File file = File.createTempFile("avatar_", "." + extensionOf(mime), dir);
        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = new FileOutputStream(file)) {
            if (in == null) {
                throw new IOException("无法读取所选图片");
            }
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            throw e;
        }
        if (file.length() <= 0) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            throw new IOException("所选图片为空");
        }
        return new PreparedAvatar(file, mime);
    }

    @NonNull
    private static String normalizeImageMime(@Nullable String mime) {
        if (mime == null || !mime.startsWith("image/") || "image/*".equals(mime)) {
            return "image/jpeg";
        }
        return mime;
    }

    @NonNull
    private static String extensionOf(@NonNull String mime) {
        switch (mime) {
            case "image/png":
                return "png";
            case "image/webp":
                return "webp";
            case "image/gif":
                return "gif";
            case "image/heic":
            case "image/heif":
                return "heic";
            default:
                return "jpg";
        }
    }

    /** 临时头像文件与对应的 multipart 部分；结束时删除临时文件 */
    private static final class PreparedAvatar {

        private final File file;
        private final MultipartBody.Part part;

        PreparedAvatar(@NonNull File file, @NonNull String mime) {
            this.file = file;
            MediaType mediaType = MediaType.parse(mime);
            if (mediaType == null) {
                mediaType = MediaType.parse("image/jpeg");
            }
            this.part = MultipartBody.Part.createFormData("file", file.getName(),
                    RequestBody.create(file, mediaType));
        }

        void delete() {
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "头像临时文件删除失败：" + file.getName());
            }
        }
    }
}
