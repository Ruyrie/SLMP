package com.zhimu.slmp.account.ui.security;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.AccountSmsCodeRequest;
import com.zhimu.slmp.account.model.request.RecoveryCodeRegenerateRequest;
import com.zhimu.slmp.account.model.vo.AccountSecurityVO;
import com.zhimu.slmp.account.ui.base.AccountFormViewModel;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.RecoveryCodeHolder;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.router.RouterPath;

import javax.inject.Inject;

/**
 * A08 账号与安全（2.4.8 节）：手机号（API-A15 security）、修改密码页、重新生成恢复码（API-A02 发码 + API-A13，幂等键）、注销入口。
 */
public class AccountSecurityViewModel extends AccountFormViewModel {

    public static final String FIELD_SMS_CODE = "sms_code";
    public static final String MSG_PASSWORD_CHANGED = "密码已修改，其他设备需要重新登录";
    public static final String MSG_CODE_REGENERATED = "已生成新的恢复码，旧恢复码已作废";

    private final AccountRepository repository;
    private final RecoveryCodeHolder codeHolder;

    private final MutableLiveData<AccountSecurityVO> security = new MutableLiveData<>(null);
    public final MutableLiveData<String> smsCode = new MutableLiveData<>("");
    private final LiveData<Boolean> canRegenerate;

    private final SingleLiveEvent<Boolean> showSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> dismissSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> navigate = new SingleLiveEvent<>();

    @Inject
    public AccountSecurityViewModel(@NonNull AccountRepository repository, @NonNull RecoveryCodeHolder codeHolder) {
        this.repository = repository;
        this.codeHolder = codeHolder;
        canRegenerate = Transformations.map(smsCode, c -> {
            clearFieldError(FIELD_SMS_CODE);
            onSubmitFieldChanged();
            return c != null && c.trim().length() == 6;
        });
        load();
    }

    public LiveData<AccountSecurityVO> getSecurity() {
        return security;
    }

    /** 面板验证码满 6 位时“验证并生成”可用 */
    public LiveData<Boolean> getCanRegenerate() {
        return canRegenerate;
    }

    public SingleLiveEvent<Boolean> getShowSheet() {
        return showSheet;
    }

    public SingleLiveEvent<Boolean> getDismissSheet() {
        return dismissSheet;
    }

    public SingleLiveEvent<String> getNavigate() {
        return navigate;
    }

    public void load() {
        load(repository.getOverview(), o -> security.setValue(o.getSecurity()));
    }

    @Override
    public void retry() {
        load();
    }

    public void openRegenerate() {
        smsCode.setValue("");
        fieldErrors.setValue(java.util.Collections.emptyMap());
        showSheet.setValue(true);
    }

    public void sendSmsCode() {
        if (sms.isCounting()) {
            return;
        }
        launch(repository.sendAccountSmsCode(new AccountSmsCodeRequest(AccountSmsCodeRequest.SCENE_REGENERATE_RECOVERY_CODE)),
                vo -> onSmsSent(vo, FIELD_SMS_CODE), this::handleSheetError);
    }

    public void regenerate() {
        if (isSubmitting()) {
            return;
        }
        if (!applyValidation(new FormValidator().smsCode(FIELD_SMS_CODE, smsCode.getValue()).validate())) {
            return;
        }
        String key = obtainIdemKey();
        submitting.setValue(true);
        launch(repository.regenerateCode(new RecoveryCodeRegenerateRequest(AccountForm.trim(smsCode.getValue())), key),
                code -> {
                    onSubmitFinished(null);
                    codeHolder.put(code);
                    dismissSheet.setValue(true);
                    navigate.setValue(RouterPath.A04);
                }, e -> {
                    onSubmitFinished(e);
                    handleSheetError(e);
                });
    }

    private void handleSheetError(@NonNull ApiException e) {
        if (handleSmsError(e, FIELD_SMS_CODE)) {
            return;
        }
        handleError(e);
    }

    /** 面板内操作无权时只提示 */
    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        showMessage(e.getMessage());
    }

    /** 点“修改密码”“注销账号” */
    public void openPage(@NonNull String route) {
        navigate.setValue(route);
    }

    /** 修改密码页返回 RESULT_OK */
    public void onPasswordChanged() {
        showMessage(MSG_PASSWORD_CHANGED);
    }

    /** A04 重新生成版返回 RESULT_OK */
    public void onCodeConfirmed() {
        showMessage(MSG_CODE_REGENERATED);
        load();
    }
}
