package com.zhimu.slmp.account.ui.login;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.account.model.request.SmsCodeSendRequest;
import com.zhimu.slmp.account.model.request.SmsLoginRequest;
import com.zhimu.slmp.account.ui.base.AccountFormViewModel;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.DeviceInfoProvider;
import com.zhimu.slmp.account.util.PhoneFormatWatcher;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.session.AppSettings;

import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * A01 登录（2.4.1 节）：密码登录（API-A04）与验证码登录（API-A05）；成功后 Repository 已保存会话，
 * next_step 为 NONE 时按 last_farm_id 恢复当前养殖场，再发 loginSuccess 由 NextStepRouter 分发。
 */
public class LoginViewModel extends AccountFormViewModel {

    public static final String MODE_PASSWORD = "PASSWORD";
    public static final String MODE_SMS = "SMS";

    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_PASSWORD = "password";
    public static final String FIELD_SMS_CODE = "sms_code";

    /** Snackbar 操作：去注册 */
    public static final String ACTION_REGISTER = "register";

    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REASON_ACCOUNT_RECOVERED = "ACCOUNT_RECOVERED";
    public static final String REASON_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String REASON_ACCOUNT_CANCELLED = "ACCOUNT_CANCELLED";

    public static final String BANNER_RELOGIN = "登录已失效。账号密码已在其他设备上修改，请重新登录。";
    public static final String BANNER_DISABLED = "这个账号已被平台停用。如有疑问，请拨打客服电话 400-123-4567。";
    public static final String MSG_CANCELLED = "账号已注销";
    public static final String MSG_PASSWORD_RESET = "密码已重置，请用新密码登录";
    public static final String MSG_GO_REGISTER = "去注册";

    private final AccountRepository repository;
    private final DeviceInfoProvider deviceInfo;
    private final AppSettings settings;

    private final MutableLiveData<String> loginMode = new MutableLiveData<>(MODE_PASSWORD);
    public final MutableLiveData<String> phone = new MutableLiveData<>("");
    public final MutableLiveData<String> password = new MutableLiveData<>("");
    public final MutableLiveData<String> smsCode = new MutableLiveData<>("");
    private final MutableLiveData<String> banner = new MutableLiveData<>(null);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<AuthTokenVO> loginSuccess = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> navigate = new SingleLiveEvent<>();

    @Inject
    public LoginViewModel(@NonNull AccountRepository repository, @NonNull DeviceInfoProvider deviceInfo,
                          @NonNull AppSettings settings) {
        this.repository = repository;
        this.deviceInfo = deviceInfo;
        this.settings = settings;
        canSubmit.setValue(false);
        canSubmit.addSource(loginMode, v -> updateCanSubmit());
        canSubmit.addSource(phone, v -> {
            clearFieldError(FIELD_PHONE);
            updateCanSubmit();
        });
        canSubmit.addSource(password, v -> {
            clearFieldError(FIELD_PASSWORD);
            updateCanSubmit();
        });
        canSubmit.addSource(smsCode, v -> {
            clearFieldError(FIELD_SMS_CODE);
            updateCanSubmit();
        });
        canSubmit.addSource(submitting, v -> updateCanSubmit());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        String extraPhone = handle.get(RouterPath.EXTRA_PHONE);
        String initial = extraPhone != null ? extraPhone : settings.getLastPhone();
        phone.setValue(PhoneFormatWatcher.format(initial));
        applyLogoutReason(handle.get(RouterPath.EXTRA_REASON));
        String msg = handle.get(RouterPath.EXTRA_MESSAGE);
        if (msg != null) {
            // 只提示一次（重建后不再提示）
            handle.remove(RouterPath.EXTRA_MESSAGE);
            showMessage(msg);
        }
    }

    /** 由 40104 的 data.reason 换算横幅或提示 */
    void applyLogoutReason(@Nullable String reason) {
        if (REASON_PASSWORD_CHANGED.equals(reason) || REASON_ACCOUNT_RECOVERED.equals(reason)) {
            banner.setValue(BANNER_RELOGIN);
        } else if (REASON_ACCOUNT_DISABLED.equals(reason)) {
            banner.setValue(BANNER_DISABLED);
        } else if (REASON_ACCOUNT_CANCELLED.equals(reason)) {
            banner.setValue(null);
            showMessage(MSG_CANCELLED);
        } else {
            banner.setValue(null);
        }
    }

    // ===== 对外 LiveData =====

    public LiveData<String> getLoginMode() {
        return loginMode;
    }

    public LiveData<String> getBanner() {
        return banner;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<AuthTokenVO> getLoginSuccess() {
        return loginSuccess;
    }

    public SingleLiveEvent<String> getNavigate() {
        return navigate;
    }

    private void updateCanSubmit() {
        boolean filled = !AccountForm.isBlank(phone.getValue())
                && (MODE_SMS.equals(loginMode.getValue())
                ? !AccountForm.isBlank(smsCode.getValue())
                : !AccountForm.isBlank(password.getValue()));
        Boolean value = filled && !isSubmitting();
        if (!value.equals(canSubmit.getValue())) {
            canSubmit.setValue(value);
        }
    }

    // ===== 方法 =====

    public void switchMode(@NonNull String mode) {
        if (mode.equals(loginMode.getValue())) {
            return;
        }
        loginMode.setValue(mode);
        fieldErrors.setValue(java.util.Collections.emptyMap());
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
                SmsCodeSendRequest.SCENE_LOGIN);
        launch(repository.sendSmsCode(req), vo -> onSmsSent(vo, FIELD_SMS_CODE), this::handleLoginError);
    }

    public void login() {
        if (isSubmitting()) {
            return;
        }
        boolean smsMode = MODE_SMS.equals(loginMode.getValue());
        FormValidator v = new FormValidator().phone(FIELD_PHONE, phone.getValue());
        if (smsMode) {
            v.smsCode(FIELD_SMS_CODE, smsCode.getValue());
        } else {
            v.required(FIELD_PASSWORD, password.getValue(), AccountForm.MSG_PASSWORD_EMPTY);
        }
        if (!applyValidation(v.validate())) {
            return;
        }
        String phoneDigits = AccountForm.phone(phone.getValue());
        Single<AuthTokenVO> source = smsMode
                ? repository.loginBySms(new SmsLoginRequest(phoneDigits, AccountForm.trim(smsCode.getValue()),
                deviceInfo.get()))
                : repository.loginByPassword(new PasswordLoginRequest(phoneDigits,
                AccountForm.text(password.getValue()), deviceInfo.get()));
        submitting.setValue(true);
        launch(source.flatMap(this::restoreFarmIfNeeded), token -> {
            submitting.setValue(false);
            settings.setLastPhone(phoneDigits);
            loginSuccess.setValue(token);
        }, e -> {
            submitting.setValue(false);
            handleLoginError(e);
        });
    }

    /** next_step 为 NONE 时按 last_farm_id 恢复当前养殖场（失败不阻断进入首页） */
    @NonNull
    private Single<AuthTokenVO> restoreFarmIfNeeded(@NonNull AuthTokenVO token) {
        String step = token.getNextStep();
        if (step != null && !AuthTokenVO.NEXT_NONE.equals(step)) {
            return Single.just(token);
        }
        return repository.restoreFarm(token.getLastFarmId()).onErrorComplete().toSingleDefault(token);
    }

    private void handleLoginError(@NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.PARAM_INVALID
                && AccountForm.REASON_PHONE_NOT_REGISTERED.equals(e.dataString("reason"))) {
            setFieldError(FIELD_PHONE, AccountForm.MSG_PHONE_NOT_REGISTERED);
            showMessage(UiMessage.withAction(AccountForm.MSG_PHONE_NOT_REGISTERED, MSG_GO_REGISTER, ACTION_REGISTER));
            return;
        }
        if (code == ErrorCodes.PASSWORD_WRONG || code == ErrorCodes.LOGIN_LOCKED
                || code == ErrorCodes.TEMP_PASSWORD_EXPIRED) {
            // 密码框保留内容
            setFieldError(FIELD_PASSWORD, e.getMessage());
            return;
        }
        if (code == ErrorCodes.ACCOUNT_DISABLED) {
            banner.setValue(e.getMessage());
            return;
        }
        if (handleSmsError(e, FIELD_SMS_CODE)) {
            return;
        }
        handleError(e);
    }

    /** A03 重置成功返回（RESULT_OK） */
    public void onPasswordReset() {
        loginMode.setValue(MODE_PASSWORD);
        password.setValue("");
        fieldErrors.setValue(java.util.Collections.emptyMap());
        focusField.setValue(FIELD_PASSWORD);
        showMessage(MSG_PASSWORD_RESET);
    }

    /** A05 提交成功返回（RESULT_OK，带 new_phone_masked） */
    public void onRecoverySubmitted(@Nullable String newPhoneMasked) {
        showMessage("找回申请已提交，客服核实后会把新密码发到 " + (newPhoneMasked == null ? "新手机号" : newPhoneMasked));
    }

    /** 点“注册”“忘记密码”“手机号无法使用”、Snackbar“去注册” */
    public void openPage(@NonNull String route) {
        navigate.setValue(route);
    }

    /** 当前手机号（去空格），带给 A02 / A03 / A05 */
    @NonNull
    public String phoneDigits() {
        return AccountForm.phone(phone.getValue());
    }
}
