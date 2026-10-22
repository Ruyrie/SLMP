package com.zhimu.slmp.account.ui.base;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.account.model.vo.SmsCodeSendVO;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.SmsCountdownHelper;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.IdemKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块01 表单页 ViewModel 的公共部分（2.4 节通用约定）：
 * <ul>
 * <li>fieldErrors（键与 JSON 字段名相同，来源 FormValidator 与 40001 data.field_errors）、submitting、focusField；</li>
 * <li>smsCountdown、smsSentHint（SmsCountdownHelper 提供；42901 按 data.cooldown_seconds 倒计时，40103 过期时置 0）；</li>
 * <li>幂等键 idemKey：点主按钮且本地校验通过时为 null 则生成；没有响应（断网、超时）后重试沿用；
 * 收到任何服务端响应或用户修改提交字段后置 null。</li>
 * </ul>
 */
public abstract class AccountFormViewModel extends BaseViewModel {

    protected final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    protected final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    protected final SingleLiveEvent<String> focusField = new SingleLiveEvent<>();
    protected final SmsCountdownHelper sms = new SmsCountdownHelper();

    @Nullable
    protected String idemKey;

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public SingleLiveEvent<String> getFocusField() {
        return focusField;
    }

    public LiveData<Integer> getSmsCountdown() {
        return sms.getCountdown();
    }

    public LiveData<String> getSmsSentHint() {
        return sms.getSentHint();
    }

    /** 当前幂等键（测试与重试判断用） */
    @Nullable
    public String getIdemKey() {
        return idemKey;
    }

    public boolean isSubmitting() {
        return Boolean.TRUE.equals(submitting.getValue());
    }

    // ===== 校验与字段错误 =====

    /** 写入本地校验结果；有错误时聚焦第一个字段并返回 false */
    protected boolean applyValidation(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            focusField.setValue(errors.keySet().iterator().next());
            return false;
        }
        return true;
    }

    /** 单个字段错误（保留其他字段的错误）并聚焦 */
    protected void setFieldError(@NonNull String field, @NonNull String message) {
        Map<String, String> map = new LinkedHashMap<>();
        Map<String, String> old = fieldErrors.getValue();
        if (old != null) {
            map.putAll(old);
        }
        map.put(field, message);
        fieldErrors.setValue(Collections.unmodifiableMap(map));
        focusField.setValue(field);
    }

    /** 清除单个字段错误（用户修改该字段后） */
    public void clearFieldError(@NonNull String field) {
        Map<String, String> old = fieldErrors.getValue();
        if (old != null && old.containsKey(field)) {
            Map<String, String> map = new LinkedHashMap<>(old);
            map.remove(field);
            fieldErrors.setValue(Collections.unmodifiableMap(map));
        }
    }

    /** 40001 的 field_errors 写入输入框，不再弹 Snackbar */
    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            focusField.setValue(errors.keySet().iterator().next());
        }
        return true;
    }

    // ===== 幂等键 =====

    /** 点主按钮且校验通过时调用 */
    @NonNull
    protected String obtainIdemKey() {
        if (idemKey == null) {
            idemKey = IdemKey.newKey();
        }
        return idemKey;
    }

    /** 用户修改了提交字段 */
    protected void onSubmitFieldChanged() {
        idemKey = null;
    }

    /** 提交结束：收到服务端响应（成功或业务错误）后作废幂等键，没有响应时保留以便重试 */
    protected void onSubmitFinished(@Nullable ApiException error) {
        submitting.setValue(false);
        if (error == null || !error.isNetwork()) {
            idemKey = null;
        }
    }

    // ===== 短信验证码 =====

    protected void onSmsSent(@NonNull SmsCodeSendVO vo, @NonNull String codeField) {
        sms.onSent(vo);
        clearFieldError(codeField);
        focusField.setValue(codeField);
    }

    /**
     * 发码与提交的通用错误：42901 倒计时并提示；40103 写入验证码框（过期时尾部立即变为“重新获取”）；
     * 50301 提示发送失败。返回 true 表示已处理。
     */
    protected boolean handleSmsError(@NonNull ApiException e, @NonNull String codeField) {
        int code = e.getCode();
        if (code == ErrorCodes.TOO_FREQUENT) {
            String cd = e.dataString("cooldown_seconds");
            if (cd != null) {
                try {
                    sms.start(Integer.parseInt(cd));
                } catch (NumberFormatException ignored) {
                    // 没有冷却秒数时只提示
                }
            }
            showMessage(e.getMessage());
            return true;
        }
        if (code == ErrorCodes.SMS_CODE_WRONG) {
            if (e.getMessage().contains("过期")) {
                sms.reset();
            }
            setFieldError(codeField, e.getMessage());
            return true;
        }
        if (code == ErrorCodes.SERVICE_UNAVAILABLE) {
            showMessage(e.getMessage());
            return true;
        }
        return false;
    }

    /** 网络中断时提示表单内容会保留 */
    @CallSuper
    @Override
    protected void handleError(@NonNull Throwable t) {
        ApiException e = ApiException.from(t);
        if (e.isNetwork()) {
            showMessage(AccountForm.MSG_NETWORK);
            return;
        }
        super.handleError(e);
    }

    @CallSuper
    @Override
    protected void onCleared() {
        sms.dispose();
        super.onCleared();
    }
}
