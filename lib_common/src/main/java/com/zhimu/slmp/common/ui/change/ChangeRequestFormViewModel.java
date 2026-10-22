package com.zhimu.slmp.common.ui.change;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.common.model.vo.ChangeRequestVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * U01 提交修改申请（02 号第十一节）：表单上下文 API-U01；选择修改项、填写新值（或“作废这条记录”）与原因，
 * 提交 API-U02（幂等键）；从 U02“重新提交”进入时先取 API-U04 带入上次的修改项、新值与原因。
 * 页面参数：RouterPath.EXTRA_BIZ_TYPE、EXTRA_BIZ_ID；重新提交时 EXTRA_REQUEST_ID。
 */
public class ChangeRequestFormViewModel extends BaseViewModel {

    public static final String FIELD_FIELD_CODE = "field_code";
    public static final String FIELD_NEW_VALUE = "new_value";
    public static final String FIELD_REASON = "reason";
    /** 修改项“作废这条记录” */
    public static final String VOID_CODE = "__VOID__";
    public static final int REASON_MAX = 200;

    public static final String MSG_PICK_FIELD = "请选择要修改的项目";
    public static final String MSG_NEW_VALUE_EMPTY = "请填写新值";
    public static final String MSG_SAME_VALUE = "新值与原值相同，不需要提交";
    public static final String MSG_REASON_EMPTY = "请填写修改原因";
    public static final String MSG_PENDING = "这条记录已有一条待审核的修改申请。审核完成前不能再次提交。";
    public static final String MSG_SUBMITTED = "已提交修改申请，等待管理员审核";
    public static final String MSG_NETWORK = "网络连接失败，已保留填写的内容";
    public static final String MSG_CHANGED = "内容已更新，请刷新后再修改";
    public static final String MSG_NO_PERMISSION = "你没有这项操作的权限";
    public static final String ACTION_RETRY = "retry";
    public static final String ACTION_REFRESH = "refresh";

    private final ChangeRepository repository;

    private final MutableLiveData<ChangeRequestFormVO> form = new MutableLiveData<>(null);
    public final MutableLiveData<String> selectedField = new MutableLiveData<>(null);
    public final MutableLiveData<String> newValue = new MutableLiveData<>("");
    public final MutableLiveData<String> reason = new MutableLiveData<>("");
    private final MutableLiveData<String> rejectReason = new MutableLiveData<>(null);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<ChangeRequestVO> submitted = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> focusField = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> closeForbidden = new SingleLiveEvent<>();

    @Nullable
    private String bizType;
    private long bizId;
    @Nullable
    private Long resubmitOfId;
    @Nullable
    private String idemKey;
    private boolean userEdited;

    @Inject
    public ChangeRequestFormViewModel(@NonNull ChangeRepository repository) {
        this.repository = repository;
        canSubmit.setValue(false);
        canSubmit.addSource(form, v -> update());
        canSubmit.addSource(selectedField, v -> onEdited(FIELD_FIELD_CODE));
        canSubmit.addSource(newValue, v -> onEdited(FIELD_NEW_VALUE));
        canSubmit.addSource(reason, v -> onEdited(FIELD_REASON));
        canSubmit.addSource(submitting, v -> update());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        bizType = handle.get(RouterPath.EXTRA_BIZ_TYPE);
        Long id = readLong(handle.get(RouterPath.EXTRA_BIZ_ID));
        bizId = id == null ? 0 : id;
        resubmitOfId = readLong(handle.get(RouterPath.EXTRA_REQUEST_ID));
        start();
    }

    @Nullable
    private static Long readLong(@Nullable Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o instanceof String) {
            try {
                return Long.parseLong((String) o);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 直接设置页面参数（测试与非 Intent 场景） */
    public void init(@Nullable String bizType, long bizId, @Nullable Long resubmitOfId) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.resubmitOfId = resubmitOfId;
        start();
    }

    // ===== 加载 =====

    public void start() {
        if (resubmitOfId != null) {
            final long rid = resubmitOfId;
            load(repository.detail(rid).flatMap(d -> {
                String type = d.getBizType() != null ? d.getBizType() : bizType;
                long id = d.getBizId() != 0 ? d.getBizId() : bizId;
                return repository.getForm(type == null ? "" : type, id).map(f -> new Object[]{d, f});
            }), pair -> {
                ChangeRequestVO d = (ChangeRequestVO) pair[0];
                ChangeRequestFormVO f = (ChangeRequestFormVO) pair[1];
                bizType = f.getBizType() != null ? f.getBizType() : d.getBizType();
                bizId = f.getBizId() != 0 ? f.getBizId() : d.getBizId();
                form.setValue(f);
                prefill(d);
            });
            return;
        }
        if (bizType == null) {
            return;
        }
        load(repository.getForm(bizType, bizId), f -> {
            form.setValue(f);
            List<ChangeRequestFormVO.Field> fields = f.getFields();
            if (fields.size() == 1 && !f.isVoidAllowed()) {
                selectedField.setValue(fields.get(0).getFieldCode());
            }
            userEdited = false;
        });
    }

    /** 重新提交：带入上次的修改项、新值和原因；上次是作废申请时只带入原因 */
    private void prefill(@NonNull ChangeRequestVO last) {
        if (last.isVoid()) {
            selectedField.setValue(VOID_CODE);
        } else {
            selectedField.setValue(last.getFieldCode());
            newValue.setValue(last.getAfterValue() == null ? "" : last.getAfterValue());
        }
        reason.setValue(last.getReason() == null ? "" : last.getReason());
        rejectReason.setValue(last.getRejectReason());
        userEdited = false;
    }

    @Override
    public void retry() {
        resetContentLoaded();
        start();
    }

    // ===== 派生 =====

    public boolean isVoidSelected() {
        return VOID_CODE.equals(selectedField.getValue());
    }

    @Nullable
    public ChangeRequestFormVO.Field currentField() {
        ChangeRequestFormVO f = form.getValue();
        return f == null || isVoidSelected() ? null : f.fieldOf(selectedField.getValue());
    }

    /** 当前修改项的重新计算范围（作废时为作废的范围） */
    @NonNull
    public List<String> currentRecalcTags() {
        ChangeRequestFormVO f = form.getValue();
        if (f == null) {
            return Collections.emptyList();
        }
        if (isVoidSelected()) {
            return f.getVoidRecalcTags();
        }
        return f.recalcTagsOf(selectedField.getValue());
    }

    /** 同一条记录已有待审核申请 */
    public boolean hasPending() {
        ChangeRequestFormVO f = form.getValue();
        return f != null && f.getPendingRequestId() != null;
    }

    private void onEdited(@NonNull String field) {
        if (form.getValue() != null) {
            userEdited = true;
        }
        clearFieldError(field);
        idemKey = null;
        update();
    }

    private void update() {
        Boolean v = form.getValue() != null && !hasPending() && !Boolean.TRUE.equals(submitting.getValue());
        if (!v.equals(canSubmit.getValue())) {
            canSubmit.setValue(v);
        }
    }

    // ===== 对外 LiveData =====

    public LiveData<ChangeRequestFormVO> getForm() {
        return form;
    }

    public LiveData<String> getRejectReason() {
        return rejectReason;
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<ChangeRequestVO> getSubmitted() {
        return submitted;
    }

    public SingleLiveEvent<String> getFocusField() {
        return focusField;
    }

    public SingleLiveEvent<Boolean> getCloseForbidden() {
        return closeForbidden;
    }

    @Nullable
    public String getIdemKey() {
        return idemKey;
    }

    /** 已填写内容（返回时确认“放弃这次修改申请？”） */
    public boolean isDirty() {
        return userEdited && (!isBlank(newValue.getValue()) || !isBlank(reason.getValue())
                || selectedField.getValue() != null);
    }

    // ===== 操作 =====

    /** 选择修改项：新值框清空（修改原因保留） */
    public void selectField(@Nullable String fieldCode) {
        if (Objects.equals(fieldCode, selectedField.getValue())) {
            return;
        }
        selectedField.setValue(fieldCode);
        newValue.setValue("");
        focusField.setValue(VOID_CODE.equals(fieldCode) ? FIELD_REASON : FIELD_NEW_VALUE);
    }

    /** 本地校验：修改项必选；非作废时新值必填、不能与原值相同、不超过上限；修改原因 1–200 字 */
    @NonNull
    public Map<String, String> validate() {
        FormValidator v = new FormValidator();
        String code = selectedField.getValue();
        v.requiredValue(FIELD_FIELD_CODE, code, MSG_PICK_FIELD);
        if (code != null && !VOID_CODE.equals(code)) {
            ChangeRequestFormVO.Field field = currentField();
            String value = newValue.getValue() == null ? "" : newValue.getValue().trim();
            v.required(FIELD_NEW_VALUE, value, MSG_NEW_VALUE_EMPTY);
            if (field != null) {
                v.check(FIELD_NEW_VALUE, !sameValue(value, field.getValue()), MSG_SAME_VALUE);
                if ("NUMBER".equals(field.getInputType())) {
                    BigDecimal min = field.getMin();
                    BigDecimal max = field.getMax();
                    String unit = field.getValueUnit() == null ? "" : " " + field.getValueUnit();
                    String msg = max != null
                            ? field.getFieldName() + "不能超过 " + max.stripTrailingZeros().toPlainString() + unit
                            : field.getFieldName() + "不能小于 " + (min == null ? "0" : min.stripTrailingZeros().toPlainString()) + unit;
                    v.range(FIELD_NEW_VALUE, value, min, max, msg);
                }
            }
        }
        String r = reason.getValue() == null ? "" : reason.getValue().trim();
        v.required(FIELD_REASON, r, MSG_REASON_EMPTY);
        v.maxLength(FIELD_REASON, r, REASON_MAX, MSG_REASON_EMPTY);
        return v.validate();
    }

    private static boolean sameValue(@NonNull String input, @Nullable String original) {
        if (original == null) {
            return false;
        }
        BigDecimal a = FormValidator.parseDecimal(input);
        BigDecimal b = FormValidator.parseDecimal(original);
        if (a != null && b != null) {
            return a.compareTo(b) == 0;
        }
        return input.equals(original.trim());
    }

    public void submit() {
        ChangeRequestFormVO f = form.getValue();
        if (f == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        if (hasPending()) {
            showMessage(MSG_PENDING);
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            focusField.setValue(errors.keySet().iterator().next());
            return;
        }
        if (idemKey == null) {
            idemKey = IdemKey.newKey();
        }
        boolean isVoid = isVoidSelected();
        ChangeRequestSubmitRequest req = new ChangeRequestSubmitRequest(
                f.getBizType() != null ? f.getBizType() : (bizType == null ? "" : bizType),
                f.getBizId() != 0 ? f.getBizId() : bizId,
                isVoid ? ChangeRequestSubmitRequest.CHANGE_VOID : ChangeRequestSubmitRequest.CHANGE_UPDATE,
                isVoid ? null : selectedField.getValue(),
                isVoid ? null : (newValue.getValue() == null ? null : newValue.getValue().trim()),
                f.getRecordVersion(),
                reason.getValue() == null ? "" : reason.getValue().trim(),
                resubmitOfId);
        submitting.setValue(true);
        Single<ChangeRequestVO> source = repository.submit(req, idemKey);
        launch(source, vo -> {
            submitting.setValue(false);
            idemKey = null;
            userEdited = false;
            submitted.setValue(vo);
        }, e -> {
            submitting.setValue(false);
            if (!e.isNetwork()) {
                idemKey = null;
            }
            onSubmitError(e);
        });
    }

    private void onSubmitError(@NonNull ApiException e) {
        int code = e.getCode();
        if (e.isNetwork()) {
            showMessage(UiMessage.withAction(MSG_NETWORK, "重试", ACTION_RETRY));
            return;
        }
        if (ErrorCodes.isDataChanged(code)) {
            showMessage(UiMessage.withAction(MSG_CHANGED, "刷新", ACTION_REFRESH));
            return;
        }
        if (code == ErrorCodes.ROLE_FORBIDDEN) {
            showMessage(MSG_NO_PERMISSION);
            closeForbidden.setValue(true);
            return;
        }
        if (code == ErrorCodes.PENDING_EXISTS) {
            showMessage(MSG_PENDING);
            return;
        }
        handleError(e);
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            focusField.setValue(errors.keySet().iterator().next());
        }
        return true;
    }

    private void clearFieldError(@NonNull String field) {
        Map<String, String> old = fieldErrors.getValue();
        if (old != null && old.containsKey(field)) {
            Map<String, String> map = new LinkedHashMap<>(old);
            map.remove(field);
            fieldErrors.setValue(map);
        }
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}
