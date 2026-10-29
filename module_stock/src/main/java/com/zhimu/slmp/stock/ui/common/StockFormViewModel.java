package com.zhimu.slmp.stock.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.RequestStatusVO;
import com.zhimu.slmp.stock.util.SubmitGuard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 本模块表单页 ViewModel 基类（技术准备文档 2.4 通用约定）：
 * <ul>
 *     <li>fieldErrors（键为 JSON 字段名）、submitting；40001 的 field_errors 写入输入框</li>
 *     <li>{@link #submitGuarded}：幂等键 SubmitGuard；读超时与 40901 查 API-M42（DONE 取回单据按成功处理，PROCESSING 每 2 秒、最多 5 次，
 *     NOT_FOUND 提示“提交没有成功，请重试”并保留键）；网络断开提示“网络连接已断开，已填写的内容已保留”+“重试”</li>
 *     <li>草稿（form_draft）：onStop 有改动且未放弃才保存，再次进入询问是否恢复；成功或放弃后删除</li>
 *     <li>返回拦截：有改动时“放弃这次填写？”</li>
 * </ul>
 */
public abstract class StockFormViewModel extends BaseViewModel {

    public static final String MSG_NETWORK_KEEP = "网络连接已断开，已填写的内容已保留";
    public static final String MSG_NOT_SAVED = "提交没有成功，请重试";
    public static final String MSG_DRAFT = "上次没有提交的内容已保存为草稿";
    public static final String MSG_VERSION = "数据已被他人修改，请刷新后重试";
    /** 新建表单点“刷新”后：已重拉选项与结存，已填内容保留 */
    public static final String MSG_REFRESHED = "已刷新结存，已填内容保留，请核对后重新提交";
    public static final String ACTION_RETRY = "stock_retry";
    public static final String ACTION_RESTORE_DRAFT = "stock_restore_draft";
    public static final String ACTION_REFRESH = "stock_refresh";

    /** 输入防抖（供应商、退回对象建议与分摊预览，300 ms；测试中改为 0） */
    @androidx.annotation.VisibleForTesting
    public static long debounceMs = 300;

    protected final StockRepository repository;
    protected final StockDraftRepository drafts;
    protected final SubmitGuard guard = new SubmitGuard();
    private final Gson gson = GsonFactory.get();

    protected final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    protected final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final SingleLiveEvent<Void> showDiscard = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> focusField = new SingleLiveEvent<>();

    protected boolean finished;
    @Nullable
    private String cleanSnapshot;
    @Nullable
    private DraftData pendingDraft;
    @Nullable
    private Runnable retryAction;

    /** 草稿内容：表单字段原文 + 待确认的幂等键 */
    static final class DraftData {
        Map<String, String> fields;
        String idemKey;
        String idemSnapshot;
    }

    protected StockFormViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts) {
        this.repository = repository;
        this.drafts = drafts;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        guard.attach(handle);
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public SingleLiveEvent<Void> getShowDiscard() {
        return showDiscard;
    }

    /** 校验未通过时第一个错误字段（页面滚动到可见并聚焦） */
    public SingleLiveEvent<String> getFocusField() {
        return focusField;
    }

    // ===================== 字段错误 =====================

    /**
     * 输入框文字是否真的变了（与上次记下的比较，null 与空串视为相同）。双向绑定在程序设值后会把同样的文字再回写一次，
     * 不能当作用户修改去清除错误：库存冲突提示“库存已变化，当前结存…”要一直显示到用户改数量或重新提交。
     */
    protected static final class TextChange {
        @NonNull
        private String last = "";

        public TextChange() {
        }

        /** 记下新文字；与上次不同时返回 true */
        public boolean update(@Nullable String now) {
            String v = now == null ? "" : now;
            if (v.equals(last)) {
                return false;
            }
            last = v;
            return true;
        }
    }

    protected void setErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        if (!errors.isEmpty()) {
            focusField.setValue(errors.keySet().iterator().next());
        }
    }

    public void clearFieldError(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(m);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
    }

    protected void putFieldError(@NonNull String field, @NonNull String message) {
        Map<String, String> m = fieldErrors.getValue();
        Map<String, String> copy = new LinkedHashMap<>(m == null ? Collections.emptyMap() : m);
        copy.put(field, message);
        fieldErrors.setValue(copy);
        focusField.setValue(field);
    }

    @Nullable
    public String errorOf(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        return m == null ? null : m.get(field);
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        setErrors(errors);
        return true;
    }

    // ===================== 提交 =====================

    /**
     * 带幂等键提交。
     *
     * @param request   请求体（决定幂等键是否沿用）
     * @param call      用幂等键发请求
     * @param fetchDone API-M42 返回 DONE 后取回单据（按 biz_type、biz_id 调详情接口），为 null 时不查询
     * @param onSuccess 成功（含超时后查询确认成功）
     * @param onFail    业务错误（已清除幂等键、submitting 已复位）；网络与超时由基类处理
     */
    protected <T> void submitGuarded(@NonNull Object request, @NonNull Function<String, Single<T>> call,
                                     @Nullable Function<RequestStatusVO, Single<T>> fetchDone,
                                     @NonNull Consumer<T> onSuccess, @NonNull Consumer<ApiException> onFail) {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        submitting.setValue(true);
        String key = guard.keyFor(request);
        retryAction = () -> submitGuarded(request, call, fetchDone, onSuccess, onFail);
        launch(call.apply(key), r -> done(r, onSuccess), e -> {
            if (fetchDone != null && (SubmitGuard.isTimeout(e) || e.getCode() == ErrorCodes.DUPLICATE_SUBMIT)) {
                poll(key, 0, fetchDone, onSuccess);
                return;
            }
            submitting.setValue(false);
            if (e.isNetwork()) {
                showMessage(UiMessage.withAction(e.keptMessage(MSG_NETWORK_KEEP), "重试", ACTION_RETRY));
                return;
            }
            guard.clear();
            onFail.accept(e);
        });
    }

    private <T> void done(T result, @NonNull Consumer<T> onSuccess) {
        submitting.setValue(false);
        guard.clear();
        retryAction = null;
        onSuccess.accept(result);
    }

    private <T> void poll(@NonNull String key, int attempt, @NonNull Function<RequestStatusVO, Single<T>> fetchDone,
                          @NonNull Consumer<T> onSuccess) {
        long delay = attempt == 0 ? 0 : SubmitGuard.pollIntervalMs;
        launch(Single.timer(delay, TimeUnit.MILLISECONDS, Schedulers.computation())
                .flatMap(t -> repository.getRequestStatus(key)), st -> {
            if (StockEnums.REQ_DONE.equals(st.status)) {
                launch(fetchDone.apply(st), r -> done(r, onSuccess), err -> notSaved());
            } else if (StockEnums.REQ_PROCESSING.equals(st.status) && attempt + 1 < SubmitGuard.MAX_POLLS) {
                poll(key, attempt + 1, fetchDone, onSuccess);
            } else {
                notSaved();
            }
        }, err -> notSaved());
    }

    private void notSaved() {
        submitting.setValue(false);
        showMessage(MSG_NOT_SAVED);
    }

    /** Snackbar“重试”：同一请求、同一幂等键重发 */
    public void retrySubmit() {
        Runnable r = retryAction;
        if (r != null) {
            r.run();
        }
    }

    /** 登记成功：删除草稿，通知首页刷新待办（服务为空时跳过） */
    protected void onSubmitted(@Nullable Long todoId, @Nullable String bizType, @Nullable Long bizId,
                               long farmId) {
        finished = true;
        guard.clear();
        deleteDraft();
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(todoId, bizType, bizId, farmId));
        }
    }

    // ===================== 返回与草稿 =====================

    /** 页面编号（form_draft 的 page_code）；不保存草稿的页面返回 null */
    @Nullable
    protected String pageCode() {
        return null;
    }

    /** 草稿 biz_key：新增“new”、修改为单据编号；当前状态不保存草稿时返回 null */
    @Nullable
    protected String draftBizKey() {
        return null;
    }

    /** 当前是否处于可编辑状态（只读状态不拦截返回、不存草稿） */
    protected boolean isEditing() {
        return true;
    }

    /** 表单字段原文（草稿与“是否有改动”的比较） */
    @NonNull
    protected Map<String, String> draftFields() {
        return Collections.emptyMap();
    }

    /** 恢复草稿字段 */
    protected void applyDraft(@NonNull Map<String, String> fields) {
    }

    /** 记录“未改动”的快照（加载完成、回填后调用） */
    protected void markClean() {
        cleanSnapshot = gson.toJson(draftFields());
    }

    public boolean isDirty() {
        return isEditing() && cleanSnapshot != null && !cleanSnapshot.equals(gson.toJson(draftFields()));
    }

    /** 返回：有改动时弹“放弃这次填写？”，返回 true 表示已拦截 */
    public boolean onBack() {
        if (!finished && isDirty()) {
            showDiscard.call();
            return true;
        }
        return false;
    }

    public void onDiscardConfirmed() {
        finished = true;
        guard.clear();
        deleteDraft();
    }

    /** onStop：有改动且未放弃时保存草稿 */
    public void saveDraft() {
        String page = pageCode();
        String key = draftBizKey();
        if (finished || page == null || key == null || !isDirty()) {
            return;
        }
        DraftData d = new DraftData();
        d.fields = new LinkedHashMap<>(draftFields());
        d.idemKey = guard.current();
        d.idemSnapshot = guard.snapshot();
        disposables.add(drafts.saveDraft(page, key, gson.toJson(d)).subscribe(() -> {
        }, t -> {
        }));
    }

    /** 加载完成后检查草稿：有则询问是否恢复 */
    protected void checkDraft() {
        String page = pageCode();
        String key = draftBizKey();
        if (page == null || key == null) {
            return;
        }
        disposables.add(drafts.getDraft(page, key)
                .map(json -> gson.fromJson(json, DraftData.class))
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(d -> {
                    if (d != null && d.fields != null && !d.fields.isEmpty()) {
                        pendingDraft = d;
                        showMessage(UiMessage.withAction(MSG_DRAFT, "恢复", ACTION_RESTORE_DRAFT));
                    }
                }, t -> {
                }));
    }

    /** Snackbar“恢复” */
    public void restoreDraft() {
        DraftData d = pendingDraft;
        pendingDraft = null;
        if (d == null || d.fields == null) {
            return;
        }
        applyDraft(d.fields);
        guard.restore(d.idemKey, d.idemSnapshot);
    }

    protected void deleteDraft() {
        pendingDraft = null;
        String page = pageCode();
        String key = draftBizKey();
        if (page == null || key == null) {
            return;
        }
        disposables.add(drafts.deleteDraft(page, key).subscribe(() -> {
        }, t -> {
        }));
    }

    @Nullable
    public String pendingIdemKey() {
        return guard.current();
    }

    // ===================== 小工具 =====================

    @NonNull
    protected static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    @NonNull
    protected static String trim(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    @Nullable
    protected static String emptyToNull(@Nullable String s) {
        String t = trim(s);
        return t.isEmpty() ? null : t;
    }
}
