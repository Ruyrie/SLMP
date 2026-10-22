package com.zhimu.slmp.account.ui.recovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.AccountRecoveryRequest;
import com.zhimu.slmp.account.model.request.SmsCodeSendRequest;
import com.zhimu.slmp.account.model.vo.AccountRecoverySubmitVO;
import com.zhimu.slmp.account.ui.base.AccountFormViewModel;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.PhoneFormatWatcher;
import com.zhimu.slmp.account.util.RecoveryCodeWatcher;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.Map;

import javax.inject.Inject;

/**
 * A05 找回账号（2.4.5 节）：原手机号、恢复码、新手机号与验证码（API-A01 RECOVERY_NEW_PHONE）、情况说明，
 * 提交 API-A10（幂等键）；40105 显示剩余次数，42903 锁定横幅并禁用提交。
 */
public class RecoveryApplyViewModel extends AccountFormViewModel {

    public static final String FIELD_OLD_PHONE = "old_phone";
    public static final String FIELD_RECOVERY_CODE = "recovery_code";
    public static final String FIELD_NEW_PHONE = "new_phone";
    public static final String FIELD_NEW_PHONE_SMS_CODE = "new_phone_sms_code";
    public static final String FIELD_DESCRIPTION = "description";

    public static final String DEFAULT_SERVICE_PHONE = "400-123-4567";
    private static final String KEY_DESCRIPTION = "description";

    private final AccountRepository repository;

    public final MutableLiveData<String> oldPhone = new MutableLiveData<>("");
    public final MutableLiveData<String> recoveryCode = new MutableLiveData<>("");
    public final MutableLiveData<String> newPhone = new MutableLiveData<>("");
    public final MutableLiveData<String> newPhoneSmsCode = new MutableLiveData<>("");
    public final MutableLiveData<String> description = new MutableLiveData<>("");
    private final LiveData<String> descriptionCount;
    private final MutableLiveData<String> lockBanner = new MutableLiveData<>(null);
    private final MutableLiveData<String> servicePhone = new MutableLiveData<>(DEFAULT_SERVICE_PHONE);
    private final MediatorLiveData<Boolean> isDirty = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<AccountRecoverySubmitVO> submitted = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> confirmDiscard = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> dial = new SingleLiveEvent<>();

    @Nullable
    private SavedStateHandle handle;
    @Nullable
    private String prefilledOldPhone;

    @Inject
    public RecoveryApplyViewModel(@NonNull AccountRepository repository) {
        this.repository = repository;
        descriptionCount = Transformations.map(description, d -> AccountForm.length(d) + " / " + AccountForm.DESCRIPTION_MAX);
        isDirty.setValue(false);
        canSubmit.setValue(false);
        watch(oldPhone, FIELD_OLD_PHONE);
        watch(recoveryCode, FIELD_RECOVERY_CODE);
        watch(newPhone, FIELD_NEW_PHONE);
        watch(newPhoneSmsCode, FIELD_NEW_PHONE_SMS_CODE);
        watch(description, FIELD_DESCRIPTION);
        canSubmit.addSource(lockBanner, v -> update());
        canSubmit.addSource(submitting, v -> update());
        loadServicePhone();
    }

    private void watch(@NonNull MutableLiveData<String> field, @NonNull String key) {
        canSubmit.addSource(field, v -> {
            clearFieldError(key);
            onSubmitFieldChanged();
            update();
        });
        isDirty.addSource(field, v -> update());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        this.handle = handle;
        String p = handle.get(RouterPath.EXTRA_PHONE);
        if (p != null) {
            prefilledOldPhone = p;
            oldPhone.setValue(PhoneFormatWatcher.format(p));
        }
        String d = handle.get(KEY_DESCRIPTION);
        if (d != null) {
            description.setValue(d);
        }
    }

    private void update() {
        // 任一输入非空即视为已填写；带入的原手机号未改动时不算
        boolean dirty = !allBlankExceptOldPhone()
                || !AccountForm.phone(oldPhone.getValue()).equals(AccountForm.phone(prefilledOldPhone));
        if (!Boolean.valueOf(dirty).equals(isDirty.getValue())) {
            isDirty.setValue(dirty);
        }
        Boolean submit = !AccountForm.isBlank(oldPhone.getValue()) && !AccountForm.isBlank(recoveryCode.getValue())
                && !AccountForm.isBlank(newPhone.getValue()) && !AccountForm.isBlank(newPhoneSmsCode.getValue())
                && !AccountForm.isBlank(description.getValue()) && lockBanner.getValue() == null && !isSubmitting();
        if (!submit.equals(canSubmit.getValue())) {
            canSubmit.setValue(submit);
        }
        if (handle != null) {
            handle.set(KEY_DESCRIPTION, description.getValue());
        }
    }

    private boolean allBlankExceptOldPhone() {
        return AccountForm.isBlank(recoveryCode.getValue()) && AccountForm.isBlank(newPhone.getValue())
                && AccountForm.isBlank(newPhoneSmsCode.getValue()) && AccountForm.isBlank(description.getValue());
    }

    // ===== 对外 LiveData =====

    public LiveData<String> getDescriptionCount() {
        return descriptionCount;
    }

    public LiveData<String> getLockBanner() {
        return lockBanner;
    }

    public LiveData<String> getServicePhone() {
        return servicePhone;
    }

    public LiveData<Boolean> getIsDirty() {
        return isDirty;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<AccountRecoverySubmitVO> getSubmitted() {
        return submitted;
    }

    public SingleLiveEvent<Boolean> getConfirmDiscard() {
        return confirmDiscard;
    }

    public SingleLiveEvent<String> getDial() {
        return dial;
    }

    // ===== 方法 =====

    /** API-A21 客服电话，失败时用资源号码 */
    public void loadServicePhone() {
        launch(repository.getAgreement(), a -> {
            if (a.getCustomerServicePhone() != null) {
                servicePhone.setValue(a.getCustomerServicePhone());
            }
        }, e -> {
        });
    }

    public void sendNewPhoneSms() {
        if (sms.isCounting()) {
            return;
        }
        FormValidator v = new FormValidator()
                .phone(FIELD_OLD_PHONE, oldPhone.getValue())
                .phone(FIELD_NEW_PHONE, newPhone.getValue());
        v.check(FIELD_NEW_PHONE, !AccountForm.phone(newPhone.getValue()).equals(AccountForm.phone(oldPhone.getValue())),
                AccountForm.MSG_SAME_PHONE);
        if (!applyValidation(v.validate())) {
            return;
        }
        SmsCodeSendRequest req = new SmsCodeSendRequest(AccountForm.phone(newPhone.getValue()),
                SmsCodeSendRequest.SCENE_RECOVERY_NEW_PHONE, AccountForm.phone(oldPhone.getValue()));
        launch(repository.sendSmsCode(req), vo -> onSmsSent(vo, FIELD_NEW_PHONE_SMS_CODE), this::handleSubmitError);
    }

    @NonNull
    Map<String, String> validate() {
        FormValidator v = new FormValidator()
                .phone(FIELD_OLD_PHONE, oldPhone.getValue());
        v.check(FIELD_RECOVERY_CODE, RecoveryCodeWatcher.normalize(recoveryCode.getValue()).length()
                == RecoveryCodeWatcher.CODE_LENGTH && !AccountForm.isBlank(recoveryCode.getValue()), AccountForm.MSG_RECOVERY_CODE);
        v.phone(FIELD_NEW_PHONE, newPhone.getValue());
        v.check(FIELD_NEW_PHONE, !AccountForm.phone(newPhone.getValue()).equals(AccountForm.phone(oldPhone.getValue())),
                AccountForm.MSG_SAME_PHONE);
        v.smsCode(FIELD_NEW_PHONE_SMS_CODE, newPhoneSmsCode.getValue());
        String desc = AccountForm.trim(description.getValue());
        v.check(FIELD_DESCRIPTION, !desc.isEmpty() && AccountForm.length(desc) <= AccountForm.DESCRIPTION_MAX,
                AccountForm.MSG_DESCRIPTION);
        return v.validate();
    }

    public void submit() {
        if (isSubmitting() || lockBanner.getValue() != null) {
            return;
        }
        if (!applyValidation(validate())) {
            return;
        }
        String key = obtainIdemKey();
        submitting.setValue(true);
        AccountRecoveryRequest req = new AccountRecoveryRequest(AccountForm.phone(oldPhone.getValue()),
                RecoveryCodeWatcher.normalize(recoveryCode.getValue()), AccountForm.phone(newPhone.getValue()),
                AccountForm.trim(newPhoneSmsCode.getValue()), AccountForm.trim(description.getValue()));
        launch(repository.submitRecovery(req, key), vo -> {
            onSubmitFinished(null);
            submitted.setValue(vo);
        }, e -> {
            onSubmitFinished(e);
            handleSubmitError(e);
        });
    }

    private void handleSubmitError(@NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.RECOVERY_CODE_WRONG) {
            setFieldError(FIELD_RECOVERY_CODE, e.getMessage());
            return;
        }
        if (code == ErrorCodes.RECOVERY_LOCKED) {
            clearFieldError(FIELD_RECOVERY_CODE);
            lockBanner.setValue(e.getMessage());
            return;
        }
        if (code == ErrorCodes.PHONE_REGISTERED) {
            setFieldError(FIELD_NEW_PHONE, e.getMessage());
            return;
        }
        if (code == ErrorCodes.PENDING_EXISTS || code == ErrorCodes.ACCOUNT_DISABLED) {
            showMessage(e.getMessage());
            return;
        }
        if (handleSmsError(e, FIELD_NEW_PHONE_SMS_CODE)) {
            return;
        }
        handleError(e);
    }

    /** 返回：已填写内容时先确认 */
    public void onBack() {
        confirmDiscard.setValue(true);
    }

    public void onServiceClick() {
        dial.setValue(servicePhone.getValue());
    }
}
