package com.zhimu.slmp.account.ui.password;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.PasswordChangeRequest;
import com.zhimu.slmp.account.ui.base.AccountFormViewModel;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;

import javax.inject.Inject;

/**
 * 修改密码页（2.4.9 节）：API-A17（幂等键），成功先保存新令牌，RESULT_OK 返回 A08。
 */
public class ChangePasswordViewModel extends AccountFormViewModel {

    public static final String FIELD_CURRENT = "current_password";
    public static final String FIELD_NEW = "new_password";
    public static final String FIELD_CONFIRM = "confirm_password";

    private final AccountRepository repository;

    public final MutableLiveData<String> currentPassword = new MutableLiveData<>("");
    public final MutableLiveData<String> newPassword = new MutableLiveData<>("");
    public final MutableLiveData<String> confirmPassword = new MutableLiveData<>("");
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final SingleLiveEvent<Boolean> done = new SingleLiveEvent<>();

    @Inject
    public ChangePasswordViewModel(@NonNull AccountRepository repository) {
        this.repository = repository;
        canSubmit.setValue(false);
        watch(currentPassword, FIELD_CURRENT);
        watch(newPassword, FIELD_NEW);
        watch(confirmPassword, FIELD_CONFIRM);
        canSubmit.addSource(submitting, v -> update());
    }

    private void watch(@NonNull MutableLiveData<String> field, @NonNull String key) {
        canSubmit.addSource(field, v -> {
            clearFieldError(key);
            onSubmitFieldChanged();
            update();
        });
    }

    private void update() {
        Boolean v = !AccountForm.isBlank(currentPassword.getValue()) && !AccountForm.isBlank(newPassword.getValue())
                && !AccountForm.isBlank(confirmPassword.getValue()) && !isSubmitting();
        if (!v.equals(canSubmit.getValue())) {
            canSubmit.setValue(v);
        }
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<Boolean> getDone() {
        return done;
    }

    public void submit() {
        if (isSubmitting()) {
            return;
        }
        String current = AccountForm.text(currentPassword.getValue());
        String next = AccountForm.text(newPassword.getValue());
        FormValidator v = new FormValidator().required(FIELD_CURRENT, current, AccountForm.MSG_PASSWORD_EMPTY);
        AccountForm.newPassword(v, FIELD_NEW, next);
        v.check(FIELD_NEW, !next.equals(current), AccountForm.MSG_PASSWORD_SAME_AS_CURRENT);
        v.required(FIELD_CONFIRM, confirmPassword.getValue(), AccountForm.MSG_PASSWORD_MISMATCH);
        v.check(FIELD_CONFIRM, next.equals(confirmPassword.getValue()), AccountForm.MSG_PASSWORD_MISMATCH);
        if (!applyValidation(v.validate())) {
            return;
        }
        String key = obtainIdemKey();
        submitting.setValue(true);
        launch(repository.changePassword(new PasswordChangeRequest(current, next,
                AccountForm.text(confirmPassword.getValue())), key), token -> {
            onSubmitFinished(null);
            done.setValue(true);
        }, e -> {
            onSubmitFinished(e);
            onSubmitError(e);
        });
    }

    private void onSubmitError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.LOGIN_LOCKED) {
            setFieldError(FIELD_CURRENT, e.getMessage());
            return;
        }
        handleError(e);
    }
}
