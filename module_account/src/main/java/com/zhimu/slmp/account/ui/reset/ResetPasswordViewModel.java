package com.zhimu.slmp.account.ui.reset;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.PasswordResetRequest;
import com.zhimu.slmp.account.model.request.SmsCodeSendRequest;
import com.zhimu.slmp.account.ui.base.AccountFormViewModel;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.PhoneFormatWatcher;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.Map;

import javax.inject.Inject;

/**
 * A03 重置密码（2.4.3 节）：发码（API-A01 RESET_PASSWORD）、重置（API-A09，幂等键）；成功 RESULT_OK 回 A01。
 */
public class ResetPasswordViewModel extends AccountFormViewModel {

    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_SMS_CODE = "sms_code";
    public static final String FIELD_NEW_PASSWORD = "new_password";

    private final AccountRepository repository;

    public final MutableLiveData<String> phone = new MutableLiveData<>("");
    public final MutableLiveData<String> smsCode = new MutableLiveData<>("");
    public final MutableLiveData<String> newPassword = new MutableLiveData<>("");
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<Boolean> resetDone = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> navigate = new SingleLiveEvent<>();

    @Inject
    public ResetPasswordViewModel(@NonNull AccountRepository repository) {
        this.repository = repository;
        canSubmit.setValue(false);
        canSubmit.addSource(phone, v -> onFieldChanged(FIELD_PHONE));
        canSubmit.addSource(smsCode, v -> onFieldChanged(FIELD_SMS_CODE));
        canSubmit.addSource(newPassword, v -> onFieldChanged(FIELD_NEW_PASSWORD));
        canSubmit.addSource(submitting, v -> updateCanSubmit());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        String p = handle.get(RouterPath.EXTRA_PHONE);
        if (p != null) {
            phone.setValue(PhoneFormatWatcher.format(p));
        }
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<Boolean> getResetDone() {
        return resetDone;
    }

    public SingleLiveEvent<String> getNavigate() {
        return navigate;
    }

    private void onFieldChanged(@NonNull String field) {
        clearFieldError(field);
        onSubmitFieldChanged();
        updateCanSubmit();
    }

    private void updateCanSubmit() {
        Boolean value = !AccountForm.isBlank(phone.getValue()) && !AccountForm.isBlank(smsCode.getValue())
                && !AccountForm.isBlank(newPassword.getValue()) && !isSubmitting();
        if (!value.equals(canSubmit.getValue())) {
            canSubmit.setValue(value);
        }
    }

    public void sendSmsCode() {
        if (sms.isCounting()) {
            return;
        }
        Map<String, String> errors = new FormValidator().phone(FIELD_PHONE, phone.getValue()).validate();
        if (!applyValidation(errors)) {
            return;
        }
        launch(repository.sendSmsCode(new SmsCodeSendRequest(AccountForm.phone(phone.getValue()),
                        SmsCodeSendRequest.SCENE_RESET_PASSWORD)),
                vo -> onSmsSent(vo, FIELD_SMS_CODE), this::handleResetError);
    }

    public void resetPassword() {
        if (isSubmitting()) {
            return;
        }
        FormValidator v = new FormValidator()
                .phone(FIELD_PHONE, phone.getValue())
                .smsCode(FIELD_SMS_CODE, smsCode.getValue());
        AccountForm.newPassword(v, FIELD_NEW_PASSWORD, newPassword.getValue());
        if (!applyValidation(v.validate())) {
            return;
        }
        String key = obtainIdemKey();
        submitting.setValue(true);
        PasswordResetRequest req = new PasswordResetRequest(AccountForm.phone(phone.getValue()),
                AccountForm.trim(smsCode.getValue()), AccountForm.text(newPassword.getValue()));
        launch(repository.resetPassword(req, key), () -> {
            onSubmitFinished(null);
            resetDone.setValue(true);
        }, e -> {
            onSubmitFinished(e);
            handleResetError(e);
        });
    }

    private void handleResetError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.PARAM_INVALID
                && AccountForm.REASON_PHONE_NOT_REGISTERED.equals(e.dataString("reason"))) {
            setFieldError(FIELD_PHONE, AccountForm.MSG_PHONE_NOT_REGISTERED);
            return;
        }
        if (handleSmsError(e, FIELD_SMS_CODE)) {
            return;
        }
        handleError(e);
    }

    /** 点“申请找回账号”：打开 A05，手机号带入原手机号 */
    public void onApplyRecoveryClick() {
        navigate.setValue(RouterPath.A05);
    }

    @NonNull
    public String phoneDigits() {
        return AccountForm.phone(phone.getValue());
    }
}
