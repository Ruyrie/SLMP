package com.zhimu.slmp.account.ui.register;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.RegisterRequest;
import com.zhimu.slmp.account.model.request.SmsCodeSendRequest;
import com.zhimu.slmp.account.model.vo.AgreementItemVO;
import com.zhimu.slmp.account.model.vo.AgreementVO;
import com.zhimu.slmp.account.ui.base.AccountFormViewModel;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.DeviceInfoProvider;
import com.zhimu.slmp.account.util.PhoneFormatWatcher;
import com.zhimu.slmp.account.util.RecoveryCodeHolder;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * A02 注册账号（2.4.2 节）：进入时取协议版本（API-A21），发码（API-A01 REGISTER），注册（API-A03）；
 * 成功后会话已保存，恢复码交 RecoveryCodeHolder，打开 A04。
 */
public class RegisterViewModel extends AccountFormViewModel {

    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_SMS_CODE = "sms_code";
    public static final String FIELD_PASSWORD = "password";
    public static final String FIELD_AGREE_TERMS = "agree_terms";

    public static final String TYPE_USER = "USER";
    public static final String TYPE_PRIVACY = "PRIVACY";

    /** Snackbar 操作：去登录（回 A01） */
    public static final String ACTION_LOGIN = "login";
    public static final String MSG_GO_LOGIN = "去登录";
    public static final String MSG_AGREEMENT_UPDATED = "协议已更新，请重新阅读并同意";

    private final AccountRepository repository;
    private final DeviceInfoProvider deviceInfo;
    private final RecoveryCodeHolder codeHolder;

    public final MutableLiveData<String> phone = new MutableLiveData<>("");
    public final MutableLiveData<String> smsCode = new MutableLiveData<>("");
    public final MutableLiveData<String> password = new MutableLiveData<>("");
    public final MutableLiveData<Boolean> agreeTerms = new MutableLiveData<>(false);
    private final MutableLiveData<AgreementVO> agreement = new MutableLiveData<>(null);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<String> navigate = new SingleLiveEvent<>();
    private final SingleLiveEvent<AgreementItemVO> openAgreement = new SingleLiveEvent<>();

    @Inject
    public RegisterViewModel(@NonNull AccountRepository repository, @NonNull DeviceInfoProvider deviceInfo,
                             @NonNull RecoveryCodeHolder codeHolder) {
        this.repository = repository;
        this.deviceInfo = deviceInfo;
        this.codeHolder = codeHolder;
        canSubmit.setValue(false);
        canSubmit.addSource(phone, v -> onFieldChanged(FIELD_PHONE));
        canSubmit.addSource(smsCode, v -> onFieldChanged(FIELD_SMS_CODE));
        canSubmit.addSource(password, v -> onFieldChanged(FIELD_PASSWORD));
        canSubmit.addSource(agreeTerms, v -> onFieldChanged(FIELD_AGREE_TERMS));
        canSubmit.addSource(submitting, v -> updateCanSubmit());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        String p = handle.get(RouterPath.EXTRA_PHONE);
        if (p != null) {
            phone.setValue(PhoneFormatWatcher.format(p));
        }
        loadAgreement();
    }

    // ===== 对外 LiveData =====

    public LiveData<AgreementVO> getAgreement() {
        return agreement;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<String> getNavigate() {
        return navigate;
    }

    public SingleLiveEvent<AgreementItemVO> getOpenAgreement() {
        return openAgreement;
    }

    private void onFieldChanged(@NonNull String field) {
        clearFieldError(field);
        onSubmitFieldChanged();
        updateCanSubmit();
    }

    private void updateCanSubmit() {
        Boolean value = !AccountForm.isBlank(phone.getValue()) && !AccountForm.isBlank(smsCode.getValue())
                && !AccountForm.isBlank(password.getValue()) && !isSubmitting();
        if (!value.equals(canSubmit.getValue())) {
            canSubmit.setValue(value);
        }
    }

    // ===== 方法 =====

    /** API-A21：进入时调用；失败时注册前重试 */
    public void loadAgreement() {
        launch(repository.getAgreement(), agreement::setValue, e -> {
            // 进入时失败不提示，注册前重试
        });
    }

    public void sendSmsCode() {
        if (sms.isCounting()) {
            return;
        }
        Map<String, String> errors = new FormValidator().phone(FIELD_PHONE, phone.getValue()).validate();
        if (!applyValidation(errors)) {
            return;
        }
        SmsCodeSendRequest req = new SmsCodeSendRequest(AccountForm.phone(phone.getValue()),
                SmsCodeSendRequest.SCENE_REGISTER);
        launch(repository.sendSmsCode(req), vo -> onSmsSent(vo, FIELD_SMS_CODE), this::handleRegisterError);
    }

    /** 本地校验：手机号、6 位验证码、新密码规则、必须勾选协议 */
    @NonNull
    Map<String, String> validate() {
        FormValidator v = new FormValidator()
                .phone(FIELD_PHONE, phone.getValue())
                .smsCode(FIELD_SMS_CODE, smsCode.getValue());
        AccountForm.newPassword(v, FIELD_PASSWORD, password.getValue());
        v.check(FIELD_AGREE_TERMS, Boolean.TRUE.equals(agreeTerms.getValue()), AccountForm.MSG_AGREE_TERMS);
        return v.validate();
    }

    public void register() {
        if (isSubmitting()) {
            return;
        }
        if (!applyValidation(validate())) {
            return;
        }
        String key = obtainIdemKey();
        submitting.setValue(true);
        AgreementVO current = agreement.getValue();
        Single<AgreementVO> agreementSource = current != null && current.getAgreementVersion() != null
                ? Single.just(current)
                : repository.getAgreement().doOnSuccess(agreement::postValue);
        Single<AuthTokenVO> source = agreementSource.flatMap(a -> repository.register(new RegisterRequest(
                AccountForm.phone(phone.getValue()), AccountForm.trim(smsCode.getValue()),
                AccountForm.text(password.getValue()), true, AccountForm.text(a.getAgreementVersion()),
                deviceInfo.get()), key));
        launch(source, token -> {
            onSubmitFinished(null);
            codeHolder.put(token.getRecoveryCode());
            navigate.setValue(RouterPath.A04);
        }, e -> {
            onSubmitFinished(e);
            handleRegisterError(e);
        });
    }

    private void handleRegisterError(@NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.PHONE_REGISTERED) {
            setFieldError(FIELD_PHONE, e.getMessage());
            // 两种 40904 没有 data.reason：文案为“已经注册”时才提供“去登录”
            if (e.getMessage().contains("已经注册")) {
                showMessage(UiMessage.withAction(e.getMessage(), MSG_GO_LOGIN, ACTION_LOGIN));
            }
            return;
        }
        if (code == ErrorCodes.PARAM_INVALID && isAgreementUpdated(e)) {
            agreeTerms.setValue(false);
            agreement.setValue(null);
            loadAgreement();
            setFieldError(FIELD_AGREE_TERMS, MSG_AGREEMENT_UPDATED);
            return;
        }
        if (handleSmsError(e, FIELD_SMS_CODE)) {
            return;
        }
        handleError(e);
    }

    private static boolean isAgreementUpdated(@NonNull ApiException e) {
        return e.getFieldErrors().containsKey("agreement_version") || e.getMessage().contains("协议已更新");
    }

    /** 点《用户协议》（USER）或《隐私政策》（PRIVACY） */
    public void onAgreementClick(@NonNull String type) {
        AgreementVO a = agreement.getValue();
        AgreementItemVO item = a == null ? null : (TYPE_PRIVACY.equals(type) ? a.getPrivacyPolicy() : a.getUserAgreement());
        if (item == null) {
            loadAgreement();
            showMessage(AccountForm.MSG_NETWORK);
            return;
        }
        openAgreement.setValue(item);
    }

    @Nullable
    public String phoneDigits() {
        return AccountForm.phone(phone.getValue());
    }
}
