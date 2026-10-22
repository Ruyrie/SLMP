package com.zhimu.slmp.today.ui.detail;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.today.data.TodoRepository;
import com.zhimu.slmp.today.model.request.TodoCompleteRequest;
import com.zhimu.slmp.today.model.request.TodoIgnoreRequest;
import com.zhimu.slmp.today.model.vo.RelatedBatchVO;
import com.zhimu.slmp.today.model.vo.TodoDetailVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.InfoRow;
import com.zhimu.slmp.today.util.LinkNavigator;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.Texts;
import com.zhimu.slmp.today.util.TodoTypeStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * T04 待办详情（TodoDetailActivity），技术文档 2.4.3、2.6.4。
 * 标记完成（只限作业类、所有者和场长）与忽略（必须写原因）带幂等键：点对话框主按钮时生成，网络失败或超时重试沿用，
 * 成功、对话框关闭、40902 或 40903 后置空；超时先查 API-T07 再判断（已为 DONE 且 MANUAL、或已为 IGNORED 按成功处理）。
 */
public class TodoDetailViewModel extends BaseViewModel {

    private static final String TAG = "TodoDetailViewModel";

    public static final String ACTION_COMPLETE = "complete";
    public static final String ACTION_IGNORE = "ignore";
    public static final int REASON_MAX = 100;

    static final String MSG_MARKED = "已标记完成";
    static final String MSG_SUBMIT_FAILED = "网络连接不稳定，没有提交成功。请稍后重试。";
    static final String MSG_CONFLICT = "内容已更新，请刷新后再修改";
    static final String MSG_REASON_EMPTY = "请填写忽略原因";
    static final String MSG_REASON_TOO_LONG = "忽略原因不能超过 100 字";

    private final TodoRepository repository;
    private final TodayServiceImpl todoService;
    private final LinkNavigator linkNavigator;

    private final MutableLiveData<TodoDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<List<InfoRow>> infoRows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> canRegister = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canComplete = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canIgnore = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> ignoreInMenu = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<String> reasonError = new MutableLiveData<>();

    private final SingleLiveEvent<String> openMarkDone = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openIgnore = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> dismissDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<TodoChange> finishWithResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();

    private long todoId;
    /** 幂等键（点对话框主按钮时生成） */
    @Nullable
    private String idemKey;
    /** 失败后重新打开忽略对话框时带回的原因 */
    @Nullable
    private String pendingReason;

    @Inject
    public TodoDetailViewModel(@NonNull TodoRepository repository, @NonNull TodayServiceImpl todoService,
                               @NonNull LinkNavigator linkNavigator) {
        this.repository = repository;
        this.todoService = todoService;
        this.linkNavigator = linkNavigator;
        disposables.add(todoService.todoChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onTodoChanged, t -> Log.w(TAG, "待办回流订阅失败", t)));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Object id = handle.get(RouterPath.EXTRA_TODO_ID);
        long value = id instanceof Number ? ((Number) id).longValue() : parseLong(id);
        if (value > 0) {
            load(value);
        }
    }

    private static long parseLong(@Nullable Object o) {
        try {
            return o == null ? 0 : Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ===================== 对外 LiveData =====================

    public LiveData<TodoDetailVO> getDetail() {
        return detail;
    }

    public LiveData<List<InfoRow>> getInfoRows() {
        return infoRows;
    }

    public LiveData<Boolean> getCanRegister() {
        return canRegister;
    }

    public LiveData<Boolean> getCanComplete() {
        return canComplete;
    }

    public LiveData<Boolean> getCanIgnore() {
        return canIgnore;
    }

    public LiveData<Boolean> getIgnoreInMenu() {
        return ignoreInMenu;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<String> getReasonError() {
        return reasonError;
    }

    public SingleLiveEvent<String> getOpenMarkDone() {
        return openMarkDone;
    }

    public SingleLiveEvent<Void> getOpenIgnore() {
        return openIgnore;
    }

    public SingleLiveEvent<Void> getDismissDialog() {
        return dismissDialog;
    }

    public SingleLiveEvent<TodoChange> getFinishWithResult() {
        return finishWithResult;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    @Nullable
    public String getPendingReason() {
        return pendingReason;
    }

    @Nullable
    @VisibleForTesting
    String getIdemKey() {
        return idemKey;
    }

    // ===================== 加载 =====================

    public void load(long id) {
        todoId = id;
        load(repository.getDetail(id), this::applyDetail);
    }

    @Override
    public void retry() {
        if (todoId > 0) {
            load(todoId);
        }
    }

    /** 41001 进 T08；40306 用无权查看版式；其余同基类 */
    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.RECORD_DELETED) {
            navEvent.setValue(NavTarget.recordInvalid("待办详情", e.getMessage(), null, null));
            setUiState(UiState.error(e.getMessage(), e.getTraceId(), e.getCode()));
            return;
        }
        if (e.getCode() == ErrorCodes.FARM_FROZEN) {
            setUiState(UiState.noPermission(e.getMessage(), e.getCode()));
            return;
        }
        super.applyErrorState(e);
    }

    @VisibleForTesting
    void applyDetail(@NonNull TodoDetailVO vo) {
        todoId = vo.getTodoId();
        detail.setValue(vo);
        infoRows.setValue(buildRows(vo));
        boolean closed = TodoTypeStyle.isClosed(vo);
        canRegister.setValue(!closed && vo.isCanRegister());
        canComplete.setValue(!closed && vo.isCanComplete());
        canIgnore.setValue(!closed && vo.isCanIgnore());
        ignoreInMenu.setValue(!closed && vo.isCanIgnore() && TodoTypeStyle.CATEGORY_WORK.equals(vo.getCategory()));
    }

    /** 信息分组：值为空的行不生成 */
    @NonNull
    static List<InfoRow> buildRows(@NonNull TodoDetailVO vo) {
        List<InfoRow> rows = new ArrayList<>();
        add(rows, "来源模块", vo.getSourceModuleName(), null);
        add(rows, "截止时间", vo.getDueText(), vo.getDueHint());
        add(rows, "接收人", vo.getReceiversText(), vo.getReceiversHint());
        add(rows, "所属养殖场", vo.getFarmName(), null);
        if (!Texts.isEmpty(vo.getDetailLabel())) {
            add(rows, vo.getDetailLabel(), vo.getDetailValue(), null);
        }
        add(rows, "提醒", vo.getRemindText(), null);
        add(rows, "完成条件", vo.getCompleteCondition(), null);
        if (TodoTypeStyle.STATUS_DONE.equals(vo.getDisplayStatus())) {
            add(rows, "完成方式", vo.getCompleteWayText(), null);
        }
        if (TodoTypeStyle.STATUS_IGNORED.equals(vo.getDisplayStatus())) {
            add(rows, "忽略原因", vo.getIgnoreReason(), null);
            String by = vo.getIgnoredBy() == null ? null : vo.getIgnoredBy().getName();
            String at = vo.getIgnoredAt() == null ? null : DateFormatter.formatDateTime(vo.getIgnoredAt());
            add(rows, "忽略人", Texts.join(" · ", java.util.Arrays.asList(by, at)), null);
        }
        return rows;
    }

    private static void add(@NonNull List<InfoRow> rows, @Nullable String label, @Nullable String value,
                            @Nullable String hint) {
        if (!Texts.isEmpty(label) && !Texts.isEmpty(value)) {
            rows.add(new InfoRow(label, value, Texts.isEmpty(hint) ? null : hint));
        }
    }

    // ===================== 点击 =====================

    public void onRegisterClick() {
        TodoDetailVO vo = detail.getValue();
        if (vo != null) {
            launch(linkNavigator.forRegister(vo), navEvent::setValue);
        }
    }

    public void onCompleteClick() {
        TodoDetailVO vo = detail.getValue();
        if (vo != null && Boolean.TRUE.equals(canComplete.getValue())) {
            openMarkDone.setValue(vo.getMarkDoneTip());
        }
    }

    public void onIgnoreClick() {
        if (Boolean.TRUE.equals(canIgnore.getValue())) {
            pendingReason = null;
            reasonError.setValue(null);
            openIgnore.call();
        }
    }

    public void onRelatedBatchClick() {
        TodoDetailVO vo = detail.getValue();
        RelatedBatchVO batch = vo == null ? null : vo.getRelatedBatch();
        if (batch == null) {
            return;
        }
        if (batch.getLink() != null && batch.getLink().getRoute() != null) {
            navEvent.setValue(new NavTarget(batch.getLink().getRoute(), batch.getLink().getParams(), null));
        } else {
            String route = "WITHDRAWAL_END".equals(vo.getTodoType()) ? "D05" : "L06";
            navEvent.setValue(NavTarget.of(route, RouterPath.EXTRA_BATCH_ID, batch.getBatchId()));
        }
    }

    // ===================== 标记完成 =====================

    public void confirmComplete() {
        TodoDetailVO vo = detail.getValue();
        if (vo == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        if (idemKey == null) {
            idemKey = IdemKey.newKey();
        }
        submitting.setValue(true);
        launch(repository.complete(vo.getTodoId(), idemKey, new TodoCompleteRequest(vo.getVersion())),
                result -> onCompleted(result), e -> onSubmitError(ACTION_COMPLETE, e));
    }

    private void onCompleted(@NonNull TodoDetailVO result) {
        submitting.setValue(false);
        idemKey = null;
        applyDetail(result);
        dismissDialog.call();
        showMessage(MSG_MARKED);
        todoService.notifyTodoChanged(new TodoChange(result.getTodoId(), null, null, result.getFarmId()));
    }

    // ===================== 忽略 =====================

    /** 去首尾空格后 1–100 字；空“请填写忽略原因”，超长“忽略原因不能超过 100 字” */
    @Nullable
    static String validateReason(@Nullable String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            return MSG_REASON_EMPTY;
        }
        if (r.codePointCount(0, r.length()) > REASON_MAX) {
            return MSG_REASON_TOO_LONG;
        }
        return null;
    }

    public void confirmIgnore(@Nullable String reason) {
        TodoDetailVO vo = detail.getValue();
        if (vo == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        String r = reason == null ? "" : reason.trim();
        pendingReason = r;
        String error = validateReason(r);
        if (error != null) {
            reasonError.setValue(error);
            openIgnore.call();
            return;
        }
        reasonError.setValue(null);
        if (idemKey == null) {
            idemKey = IdemKey.newKey();
        }
        submitting.setValue(true);
        launch(repository.ignore(vo.getTodoId(), idemKey, new TodoIgnoreRequest(r, vo.getVersion())),
                this::onIgnored, e -> onSubmitError(ACTION_IGNORE, e));
    }

    private void onIgnored(@NonNull TodoDetailVO result) {
        submitting.setValue(false);
        idemKey = null;
        pendingReason = null;
        applyDetail(result);
        dismissDialog.call();
        TodoChange change = new TodoChange(result.getTodoId(), null, null, result.getFarmId());
        todoService.notifyTodoChanged(change);
        finishWithResult.setValue(change);
    }

    // ===================== 失败处理 =====================

    private void onSubmitError(@NonNull String action, @NonNull ApiException e) {
        int code = e.getCode();
        if (e.isNetwork()) {
            verifyAfterTimeout(action);
            return;
        }
        submitting.setValue(false);
        if (ErrorCodes.isDataChanged(code)) {
            idemKey = null;
            pendingReason = null;
            dismissDialog.call();
            showMessage(MSG_CONFLICT);
            retry();
            return;
        }
        if (code == ErrorCodes.RECORD_DELETED) {
            idemKey = null;
            navEvent.setValue(NavTarget.recordInvalid("待办详情", e.getMessage(), null, null));
            return;
        }
        if (ACTION_IGNORE.equals(action) && (code == ErrorCodes.REASON_EMPTY || code == ErrorCodes.PARAM_INVALID)) {
            reasonError.setValue(code == ErrorCodes.REASON_EMPTY ? MSG_REASON_EMPTY : MSG_REASON_TOO_LONG);
            openIgnore.call();
            return;
        }
        if (code == ErrorCodes.ROLE_FORBIDDEN) {
            showMessage(e.getMessage());
            IFarmContextService farm = ServiceHub.get(IFarmContextService.class);
            if (farm != null) {
                disposables.add(farm.refreshContext().subscribeOn(Schedulers.io())
                        .subscribe(() -> { }, t -> Log.i(TAG, "刷新角色失败：" + t.getMessage())));
            }
            retry();
            return;
        }
        if (ErrorCodes.isAuthHandled(code)) {
            return;
        }
        showMessage(e.getMessage());
    }

    /** 超时或网络失败：先查 API-T07，已处理按成功；否则保留对话框与原因，重试沿用幂等键 */
    public void verifyAfterTimeout(@NonNull String action) {
        launch(repository.getDetail(todoId), vo -> {
            boolean done = ACTION_COMPLETE.equals(action)
                    && TodoTypeStyle.STATUS_DONE.equals(vo.getDisplayStatus()) && "MANUAL".equals(vo.getCompleteWay());
            boolean ignored = ACTION_IGNORE.equals(action) && TodoTypeStyle.STATUS_IGNORED.equals(vo.getDisplayStatus());
            if (done) {
                onCompleted(vo);
            } else if (ignored) {
                onIgnored(vo);
            } else {
                failKeepDialog(action);
            }
        }, e -> failKeepDialog(action));
    }

    private void failKeepDialog(@NonNull String action) {
        submitting.setValue(false);
        showMessage(MSG_SUBMIT_FAILED);
        TodoDetailVO vo = detail.getValue();
        if (ACTION_IGNORE.equals(action)) {
            openIgnore.call();
        } else if (vo != null) {
            openMarkDone.setValue(vo.getMarkDoneTip());
        }
    }

    /** 用户关闭对话框（取消、返回键）：幂等键与错误置空 */
    public void onDialogDismissed() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        idemKey = null;
        pendingReason = null;
        reasonError.setValue(null);
    }

    /** 登记回流：本待办变化时重新加载 */
    public void onTodoChanged(@NonNull TodoChange change) {
        if (change.getTodoId() != null && change.getTodoId() == todoId && !Boolean.TRUE.equals(submitting.getValue())
                && detail.getValue() != null && !TodoTypeStyle.isClosed(detail.getValue())) {
            launch(repository.getDetail(todoId), this::applyDetail, e -> Log.i(TAG, "刷新失败：" + e.getMessage()));
        }
    }
}
