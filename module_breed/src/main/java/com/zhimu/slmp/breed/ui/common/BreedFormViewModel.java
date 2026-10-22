package com.zhimu.slmp.breed.ui.common;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.util.SubmitSupport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * 登记表单 ViewModel 的共用部分（技术文档 2.4 节约定、2.5.2 错误码、2.6.2 提交与超时确认）：
 * fieldErrors（键为 JSON 字段名）、submitting、放弃确认、提交时无权限、网络失败“重试”、版本冲突“刷新”、
 * 超时后按幂等键查 API-B32。
 */
public abstract class BreedFormViewModel extends BaseViewModel {

    public static final String ACTION_RETRY = "breed_retry";
    public static final String ACTION_REFRESH = "breed_refresh";

    protected final BreedRepository repository;

    protected final MutableLiveData<Map<String, String>> fieldErrors =
            new MutableLiveData<>(Collections.emptyMap());
    protected final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    protected final SingleLiveEvent<Void> confirmDiscard = new SingleLiveEvent<>();
    protected final SingleLiveEvent<Void> noPermission = new SingleLiveEvent<>();
    protected final SingleLiveEvent<String> scrollToError = new SingleLiveEvent<>();

    /** 表单有未提交的改动 */
    protected boolean dirty;
    /** 当前失败发生在提交时（无权限只提示并关闭，不切换整页） */
    private boolean inSubmit;

    protected BreedFormViewModel(@NonNull BreedRepository repository) {
        this.repository = repository;
    }

    // ===== 对外 =====

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Void> getConfirmDiscard() {
        return confirmDiscard;
    }

    public LiveData<Void> getNoPermission() {
        return noPermission;
    }

    public LiveData<String> getScrollToError() {
        return scrollToError;
    }

    /** 返回键：有改动时弹出“放弃本次登记？”并返回 true */
    public boolean onBack() {
        if (dirty && !Boolean.TRUE.equals(submitting.getValue())) {
            confirmDiscard.call();
            return true;
        }
        return false;
    }

    public void markDirty() {
        dirty = true;
    }

    /** Snackbar 操作：重试沿用幂等键重新提交；刷新重新加载 */
    @CallSuper
    public void onMessageAction(@NonNull String actionId) {
        if (ACTION_RETRY.equals(actionId)) {
            retrySubmit();
        } else if (ACTION_REFRESH.equals(actionId)) {
            retry();
        }
    }

    /** “重试”：沿用同一幂等键重新提交（各页面重写为调用自己的提交方法） */
    protected void retrySubmit() {
    }

    // ===== 字段错误 =====

    protected void setErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(errors.isEmpty() ? Collections.emptyMap() : new LinkedHashMap<>(errors));
        if (!errors.isEmpty()) {
            scrollToError.setValue(errors.keySet().iterator().next());
        }
    }

    protected void putFieldError(@NonNull String field, @Nullable String message) {
        Map<String, String> m = new LinkedHashMap<>(current());
        if (message == null) {
            m.remove(field);
        } else {
            m.put(field, message);
        }
        fieldErrors.setValue(m);
    }

    /** 用户修改某个字段后清除它的错误 */
    protected void clearFieldError(@NonNull String... fields) {
        Map<String, String> m = current();
        boolean changed = false;
        Map<String, String> next = new LinkedHashMap<>(m);
        for (String f : fields) {
            if (next.remove(f) != null) {
                changed = true;
            }
        }
        if (changed) {
            fieldErrors.setValue(next);
        }
    }

    @NonNull
    private Map<String, String> current() {
        Map<String, String> m = fieldErrors.getValue();
        return m == null ? Collections.emptyMap() : m;
    }

    /** 本页面认识的字段；40001 中其余字段合并为一条 Snackbar */
    protected boolean isKnownField(@NonNull String field) {
        return true;
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        Map<String, String> known = new LinkedHashMap<>();
        StringBuilder other = new StringBuilder();
        for (Map.Entry<String, String> e : errors.entrySet()) {
            if (isKnownField(e.getKey())) {
                known.put(e.getKey(), e.getValue());
            } else {
                if (other.length() > 0) {
                    other.append("；");
                }
                other.append(e.getValue());
            }
        }
        if (!known.isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>(current());
            merged.putAll(known);
            fieldErrors.setValue(merged);
            scrollToError.setValue(known.keySet().iterator().next());
        }
        if (other.length() > 0) {
            showMessage(other.toString());
        }
        return true;
    }

    // ===== 提交 =====

    protected void beginSubmit() {
        inSubmit = true;
        submitting.setValue(true);
    }

    protected void endSubmit() {
        inSubmit = false;
        submitting.setValue(false);
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (inSubmit) {
            showMessage(SubmitSupport.MSG_NO_PERMISSION);
            noPermission.call();
        } else {
            super.onNoPermission(e);
        }
    }

    /**
     * 提交失败（42206 由页面先处理）：超时且有 SUBMISSION_TYPE 时保持提交中并查 API-B32；网络失败提示“重试”；
     * 其余按 2.5.2 节。
     *
     * @param submissionType SUBMISSION_TYPE；新增母猪、死亡淘汰、照蛋、修改类为 null
     * @param idem           幂等键（超时确认用）
     * @param onSaved        API-B32 确认已保存时按成功处理
     */
    protected void onSubmitError(@NonNull ApiException e, @Nullable String submissionType,
                                 @Nullable SubmitSupport.IdemKeeper idem, @Nullable Consumer<RegisterResultVO> onSaved) {
        if (SubmitSupport.isTimeout(e) && submissionType != null && idem != null && idem.current() != null
                && onSaved != null) {
            confirmUncertain(submissionType, idem.current(), onSaved);
            return;
        }
        endSubmit();
        int code = e.getCode();
        if (e.isNetwork()) {
            showMessage(UiMessage.withAction(SubmitSupport.MSG_NETWORK_KEEP, SubmitSupport.MSG_RETRY, ACTION_RETRY));
            return;
        }
        if (code == ErrorCodes.VERSION_CONFLICT) {
            showMessage(UiMessage.withAction(SubmitSupport.MSG_CONFLICT, SubmitSupport.MSG_REFRESH, ACTION_REFRESH));
            return;
        }
        if (code == ErrorCodes.STATE_CHANGED) {
            showMessage(UiMessage.withAction(e.getMessage(), SubmitSupport.MSG_REFRESH, ACTION_REFRESH));
            return;
        }
        if (code == ErrorCodes.NOT_FOUND) {
            showMessage(SubmitSupport.MSG_NOT_FOUND);
            return;
        }
        inSubmit = true;
        handleError(e);
        inSubmit = false;
    }

    /** 超时后确认（API-B32）：已保存按成功处理；未保存提示“提交未成功，请重新提交”并保留幂等键 */
    protected void confirmUncertain(@NonNull String type, @NonNull String clientRequestId,
                                    @NonNull Consumer<RegisterResultVO> onSaved) {
        launch(repository.getSubmission(clientRequestId, type), s -> {
            if (s.isSaved() && s.getResult() != null) {
                onSaved.accept(s.getResult());
            } else {
                endSubmit();
                showMessage(SubmitSupport.MSG_NOT_SAVED);
            }
        }, err -> {
            endSubmit();
            showMessage(UiMessage.withAction(SubmitSupport.MSG_NETWORK_KEEP, SubmitSupport.MSG_RETRY, ACTION_RETRY));
        });
    }
}
