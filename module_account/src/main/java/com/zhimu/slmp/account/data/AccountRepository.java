package com.zhimu.slmp.account.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.account.model.request.AccountCancelRequest;
import com.zhimu.slmp.account.model.request.AccountRecoveryRequest;
import com.zhimu.slmp.account.model.request.AccountSmsCodeRequest;
import com.zhimu.slmp.account.model.request.AfterRecoveryPasswordRequest;
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
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;
import com.zhimu.slmp.common.model.vo.SessionVO;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.network.RxResult;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 模块01 账号仓库：封装 AccountApi（Retrofit 已在 io 线程执行）；登录类接口（A03、A04、A05、A11、A17）成功后
 * 先交 SessionManager.saveToken，再把结果交给调用方。
 */
@FeatureScope
public class AccountRepository {

    private final AccountApi api;
    private final SessionManager sessionManager;
    private final FarmContext farmContext;

    @Inject
    public AccountRepository(@NonNull AccountApi api, @NonNull SessionManager sessionManager,
                             @NonNull FarmContext farmContext) {
        this.api = api;
        this.sessionManager = sessionManager;
        this.farmContext = farmContext;
    }

    /** API-A01 */
    @NonNull
    public Single<SmsCodeSendVO> sendSmsCode(@NonNull SmsCodeSendRequest request) {
        return api.sendSmsCode(request).compose(RxResult.unwrap());
    }

    /** API-A02 */
    @NonNull
    public Single<SmsCodeSendVO> sendAccountSmsCode(@NonNull AccountSmsCodeRequest request) {
        return api.sendAccountSmsCode(request).compose(RxResult.unwrap());
    }

    /** API-A03：成功后保存会话 */
    @NonNull
    public Single<AuthTokenVO> register(@NonNull RegisterRequest request, @NonNull String idemKey) {
        return api.register(request, idemKey).compose(RxResult.unwrap()).doOnSuccess(sessionManager::saveToken);
    }

    /** API-A04：成功后保存会话 */
    @NonNull
    public Single<AuthTokenVO> loginByPassword(@NonNull PasswordLoginRequest request) {
        return api.loginByPassword(request).compose(RxResult.unwrap()).doOnSuccess(sessionManager::saveToken);
    }

    /** API-A05：成功后保存会话 */
    @NonNull
    public Single<AuthTokenVO> loginBySms(@NonNull SmsLoginRequest request) {
        return api.loginBySms(request).compose(RxResult.unwrap()).doOnSuccess(sessionManager::saveToken);
    }

    /** API-A07 */
    @NonNull
    public Completable logout() {
        return RxResult.completable(api.logout());
    }

    /**
     * 退出登录（2.6.7）：退出前回调（IPushService.unbindCurrent，删除别名并调 API-T21；服务未注册时跳过）
     * → API-A07（失败不阻断）→ SessionManager.logout 清除本机令牌、用户、养殖场上下文与 Room 三张表。
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
                .andThen(logout().onErrorComplete())
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
        return RxResult.completable(api.resetPassword(request, idemKey));
    }

    /** API-A10 */
    @NonNull
    public Single<AccountRecoverySubmitVO> submitRecovery(@NonNull AccountRecoveryRequest request,
                                                          @NonNull String idemKey) {
        return api.submitRecovery(request, idemKey).compose(RxResult.unwrap());
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
        return api.regenerateCode(request, idemKey).compose(RxResult.unwrap());
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
        return RxResult.completable(api.cancelAccount(request, idemKey))
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
}
