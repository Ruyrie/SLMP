package com.zhimu.slmp.account.ui.afterrecovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.AfterRecoveryPasswordRequest;
import com.zhimu.slmp.account.ui.base.AccountFormViewModel;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.RecoveryCodeWatcher;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;
import com.zhimu.slmp.common.model.vo.RecoveryInfoVO;
import com.zhimu.slmp.common.session.SessionManager;

import java.time.LocalDate;

import javax.inject.Inject;

/**
 * A06 找回后首次登录（2.4.6 节，Activity 范围）：第 1 步修改密码（API-A11，幂等键），成功先保存新令牌再切到第 2 步；
 * 第 2 步显示新的恢复码，勾选后确认（API-A14）进入首页。进程被回收后由 SavedStateHandle 恢复 step，码已丢失时调 API-A12。
 */
public class AfterRecoveryViewModel extends AccountFormViewModel {

    public static final String KEY_STEP = "step";
    public static final String FIELD_NEW_PASSWORD = "new_password";
    public static final String FIELD_CONFIRM_PASSWORD = "confirm_password";
    public static final String MSG_DONE = "密码已修改，新的恢复码已生效";
    public static final String MSG_CODE_UPDATED = "恢复码已更新，请保存最新的恢复码";

    private final AccountRepository repository;
    private final SessionManager sessionManager;

    private final MutableLiveData<Integer> step = new MutableLiveData<>(1);
    private final LiveData<Integer> progress;
    private final MutableLiveData<RecoveryInfoVO> recoveryInfo = new MutableLiveData<>(null);
    private final LiveData<String> accountBanner;
    private final LiveData<String> cancelBanner;
    public final MutableLiveData<String> newPassword = new MutableLiveData<>("");
    public final MutableLiveData<String> confirmPassword = new MutableLiveData<>("");
    private final MutableLiveData<RecoveryCodeVO> recoveryCode = new MutableLiveData<>(null);
    private final LiveData<String> codeGroups;
    public final MutableLiveData<Boolean> savedChecked = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<Boolean> done = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> copyText = new SingleLiveEvent<>();
    private final SingleLiveEvent<RecoveryCodeVO> saveImage = new SingleLiveEvent<>();

    @Nullable
    private SavedStateHandle handle;

    @Inject
    public AfterRecoveryViewModel(@NonNull AccountRepository repository, @NonNull SessionManager sessionManager) {
        this.repository = repository;
        this.sessionManager = sessionManager;
        progress = Transformations.map(step, s -> s != null && s == 2 ? 100 : 50);
        accountBanner = Transformations.map(recoveryInfo, AfterRecoveryViewModel::accountBannerOf);
        cancelBanner = Transformations.map(recoveryInfo, AfterRecoveryViewModel::cancelBannerOf);
        codeGroups = Transformations.map(recoveryCode, c -> c == null ? null : RecoveryCodeWatcher.format(c.getCode()));
        canSubmit.setValue(false);
        canSubmit.addSource(step, v -> update());
        canSubmit.addSource(newPassword, v -> {
            clearFieldError(FIELD_NEW_PASSWORD);
            onSubmitFieldChanged();
            update();
        });
        canSubmit.addSource(confirmPassword, v -> {
            clearFieldError(FIELD_CONFIRM_PASSWORD);
            onSubmitFieldChanged();
            update();
        });
        canSubmit.addSource(savedChecked, v -> update());
        canSubmit.addSource(recoveryCode, v -> update());
        canSubmit.addSource(submitting, v -> update());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        this.handle = handle;
        Integer s = handle.get(KEY_STEP);
        step.setValue(s == null ? 1 : s);
        start();
    }

    @Nullable
    static String accountBannerOf(@Nullable RecoveryInfoVO info) {
        if (info == null) {
            return null;
        }
        String masked = info.getNewPhoneMasked() == null ? "" : info.getNewPhoneMasked() + " ";
        return "账号已找回。你已用新手机号 " + masked + "登录，请先修改密码。其他设备上的登录已自动失效。";
    }

    @Nullable
    static String cancelBannerOf(@Nullable RecoveryInfoVO info) {
        if (info == null || info.getCancelAvailableDate() == null) {
            return null;
        }
        LocalDate d = DateFormatter.parseDate(info.getCancelAvailableDate());
        String date = d == null ? info.getCancelAvailableDate() : DateFormatter.monthDay(d);
        return "找回后 7 天内不能注销账号。" + date + "前注销不可用，防止账号被他人冒名找回后恶意注销。";
    }

    private void update() {
        boolean busy = isSubmitting();
        Integer s = step.getValue();
        Boolean v;
        if (s != null && s == 2) {
            v = Boolean.TRUE.equals(savedChecked.getValue()) && recoveryCode.getValue() != null && !busy;
        } else {
            v = !AccountForm.isBlank(newPassword.getValue()) && !AccountForm.isBlank(confirmPassword.getValue()) && !busy;
        }
        if (!v.equals(canSubmit.getValue())) {
            canSubmit.setValue(v);
        }
    }

    // ===== 对外 LiveData =====

    public LiveData<Integer> getStep() {
        return step;
    }

    public LiveData<Integer> getProgress() {
        return progress;
    }

    public LiveData<RecoveryInfoVO> getRecoveryInfo() {
        return recoveryInfo;
    }

    public LiveData<String> getAccountBanner() {
        return accountBanner;
    }

    public LiveData<String> getCancelBanner() {
        return cancelBanner;
    }

    public LiveData<RecoveryCodeVO> getRecoveryCode() {
        return recoveryCode;
    }

    public LiveData<String> getCodeGroups() {
        return codeGroups;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<Boolean> getDone() {
        return done;
    }

    public SingleLiveEvent<String> getCopyText() {
        return copyText;
    }

    public SingleLiveEvent<RecoveryCodeVO> getSaveImage() {
        return saveImage;
    }

    // ===== 方法 =====

    /** 缺 recovery_info 时调 API-A08；step 为 2 且没有码时调 API-A12 */
    public void start() {
        RecoveryInfoVO info = sessionManager.getRecoveryInfo();
        if (info != null) {
            recoveryInfo.setValue(info);
        } else {
            launch(repository.getSession(), s -> {
                recoveryInfo.setValue(s.getRecoveryInfo());
                if (AuthTokenVO.NEXT_SAVE_RECOVERY_CODE.equals(s.getNextStep())) {
                    toStep2(null);
                }
            }, e -> {
            });
        }
        Integer s = step.getValue();
        if (s != null && s == 2 && recoveryCode.getValue() == null) {
            regenerateCode();
        }
    }

    private void regenerateCode() {
        launch(repository.regeneratePendingCode(), recoveryCode::setValue);
    }

    private void toStep2(@Nullable RecoveryCodeVO code) {
        step.setValue(2);
        if (handle != null) {
            handle.set(KEY_STEP, 2);
        }
        if (code != null) {
            recoveryCode.setValue(code);
        } else if (recoveryCode.getValue() == null) {
            regenerateCode();
        }
    }

    /** 主按钮：第 1 步提交改密，第 2 步进入智牧通 */
    public void onPrimaryClick() {
        Integer s = step.getValue();
        if (s != null && s == 2) {
            enter();
        } else {
            submitPassword();
        }
    }

    public void submitPassword() {
        if (isSubmitting()) {
            return;
        }
        FormValidator v = new FormValidator();
        AccountForm.newPassword(v, FIELD_NEW_PASSWORD, newPassword.getValue());
        v.required(FIELD_CONFIRM_PASSWORD, confirmPassword.getValue(), AccountForm.MSG_PASSWORD_MISMATCH);
        v.check(FIELD_CONFIRM_PASSWORD, AccountForm.text(newPassword.getValue()).equals(confirmPassword.getValue()),
                AccountForm.MSG_PASSWORD_MISMATCH);
        if (!applyValidation(v.validate())) {
            return;
        }
        String key = obtainIdemKey();
        submitting.setValue(true);
        AfterRecoveryPasswordRequest req = new AfterRecoveryPasswordRequest(AccountForm.text(newPassword.getValue()),
                AccountForm.text(confirmPassword.getValue()));
        launch(repository.changePasswordAfterRecovery(req, key), token -> {
            onSubmitFinished(null);
            newPassword.setValue("");
            confirmPassword.setValue("");
            toStep2(token.getRecoveryCode());
        }, e -> {
            onSubmitFinished(e);
            if (e.getCode() == ErrorCodes.FORCED_STEP_REQUIRED
                    && AuthTokenVO.NEXT_SAVE_RECOVERY_CODE.equals(e.dataString("next_step"))) {
                // 已改过密码（例如响应丢失后重试）：直接进入第 2 步
                toStep2(null);
                return;
            }
            handleError(e);
        });
    }

    public void copyCode() {
        RecoveryCodeVO c = recoveryCode.getValue();
        if (c != null) {
            copyText.setValue(RecoveryCodeWatcher.format(c.getCode()));
        }
    }

    public void saveImage() {
        RecoveryCodeVO c = recoveryCode.getValue();
        if (c != null) {
            saveImage.setValue(c);
        }
    }

    /** API-A14；成功后按 last_farm_id 恢复养殖场，进入首页 */
    public void enter() {
        RecoveryCodeVO c = recoveryCode.getValue();
        if (c == null || isSubmitting() || !Boolean.TRUE.equals(savedChecked.getValue())) {
            return;
        }
        submitting.setValue(true);
        launch(repository.confirmRecoveryCode(c.getRecoveryCodeId())
                .flatMap(vo -> repository.getSession()
                        .flatMapCompletable(s -> repository.restoreFarm(s.getLastFarmId()))
                        .toSingleDefault(true)), ok -> {
            submitting.setValue(false);
            recoveryCode.setValue(null);
            done.setValue(true);
        }, e -> {
            submitting.setValue(false);
            if (e.getCode() == ErrorCodes.STATE_CHANGED || e.getCode() == ErrorCodes.NOT_FOUND) {
                showMessage(MSG_CODE_UPDATED);
                savedChecked.setValue(false);
                recoveryCode.setValue(null);
                regenerateCode();
                return;
            }
            handleError(e);
        });
    }

    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        // 40902 / 40903 已在各操作中处理
    }
}
