package com.zhimu.slmp.account.data;

import com.google.gson.JsonElement;
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
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;
import com.zhimu.slmp.common.model.vo.SessionVO;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;

import io.reactivex.rxjava3.core.Single;
import okhttp3.MultipartBody;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 模块01 账号接口：API-A01–A05、A07–A17、A19–A21（A06 在 common.session.SessionApi，A18 已取消）；
 * 另含冷启动兜底用的 API-F02（模块02 的 IFarmContextService 未注册时）。
 */
public interface AccountApi {

    /** API-A01 发送验证码（未登录） */
    @POST("api/v1/sms-codes")
    Single<ApiResponse<SmsCodeSendVO>> sendSmsCode(@Body SmsCodeSendRequest body);

    /** API-A02 发送验证码到本人手机号 */
    @POST("api/v1/account/sms-codes")
    Single<ApiResponse<SmsCodeSendVO>> sendAccountSmsCode(@Body AccountSmsCodeRequest body);

    /** API-A03 注册（读取超时 60 秒） */
    @retrofit2.http.Headers(Headers.READ_TIMEOUT_60S)
    @POST("api/v1/auth/register")
    Single<ApiResponse<AuthTokenVO>> register(@Body RegisterRequest body,
                                              @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-A04 密码登录（读取超时 60 秒） */
    @retrofit2.http.Headers(Headers.READ_TIMEOUT_60S)
    @POST("api/v1/auth/login/password")
    Single<ApiResponse<AuthTokenVO>> loginByPassword(@Body PasswordLoginRequest body);

    /** API-A05 验证码登录（读取超时 60 秒） */
    @retrofit2.http.Headers(Headers.READ_TIMEOUT_60S)
    @POST("api/v1/auth/login/sms")
    Single<ApiResponse<AuthTokenVO>> loginBySms(@Body SmsLoginRequest body);

    /** API-A07 退出登录 */
    @POST("api/v1/auth/logout")
    Single<ApiResponse<JsonElement>> logout();

    /** API-A08 当前会话 */
    @GET("api/v1/auth/session")
    Single<ApiResponse<SessionVO>> getSession();

    /** API-A09 短信重置密码 */
    @POST("api/v1/auth/password-resets")
    Single<ApiResponse<JsonElement>> resetPassword(@Body PasswordResetRequest body,
                                                   @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-A10 凭恢复码提交找回申请 */
    @POST("api/v1/account-recoveries")
    Single<ApiResponse<AccountRecoverySubmitVO>> submitRecovery(@Body AccountRecoveryRequest body,
                                                                @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-A11 找回后修改密码 */
    @PUT("api/v1/account/password/after-recovery")
    Single<ApiResponse<AuthTokenVO>> changePasswordAfterRecovery(@Body AfterRecoveryPasswordRequest body,
                                                                 @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-A12 重新生成待确认恢复码 */
    @POST("api/v1/account/recovery-codes/pending")
    Single<ApiResponse<RecoveryCodeVO>> regeneratePendingCode();

    /** API-A13 短信验证后重新生成恢复码 */
    @POST("api/v1/account/recovery-codes")
    Single<ApiResponse<RecoveryCodeVO>> regenerateCode(@Body RecoveryCodeRegenerateRequest body,
                                                       @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-A14 确认恢复码已保存 */
    @POST("api/v1/account/recovery-codes/{id}/confirm")
    Single<ApiResponse<RecoveryCodeConfirmVO>> confirmRecoveryCode(@Path("id") long recoveryCodeId);

    /** API-A15 我的页汇总（X-Farm-Id 由拦截器添加） */
    @GET("api/v1/account/overview")
    Single<ApiResponse<AccountOverviewVO>> getOverview();

    /** API-A16 修改姓名 */
    @PATCH("api/v1/account/profile")
    Single<ApiResponse<ProfileVO>> updateProfile(@Body ProfileUpdateRequest body);

    /** API-A17 修改密码 */
    @PUT("api/v1/account/password")
    Single<ApiResponse<AuthTokenVO>> changePassword(@Body PasswordChangeRequest body,
                                                    @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** 上传头像文件（scene=AVATAR，multipart 字段 file），返回 FileVO；文件 ID 取 resolvedFileId() */
    @Multipart
    @POST("api/v1/files")
    Single<ApiResponse<FileVO>> uploadAvatar(@Part MultipartBody.Part file,
                                             @Query("scene") String scene);

    /** 绑定头像：body {"file_id":123}，返回更新后的当前用户 */
    @PUT("api/v1/account/avatar")
    Single<ApiResponse<AccountUserVO>> bindAvatar(@Body AvatarBindRequest body);

    /** 恢复默认头像（清除 avatar_url），返回更新后的当前用户 */
    @DELETE("api/v1/account/avatar")
    Single<ApiResponse<AccountUserVO>> clearAvatar();

    /** API-A19 注销预览 */
    @GET("api/v1/account/cancellation-preview")
    Single<ApiResponse<CancellationPreviewVO>> getCancellationPreview();

    /** API-A20 注销账号 */
    @POST("api/v1/account/cancellation")
    Single<ApiResponse<JsonElement>> cancelAccount(@Body AccountCancelRequest body,
                                                   @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-A21 当前协议与客服电话 */
    @GET("api/v1/agreements/current")
    Single<ApiResponse<AgreementVO>> getAgreement();

    /** API-F02 养殖场上下文（冷启动恢复上次养殖场的兜底，模块02 服务未注册时使用） */
    @GET("api/v1/farms/{farmId}/context")
    Single<ApiResponse<FarmContextVO>> getFarmContext(@Path("farmId") long farmId);
}
