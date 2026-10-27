package com.zhimu.slmp.feed.ui.register;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.UserBriefVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.feed.FeedExtras;
import com.zhimu.slmp.feed.data.FeedRepository;
import com.zhimu.slmp.feed.model.request.FeedRecordCreateRequest;
import com.zhimu.slmp.feed.model.request.FeedRecordUpdateRequest;
import com.zhimu.slmp.feed.model.request.FeedRecordVoidRequest;
import com.zhimu.slmp.feed.model.vo.FeedOptionBriefVO;
import com.zhimu.slmp.feed.model.vo.FeedRecordDetailVO;
import com.zhimu.slmp.feed.model.vo.FeedTaskFormVO;
import com.zhimu.slmp.feed.ui.common.FeedDraftStore;
import com.zhimu.slmp.feed.ui.common.FeedErrors;
import com.zhimu.slmp.feed.ui.common.FeedIdemKeeper;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.util.FeedCalc;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.network.model.ApiResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * G02 登记投喂（新增、修改、只读）。入参 EXTRA_TASK_ID（待投喂、已逾期）或 RouterPath.EXTRA_RECORD_ID（已登记）。
 * <ul>
 *     <li>新增：API-G03 带出任务、计划量与饲料；已登记（record_id）改调 API-G05；不能登记时 blockText 只读</li>
 *     <li>修改（所有者、场长当天记录）：API-G05 + API-G03（饲料与执行人选项，待定事项 2）→ API-G06；菜单作废 API-G07</li>
 *     <li>只读：员工已确认、非当天（底部“提交修改申请”进入 U01）、已作废、审核中</li>
 * </ul>
 * 实时计算偏差与投喂异常（FeedCalc）、超结存拦截（修改同一饲料按差值）；确认投喂带幂等键，读超时先查 API-G08，
 * saved 为真按成功处理，否则同键重发；新增模式离开页面时保存草稿（form_draft，page_code G02，biz_key task:{task_id}）。
 */
public class FeedRegisterViewModel extends BaseViewModel {

    public static final String PAGE_CODE = "G02";
    public static final String F_FEED = "feed_id";
    public static final String F_ACTUAL = "actual_qty";
    public static final String F_EXECUTOR = "executor_id";
    public static final String F_REMARK = "remark";
    public static final int MAX_REMARK = 100;

    static final String MSG_DONE = "已登记投喂，待办已自动完成";
    static final String MSG_SAVED = "已修改投喂记录，饲料出库已同步更正";
    static final String MSG_VOIDED = "已作废投喂记录，饲料出库已同步作废";
    static final String MSG_DRAFT = "上次没有提交的投喂登记已保存为草稿";

    private final FeedRepository repository;
    private final FarmContext farmContext;
    private final FeedDraftStore drafts;
    private final FeedIdemKeeper idem = new FeedIdemKeeper();

    private final MutableLiveData<FeedRegisterMode> mode = new MutableLiveData<>(FeedRegisterMode.CREATE);
    private final MutableLiveData<FeedTaskFormVO> formCtx = new MutableLiveData<>();
    private final MutableLiveData<FeedRecordDetailVO> record = new MutableLiveData<>();
    private final MutableLiveData<FeedTaskCard> taskCard = new MutableLiveData<>();
    private final MutableLiveData<BigDecimal> planQty = new MutableLiveData<>();
    private final MutableLiveData<String> planText = new MutableLiveData<>("");
    private final MutableLiveData<String> planSourceText = new MutableLiveData<>("");
    private final MutableLiveData<List<FeedOptionBriefVO>> feedOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<FeedOptionBriefVO> selectedFeed = new MutableLiveData<>();
    private final MutableLiveData<String> actualQty = new MutableLiveData<>("");
    private final MutableLiveData<String> stockHint = new MutableLiveData<>("");
    private final MutableLiveData<BigDecimal> deviationRatio = new MutableLiveData<>();
    private final MutableLiveData<String> abnormalText = new MutableLiveData<>();
    private final MutableLiveData<String> stockError = new MutableLiveData<>();
    private final MutableLiveData<String> outboundText = new MutableLiveData<>("");
    private final MutableLiveData<String> outboundError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> executorVisible = new MutableLiveData<>(false);
    private final MutableLiveData<List<UserBriefVO>> executorOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<UserBriefVO> selectedExecutor = new MutableLiveData<>();
    private final MutableLiveData<String> remark = new MutableLiveData<>("");
    private final MutableLiveData<String> proxyHint = new MutableLiveData<>();
    private final MutableLiveData<String> blockText = new MutableLiveData<>();
    private final MutableLiveData<String> readonlyNotice = new MutableLiveData<>();
    private final MutableLiveData<List<String[]>> readonlyRows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> canVoid = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canRequestChange = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canStockIn = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());

    private final SingleLiveEvent<Void> focusActualQty = new SingleLiveEvent<>();
    private final SingleLiveEvent<UiMessage> closeWithResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDiscardDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> showVoidDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Bundle> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openStockIn = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> redirectToday = new SingleLiveEvent<>();

    @Nullable
    private Long taskId;
    @Nullable
    private Long recordId;
    @Nullable
    private Long penId;
    @Nullable
    private String initialSnapshot;
    @Nullable
    private Draft pendingDraft;
    private boolean finished;
    private boolean resentAfterLookup;

    /** 草稿内容：请求字段 + 待确认的幂等键 */
    static final class Draft {
        Long feedId;
        String actualQty;
        Long executorId;
        String remark;
        String idemKey;
        String idemSnapshot;
    }

    /** 修改模式的初始化数据 */
    private static final class RecordInit {
        final FeedRecordDetailVO record;
        @Nullable
        final FeedTaskFormVO form;

        RecordInit(@NonNull FeedRecordDetailVO record, @Nullable FeedTaskFormVO form) {
            this.record = record;
            this.form = form;
        }
    }

    @Inject
    public FeedRegisterViewModel(@NonNull FeedRepository repository, @NonNull FarmContext farmContext,
                                 @NonNull AppDatabase database) {
        this.repository = repository;
        this.farmContext = farmContext;
        this.drafts = new FeedDraftStore(database.formDraftDao());
        canSubmit.setValue(false);
        canSubmit.addSource(actualQty, v -> recompute());
        canSubmit.addSource(selectedFeed, v -> recompute());
        canSubmit.addSource(selectedExecutor, v -> recompute());
        canSubmit.addSource(submitting, v -> recompute());
        canSubmit.addSource(mode, v -> recompute());
        canSubmit.addSource(blockText, v -> recompute());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        penId = FeedExtras.readId(handle.get(RouterPath.EXTRA_PEN_ID));
        init(FeedExtras.readId(handle.get(RouterPath.EXTRA_TASK_ID)),
                FeedExtras.readId(handle.get(RouterPath.EXTRA_RECORD_ID)));
    }

    // ===================== 对外 =====================

    public LiveData<FeedRegisterMode> getMode() {
        return mode;
    }

    public LiveData<FeedTaskFormVO> getFormCtx() {
        return formCtx;
    }

    public LiveData<FeedRecordDetailVO> getRecord() {
        return record;
    }

    public LiveData<FeedTaskCard> getTaskCard() {
        return taskCard;
    }

    public LiveData<BigDecimal> getPlanQty() {
        return planQty;
    }

    /** 计划量框的值“160 千克” */
    public LiveData<String> getPlanText() {
        return planText;
    }

    public LiveData<String> getPlanSourceText() {
        return planSourceText;
    }

    public LiveData<List<FeedOptionBriefVO>> getFeedOptions() {
        return feedOptions;
    }

    public MutableLiveData<FeedOptionBriefVO> getSelectedFeed() {
        return selectedFeed;
    }

    public MutableLiveData<String> getActualQty() {
        return actualQty;
    }

    public LiveData<String> getStockHint() {
        return stockHint;
    }

    public LiveData<BigDecimal> getDeviationRatio() {
        return deviationRatio;
    }

    public LiveData<String> getAbnormalText() {
        return abnormalText;
    }

    public LiveData<String> getStockError() {
        return stockError;
    }

    public LiveData<String> getOutboundText() {
        return outboundText;
    }

    public LiveData<String> getOutboundError() {
        return outboundError;
    }

    public LiveData<Boolean> getExecutorVisible() {
        return executorVisible;
    }

    public LiveData<List<UserBriefVO>> getExecutorOptions() {
        return executorOptions;
    }

    public MutableLiveData<UserBriefVO> getSelectedExecutor() {
        return selectedExecutor;
    }

    public MutableLiveData<String> getRemark() {
        return remark;
    }

    public LiveData<String> getProxyHint() {
        return proxyHint;
    }

    public LiveData<String> getBlockText() {
        return blockText;
    }

    public LiveData<String> getReadonlyNotice() {
        return readonlyNotice;
    }

    /** 只读卡片的“标签—值”行 */
    public LiveData<List<String[]>> getReadonlyRows() {
        return readonlyRows;
    }

    public LiveData<Boolean> getCanVoid() {
        return canVoid;
    }

    public LiveData<Boolean> getCanRequestChange() {
        return canRequestChange;
    }

    public LiveData<Boolean> getCanStockIn() {
        return canStockIn;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public SingleLiveEvent<Void> getFocusActualQty() {
        return focusActualQty;
    }

    public SingleLiveEvent<UiMessage> getCloseWithResult() {
        return closeWithResult;
    }

    public SingleLiveEvent<Void> getShowDiscardDialog() {
        return showDiscardDialog;
    }

    /** 作废确认对话框正文 */
    public SingleLiveEvent<String> getShowVoidDialog() {
        return showVoidDialog;
    }

    public SingleLiveEvent<Bundle> getOpenChangeRequest() {
        return openChangeRequest;
    }

    public SingleLiveEvent<Long> getOpenStockIn() {
        return openStockIn;
    }

    /** 没有任务与记录参数（如 T02 快捷登记）时转到 G01（可带栏舍筛选） */
    public SingleLiveEvent<Long> getRedirectToday() {
        return redirectToday;
    }

    /** 以接口中的当前成员角色为准，兼容旧接口给员工返回空选项列表。 */
    public boolean isStaffView() {
        FeedTaskFormVO f = formCtx.getValue();
        String role = f != null && f.getCurrentUser() != null ? f.getCurrentUser().getRole() : null;
        if (role == null) role = farmContext.role();
        if ("STAFF".equals(role)) return true;
        if ("OWNER".equals(role) || "MANAGER".equals(role)) return false;
        if (f != null) {
            return f.getExecutorOptions() == null;
        }
        return farmContext.isStaff();
    }

    // ===================== 初始化 =====================

    /** API-G03 / API-G05（修改模式另取 API-G03 的选项）；新增模式查草稿 */
    public void init(@Nullable Long taskId, @Nullable Long recordId) {
        this.taskId = taskId;
        this.recordId = recordId;
        if (taskId == null && recordId == null) {
            finished = true;
            redirectToday.setValue(penId);
            return;
        }
        disposables.add(drafts.deleteExpired().subscribe(() -> {
        }, t -> {
        }));
        if (recordId != null) {
            load(recordSource(recordId), this::applyRecord);
        } else {
            load(repository.getTaskForm(taskId).flatMap(form -> {
                Long rid = form.getRecordId();
                if (rid == null) {
                    return Single.just(new Object[]{form, null});
                }
                return repository.getRecord(rid).map(r -> new Object[]{form, r});
            }), pair -> {
                FeedTaskFormVO form = (FeedTaskFormVO) pair[0];
                FeedRecordDetailVO r = (FeedRecordDetailVO) pair[1];
                if (r != null) {
                    this.recordId = r.getRecordId();
                    discardDraft();
                    applyRecord(new RecordInit(r, r.isEditable() ? form : null));
                } else {
                    applyForm(form);
                }
            });
        }
    }

    @NonNull
    private Single<RecordInit> recordSource(long id) {
        return repository.getRecord(id).flatMap(r -> r.isEditable() && r.getTaskId() > 0
                ? repository.getTaskForm(r.getTaskId()).map(f -> new RecordInit(r, f))
                .onErrorReturnItem(new RecordInit(r, null))
                : Single.just(new RecordInit(r, null)));
    }

    @Override
    public void retry() {
        init(taskId, recordId);
    }

    /** 新增模式 */
    private void applyForm(@NonNull FeedTaskFormVO f) {
        formCtx.setValue(f);
        record.setValue(null);
        taskCard.setValue(FeedTaskCard.of(f));
        planQty.setValue(f.getPlanQty());
        planText.setValue(FeedFormat.kg(f.getPlanQty()));
        feedOptions.setValue(f.getFeedOptions());
        canStockIn.setValue(f.isCanStockIn());
        canVoid.setValue(false);
        canRequestChange.setValue(false);
        readonlyNotice.setValue(null);
        List<UserBriefVO> executors = f.getExecutorOptions();
        boolean staff = isStaffView();
        executorVisible.setValue(!staff);
        executorOptions.setValue(staff || executors == null ? Collections.emptyList() : executors);
        selectedFeed.setValue(matchFeed(f.getFeedOptions(), f.getDefaultFeed()));
        if (staff) {
            selectedExecutor.setValue(f.getCurrentUser());
        } else if (executors != null) {
            UserBriefVO def = f.getDefaultExecutor() != null ? matchUser(executors, f.getDefaultExecutor().getUserId())
                    : null;
            selectedExecutor.setValue(def);
        } else {
            selectedExecutor.setValue(null);
        }
        mode.setValue(FeedRegisterMode.CREATE);
        if (!f.isCanRegister()) {
            blockText.setValue(blockTextOf(f.getBlockReason()));
            discardDraft();
        } else {
            blockText.setValue(null);
        }
        initialSnapshot = snapshot();
        recompute();
        if (f.isCanRegister()) {
            focusActualQty.call();
            checkDraft();
        }
    }

    /** 修改或只读模式 */
    private void applyRecord(@NonNull RecordInit init) {
        FeedRecordDetailVO r = init.record;
        FeedTaskFormVO f = init.form;
        record.setValue(r);
        formCtx.setValue(f);
        taskCard.setValue(FeedTaskCard.of(r));
        planQty.setValue(r.getPlanQty());
        planText.setValue(FeedFormat.kg(r.getPlanQty()));
        blockText.setValue(null);
        boolean edit = r.isEditable() && FeedRecordDetailVO.STATUS_NORMAL.equals(r.getStatus());
        canVoid.setValue(edit && r.isCanVoid());
        canRequestChange.setValue(!edit && r.isCanRequestChange());
        canStockIn.setValue(f != null && f.isCanStockIn());
        if (edit) {
            List<FeedOptionBriefVO> options = f != null && !f.getFeedOptions().isEmpty() ? f.getFeedOptions()
                    : r.getFeed() == null ? Collections.emptyList() : Collections.singletonList(r.getFeed());
            feedOptions.setValue(options);
            selectedFeed.setValue(matchFeed(options, r.getFeed()));
            actualQty.setValue(r.getActualQty() == null ? "" : r.getActualQty().stripTrailingZeros().toPlainString());
            List<UserBriefVO> executors = f == null ? null : f.getExecutorOptions();
            executorVisible.setValue(executors != null && !executors.isEmpty());
            List<UserBriefVO> list = executors == null ? new ArrayList<>() : new ArrayList<>(executors);
            if (r.getExecutor() != null && matchUser(list, r.getExecutor().getUserId()) == null) {
                list.add(r.getExecutor());
            }
            executorOptions.setValue(list);
            selectedExecutor.setValue(r.getExecutor() == null ? null : matchUser(list, r.getExecutor().getUserId()));
            remark.setValue(r.getRemark() == null ? "" : r.getRemark());
            readonlyNotice.setValue(null);
            mode.setValue(FeedRegisterMode.EDIT);
        } else {
            readonlyNotice.setValue(readonlyTextOf(r));
            readonlyRows.setValue(readonlyRowsOf(r));
            mode.setValue(FeedRegisterMode.READONLY);
        }
        initialSnapshot = snapshot();
        recompute();
    }

    @Nullable
    private static FeedOptionBriefVO matchFeed(@NonNull List<FeedOptionBriefVO> options,
                                               @Nullable FeedOptionBriefVO target) {
        if (target == null) {
            return null;
        }
        for (FeedOptionBriefVO o : options) {
            if (o.getFeedId() == target.getFeedId()) {
                return o;
            }
        }
        return target;
    }

    @Nullable
    private static UserBriefVO matchUser(@NonNull List<UserBriefVO> list, long userId) {
        for (UserBriefVO u : list) {
            if (u.getUserId() == userId) {
                return u;
            }
        }
        return null;
    }

    @NonNull
    static String blockTextOf(@Nullable String reason) {
        if (FeedTaskFormVO.BLOCK_BATCH_ENDED.equals(reason)) {
            return "批次已结束，不能再登记投喂";
        }
        if (FeedTaskFormVO.BLOCK_MISSED.equals(reason)) {
            return "已超过补登期限，这餐已记为未执行";
        }
        if (FeedTaskFormVO.BLOCK_DONE.equals(reason)) {
            return "这餐已经登记过投喂";
        }
        return "这条饲喂任务已失效";
    }

    /**
     * 只读提示：员工已确认、非当天（服务端 2.5.3 文案）；已作废、审核中的文案 UI 文档未给出（待定事项 4），暂用下面的写法。
     */
    @NonNull
    static String readonlyTextOf(@NonNull FeedRecordDetailVO r) {
        String reason = FeedRecordDetailVO.STATUS_VOID.equals(r.getStatus()) ? FeedRecordDetailVO.READONLY_VOIDED
                : r.getReadonlyReason();
        if (FeedRecordDetailVO.READONLY_NOT_TODAY.equals(reason)) {
            return "这条记录不是今天登记的，修改需要提交修改申请，由管理员审核。";
        }
        if (FeedRecordDetailVO.READONLY_VOIDED.equals(reason)) {
            return "这条投喂记录已作废，关联的饲料出库已同步作废。";
        }
        if (FeedRecordDetailVO.READONLY_PENDING_REQUEST.equals(reason)) {
            return "这条记录有修改申请正在审核，审核完成前不能修改。";
        }
        return "这条投喂已确认，不能再修改。需要更正时请联系所有者或场长。";
    }

    @NonNull
    static List<String[]> readonlyRowsOf(@NonNull FeedRecordDetailVO r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"饲料", r.getFeed() == null ? "" : FeedFormat.orEmpty(r.getFeed().getFeedName())});
        rows.add(new String[]{"计划量", FeedFormat.kg(r.getPlanQty())});
        rows.add(new String[]{"实际投喂量", FeedFormat.kg(r.getActualQty())});
        rows.add(new String[]{"偏差", FeedFormat.signedPercent(r.getDeviationRatio())});
        rows.add(new String[]{"执行人", r.getExecutor() == null ? "" : FeedFormat.orEmpty(r.getExecutor().getName())});
        String recorder = r.getRecorder() == null ? "" : FeedFormat.orEmpty(r.getRecorder().getName());
        if (r.isProxy()) {
            recorder = recorder + "（代登记）";
        }
        rows.add(new String[]{"登记人", recorder});
        String at = DateFormatter.formatDateTime(r.getRegisteredAt());
        rows.add(new String[]{"登记时间", r.isBackfill() ? at + FeedFormat.DOT + "补登" : at});
        rows.add(new String[]{"备注", r.getRemark() == null || r.getRemark().isEmpty() ? "无" : r.getRemark()});
        return rows;
    }

    // ===================== 即时计算 =====================

    public void onFeedSelected(@Nullable FeedOptionBriefVO feed) {
        if (feed != null && !feed.isSelectable()) {
            return;
        }
        selectedFeed.setValue(feed);
        clearFieldError(F_FEED);
    }

    public void onFeedSelectedById(long feedId) {
        List<FeedOptionBriefVO> list = feedOptions.getValue();
        if (list != null) {
            for (FeedOptionBriefVO o : list) {
                if (o.getFeedId() == feedId) {
                    onFeedSelected(o);
                    return;
                }
            }
        }
    }

    public void onExecutorSelectedById(long userId) {
        List<UserBriefVO> list = executorOptions.getValue();
        if (list != null) {
            selectedExecutor.setValue(matchUser(list, userId));
            clearFieldError(F_EXECUTOR);
        }
    }

    /** 由输入变化触发：偏差与异常横幅、结存提示与超结存拦截、出库说明、登记人说明、主按钮可用 */
    @VisibleForTesting
    void recompute() {
        FeedRegisterMode m = mode.getValue();
        FeedTaskFormVO f = formCtx.getValue();
        FeedRecordDetailVO r = record.getValue();
        BigDecimal plan = planQty.getValue();
        BigDecimal actual = FeedCalc.parse(actualQty.getValue());
        if (actual != null && actual.signum() <= 0) {
            actual = null;
        }
        boolean editing = m == FeedRegisterMode.EDIT && r != null;
        BigDecimal threshold = editing ? r.getAbnormalThreshold() : f == null ? null : f.getAbnormalThreshold();

        // 计划量来源与偏差
        BigDecimal dev = FeedCalc.deviation(plan, actual);
        deviationRatio.setValue(dev);
        Integer head;
        BigDecimal daily;
        Integer meals;
        if (r != null && r.getPlanFormula() != null) {
            head = r.getPlanFormula().getHeadCount();
            daily = r.getPlanFormula().getDailyQtyPerHead();
            meals = r.getPlanFormula().getMealsPerDay();
        } else if (f != null) {
            head = f.getHeadCount();
            daily = f.getDailyQtyPerHead();
            meals = f.getMealsPerDay();
        } else {
            head = null;
            daily = null;
            meals = null;
        }
        planSourceText.setValue(FeedCalc.planSource(head, daily, meals, dev));
        abnormalText.setValue(FeedCalc.abnormalText(dev, threshold));

        // 结存
        FeedOptionBriefVO feed = selectedFeed.getValue();
        stockHint.setValue(stockHintOf(feed));
        BigDecimal stock = feed == null ? null : feed.getStockQty();
        BigDecimal original = editing && r.getFeed() != null && feed != null
                && feed.getFeedId() == r.getFeed().getFeedId() ? r.getActualQty() : null;
        boolean exceeds = FeedCalc.exceedsStock(actual, stock, original);
        boolean staff = isStaffView();
        if (exceeds) {
            stockError.setValue("不能超过结存 " + FeedFormat.kg(stock));
            BigDecimal need = FeedCalc.outboundNeed(actual, original);
            outboundError.setValue("本次需要 " + FeedFormat.kg(need) + "，结存 " + FeedFormat.kg(stock) + "，"
                    + (staff ? "请联系所有者或场长先登记入库。" : "请先登记入库。"));
        } else {
            stockError.setValue(null);
            outboundError.setValue(null);
        }
        BigDecimal shown = actual != null ? actual : editing ? r.getActualQty() : plan;
        if (editing) {
            outboundText.setValue("保存后饲喂领用出库同步更正为 " + FeedFormat.kg(shown) + "。");
        } else if (staff) {
            outboundText.setValue("确认后自动生成 " + FeedFormat.kg(shown) + "饲喂领用出库，待办自动完成。"
                    + "确认后不能再修改，需要更正时请联系所有者或场长。");
        } else {
            outboundText.setValue("确认后自动生成 " + FeedFormat.kg(shown) + "饲喂领用出库，待办自动完成。"
                    + "员工确认后不能再修改；你可以修改或作废当天的记录，出库同步更正。");
        }

        // 登记人说明
        // 修改模式说明原登记人；新增模式为当前登录人
        UserBriefVO current = editing && r.getRecorder() != null ? r.getRecorder()
                : f != null ? f.getCurrentUser() : r == null ? null : r.getRecorder();
        String me = current == null ? "" : FeedFormat.orEmpty(current.getName());
        if (staff) {
            proxyHint.setValue("实际执行人：" + me + "（本人）");
        } else {
            UserBriefVO exec = selectedExecutor.getValue();
            boolean proxy = exec != null && current != null && exec.getUserId() != current.getUserId();
            proxyHint.setValue("登记人：" + me + (proxy ? "（代登记）" : ""));
        }

        boolean form = m == FeedRegisterMode.CREATE || m == FeedRegisterMode.EDIT;
        canSubmit.setValue(form && blockText.getValue() == null && !Boolean.TRUE.equals(submitting.getValue())
                && !exceeds && (f != null || r != null));
    }

    @NonNull
    static String stockHintOf(@Nullable FeedOptionBriefVO feed) {
        if (feed == null) {
            return "";
        }
        String days = feed.getRemainingDays() == null ? "暂无消耗" : "剩余可用 " + feed.getRemainingDays() + " 天";
        return "结存 " + FeedFormat.kg(feed.getStockQty()) + FeedFormat.DOT + days;
    }

    /** 执行人下拉显示“李明 · 员工” */
    @NonNull
    public static String userLabel(@Nullable UserBriefVO u) {
        if (u == null) {
            return "";
        }
        return FeedFormat.join(u.getName(), u.getRoleText());
    }

    // ===================== 校验与提交 =====================

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        FeedOptionBriefVO feed = selectedFeed.getValue();
        if (feed == null || feed.getFeedId() <= 0) {
            e.put(F_FEED, "请选择饲料");
        } else if (!feed.isSelectable() && !isOriginalFeed(feed)) {
            e.put(F_FEED, feed.getDisabledReason() == null ? "这种饲料结存为 0，不能出库" : feed.getDisabledReason());
        }
        String qtyError = FeedCalc.checkActual(actualQty.getValue());
        if (qtyError != null) {
            e.put(F_ACTUAL, qtyError);
        } else if (stockError.getValue() != null) {
            e.put(F_ACTUAL, stockError.getValue());
        }
        if (!isStaffView() && Boolean.TRUE.equals(executorVisible.getValue()) && selectedExecutor.getValue() == null) {
            e.put(F_EXECUTOR, "请选择实际执行人");
        }
        String note = remark.getValue() == null ? "" : remark.getValue().trim();
        if (note.codePointCount(0, note.length()) > MAX_REMARK) {
            e.put(F_REMARK, "备注最多 100 个字");
        }
        return e;
    }

    private boolean isOriginalFeed(@NonNull FeedOptionBriefVO feed) {
        FeedRecordDetailVO r = record.getValue();
        return r != null && r.getFeed() != null && r.getFeed().getFeedId() == feed.getFeedId();
    }

    /** 新增“确认投喂”（API-G04）或修改“保存修改”（API-G06） */
    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        recompute();
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        if (mode.getValue() == FeedRegisterMode.EDIT) {
            saveEdit();
            return;
        }
        FeedRecordCreateRequest req = buildCreateRequest();
        if (req == null) {
            return;
        }
        resentAfterLookup = false;
        submitting.setValue(true);
        sendCreate(req, idem.keyFor(req));
    }

    @Nullable
    @VisibleForTesting
    FeedRecordCreateRequest buildCreateRequest() {
        FeedTaskFormVO f = formCtx.getValue();
        FeedOptionBriefVO feed = selectedFeed.getValue();
        BigDecimal actual = FeedCalc.parse(actualQty.getValue());
        if (f == null || feed == null || actual == null) {
            return null;
        }
        FeedRecordCreateRequest req = new FeedRecordCreateRequest();
        req.taskId = f.getTaskId();
        req.feedId = feed.getFeedId();
        req.actualQty = actual.stripTrailingZeros().scale() < 0 ? actual.setScale(0) : actual.stripTrailingZeros();
        UserBriefVO exec = selectedExecutor.getValue();
        req.executorId = isStaffView() || exec == null ? null : exec.getUserId();
        String note = remark.getValue() == null ? "" : remark.getValue().trim();
        req.remark = note.isEmpty() ? null : note;
        return req;
    }

    private void sendCreate(@NonNull FeedRecordCreateRequest req, @NonNull String key) {
        launch(repository.createRecord(req, key), resp -> onCreated(resp.getData(), resp.getMessage()),
                e -> onCreateError(e, req));
    }

    private void onCreated(@Nullable FeedRecordDetailVO r, @Nullable String message) {
        submitting.setValue(false);
        finished = true;
        idem.clear();
        discardDraft();
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            FeedTaskFormVO f = formCtx.getValue();
            todo.notifyTodoChanged(new TodoChange(null, FeedExtras.BIZ_FEED_TASK,
                    f == null ? (r == null ? null : r.getTaskId()) : f.getTaskId(), farmContext.farmIdOrZero()));
        }
        String text = message;
        if (text == null || text.isEmpty() || "ok".equalsIgnoreCase(text)) {
            String proxyName = r == null ? null : r.getProxyExecutorName();
            text = proxyName != null ? "已代" + proxyName + "登记投喂，待办已自动完成" : MSG_DONE;
        }
        closeWithResult.setValue(UiMessage.of(text));
    }

    private void onCreateError(@NonNull ApiException e, @NonNull FeedRecordCreateRequest req) {
        int code = e.getCode();
        if (FeedErrors.isTimeout(e)) {
            lookupAfterTimeout(req);
            return;
        }
        if (code == ErrorCodes.DUPLICATE_SUBMIT) {
            // 同一幂等键处理中：静默，2 秒后查询
            String key = idem.current();
            if (key != null) {
                launch(Single.timer(2, TimeUnit.SECONDS).flatMap(t -> repository.lookupRecord(key)),
                        vo -> onLookup(vo.isSaved(), vo.getRecord(), req), err -> unconfirmed());
            }
            return;
        }
        submitting.setValue(false);
        handleSubmitError(e);
    }

    /** 读超时：先查 API-G08，saved 为真按成功处理，否则同键重发一次 */
    public void lookupAfterTimeout(@NonNull FeedRecordCreateRequest req) {
        String key = idem.current();
        if (key == null) {
            submitting.setValue(false);
            return;
        }
        submitting.setValue(true);
        launch(repository.lookupRecord(key), vo -> onLookup(vo.isSaved(), vo.getRecord(), req), err -> unconfirmed());
    }

    private void onLookup(boolean saved, @Nullable FeedRecordDetailVO r, @NonNull FeedRecordCreateRequest req) {
        if (saved) {
            onCreated(r, null);
            return;
        }
        String key = idem.current();
        if (resentAfterLookup || key == null) {
            unconfirmed();
            return;
        }
        resentAfterLookup = true;
        sendCreate(req, key);
    }

    private void unconfirmed() {
        submitting.setValue(false);
        showMessage(FeedErrors.MSG_UNCONFIRMED);
    }

    /** 新增与修改共用的错误处理 */
    private void handleSubmitError(@NonNull ApiException e) {
        int code = e.getCode();
        if (e.isNetwork()) {
            showMessage(e.keptMessage(FeedErrors.MSG_NETWORK_KEEP));
            return;
        }
        switch (code) {
            case ErrorCodes.STOCK_NOT_ENOUGH:
                idem.clear();
                if (e.dataString("need_qty") != null) {
                    // 预检超结存：实投框错误 + 错误横幅
                    putFieldError(F_ACTUAL, "不能超过结存 " + FeedFormat.kg(parseOrNull(e.dataString("stock_qty"))));
                    outboundError.setValue(e.getMessage());
                } else {
                    // 原子扣减失败：饲料框错误 +“刷新”
                    putFieldError(F_FEED, e.getMessage());
                    showMessage(UiMessage.withAction(e.getMessage(), "刷新", FeedErrors.ACTION_REFRESH));
                }
                return;
            case ErrorCodes.STATE_CHANGED:
                idem.clear();
                if (e.getMessage().contains("已由") || "REGISTERED".equals(e.dataString("reason"))) {
                    finished = true;
                    discardDraft();
                    closeWithResult.setValue(UiMessage.of(e.getMessage()));
                } else if (mode.getValue() == FeedRegisterMode.EDIT) {
                    showMessage(e.getMessage());
                    retry();
                } else {
                    blockText.setValue(e.getMessage());
                    discardDraft();
                }
                return;
            case ErrorCodes.SERVER_ERROR:
                idem.clear();
                showMessage(UiMessage.withAction(e.getMessage(), "重试", FeedErrors.ACTION_RETRY));
                return;
            case ErrorCodes.VERSION_CONFLICT:
                showMessage(UiMessage.withAction(FeedErrors.MSG_VERSION, "刷新", FeedErrors.ACTION_REFRESH));
                return;
            case ErrorCodes.PEN_FORBIDDEN:
                idem.clear();
                onNoPermission(e);
                return;
            case ErrorCodes.ROLE_FORBIDDEN:
            case ErrorCodes.CHANGE_REQUEST_REQUIRED:
            case ErrorCodes.PENDING_EXISTS:
                // 提交时无权修改、非当天、已有修改申请：提示并切换为只读
                idem.clear();
                showMessage(e.getMessage());
                retry();
                return;
            default:
                if (code == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
                    idem.clear();
                    fieldErrors.setValue(new LinkedHashMap<>(e.getFieldErrors()));
                    return;
                }
                handleError(e);
        }
    }

    @Nullable
    private static BigDecimal parseOrNull(@Nullable String s) {
        return FeedCalc.parse(s);
    }

    /** 40304：整页换成“你没有这个栏舍的登记权限”，说明取 data.detail */
    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        String detail = e.dataString("detail");
        setUiState(UiState.noPermission(detail != null ? detail : e.getMessage(), e.getCode()));
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    /** API-G06 保存修改（同一饲料按差值校验结存，已在 recompute 中拦截） */
    public void saveEdit() {
        FeedRecordDetailVO r = record.getValue();
        FeedOptionBriefVO feed = selectedFeed.getValue();
        BigDecimal actual = FeedCalc.parse(actualQty.getValue());
        if (r == null || feed == null || actual == null) {
            return;
        }
        FeedRecordUpdateRequest req = new FeedRecordUpdateRequest();
        req.feedId = feed.getFeedId();
        req.actualQty = actual.stripTrailingZeros().scale() < 0 ? actual.setScale(0) : actual.stripTrailingZeros();
        UserBriefVO exec = selectedExecutor.getValue();
        req.executorId = exec == null ? null : exec.getUserId();
        String note = remark.getValue() == null ? "" : remark.getValue().trim();
        req.remark = note.isEmpty() ? null : note;
        req.version = r.getVersion();
        submitting.setValue(true);
        launch(repository.updateRecord(r.getRecordId(), req), resp -> onSaved(resp, MSG_SAVED), e -> {
            submitting.setValue(false);
            handleSubmitError(e);
        });
    }

    /** more_vert →“作废这条记录”：确认对话框正文带出实投量 */
    public void onVoidMenuClick() {
        FeedRecordDetailVO r = record.getValue();
        if (r == null || !Boolean.TRUE.equals(canVoid.getValue())) {
            return;
        }
        showVoidDialog.setValue("关联的 " + FeedFormat.kg(r.getActualQty()) + "饲喂领用出库会同步作废，作废后不能恢复。");
    }

    /** API-G07 作废 */
    public void confirmVoid() {
        FeedRecordDetailVO r = record.getValue();
        if (r == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        submitting.setValue(true);
        launch(repository.voidRecord(r.getRecordId(), new FeedRecordVoidRequest(r.getVersion())),
                resp -> onSaved(resp, MSG_VOIDED), e -> {
                    submitting.setValue(false);
                    handleSubmitError(e);
                });
    }

    private void onSaved(@NonNull ApiResponse<FeedRecordDetailVO> resp, @NonNull String fallback) {
        submitting.setValue(false);
        finished = true;
        ITodoService todo = ServiceHub.get(ITodoService.class);
        FeedRecordDetailVO r = record.getValue();
        if (todo != null && r != null) {
            todo.notifyTodoChanged(new TodoChange(null, FeedExtras.BIZ_FEED_TASK, r.getTaskId(),
                    farmContext.farmIdOrZero()));
        }
        String text = resp.getMessage();
        closeWithResult.setValue(UiMessage.of(text == null || text.isEmpty() || "ok".equalsIgnoreCase(text)
                ? fallback : text));
    }

    /** “提交修改申请”→ U01（biz_type FEED_RECORD） */
    public void requestChange() {
        FeedRecordDetailVO r = record.getValue();
        if (r != null) {
            openChangeRequest.setValue(FeedNav.changeArgs(FeedExtras.BIZ_FEED_RECORD, r.getRecordId()));
        }
    }

    /** 横幅“去入库” */
    public void onStockInClick() {
        FeedOptionBriefVO feed = selectedFeed.getValue();
        openStockIn.setValue(feed == null ? 0L : feed.getFeedId());
    }

    public void clearFieldError(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(m);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
    }

    private void putFieldError(@NonNull String field, @NonNull String message) {
        Map<String, String> copy = new LinkedHashMap<>(fieldErrors.getValue() == null ? Collections.emptyMap()
                : fieldErrors.getValue());
        copy.put(field, message);
        fieldErrors.setValue(copy);
    }

    /** 当前待确认的幂等键（测试与超时查询用） */
    @Nullable
    public String pendingIdemKey() {
        return idem.current();
    }

    // ===================== 返回与草稿 =====================

    @NonNull
    private String snapshot() {
        FeedOptionBriefVO feed = selectedFeed.getValue();
        UserBriefVO exec = selectedExecutor.getValue();
        return (feed == null ? "" : feed.getFeedId()) + "|" + actualQty.getValue() + "|"
                + (exec == null ? "" : exec.getUserId()) + "|" + remark.getValue();
    }

    public boolean isDirty() {
        FeedRegisterMode m = mode.getValue();
        return (m == FeedRegisterMode.CREATE || m == FeedRegisterMode.EDIT) && blockText.getValue() == null
                && initialSnapshot != null && !initialSnapshot.equals(snapshot());
    }

    /** 有改动返回：“放弃这次填写？”；返回 true 表示已拦截 */
    public boolean onBack() {
        if (isDirty() && !finished) {
            showDiscardDialog.call();
            return true;
        }
        return false;
    }

    /** 选“放弃”：删除草稿后关闭 */
    public void onDiscardConfirmed() {
        finished = true;
        idem.clear();
        discardDraft();
    }

    @Nullable
    private String draftKey() {
        FeedTaskFormVO f = formCtx.getValue();
        return f == null ? null : "task:" + f.getTaskId();
    }

    /** 新增模式 onStop 且有改动时保存草稿（含待确认的幂等键） */
    public void saveDraft() {
        String key = draftKey();
        if (finished || key == null || mode.getValue() != FeedRegisterMode.CREATE || blockText.getValue() != null
                || (!isDirty() && idem.current() == null)) {
            return;
        }
        Draft d = new Draft();
        FeedOptionBriefVO feed = selectedFeed.getValue();
        UserBriefVO exec = selectedExecutor.getValue();
        d.feedId = feed == null ? null : feed.getFeedId();
        d.actualQty = actualQty.getValue();
        d.executorId = exec == null ? null : exec.getUserId();
        d.remark = remark.getValue();
        d.idemKey = idem.current();
        d.idemSnapshot = idem.snapshot();
        disposables.add(drafts.save(PAGE_CODE, farmContext.farmIdOrZero(), key, d).subscribe(() -> {
        }, t -> {
        }));
    }

    /** 再次进入：带待确认键时先查 API-G08（已保存则删除草稿并按成功处理），否则询问是否恢复 */
    private void checkDraft() {
        String key = draftKey();
        if (key == null) {
            return;
        }
        launch(drafts.find(PAGE_CODE, farmContext.farmIdOrZero(), key, Draft.class).toSingle(), d -> {
            if (d.idemKey != null) {
                launch(repository.lookupRecord(d.idemKey), vo -> {
                    if (vo.isSaved()) {
                        onCreated(vo.getRecord(), null);
                    } else {
                        askRestore(d);
                    }
                }, err -> askRestore(d));
            } else {
                askRestore(d);
            }
        }, e -> {
            // 没有草稿（Maybe 为空）
        });
    }

    private void askRestore(@NonNull Draft d) {
        pendingDraft = d;
        showMessage(UiMessage.withAction(MSG_DRAFT, "恢复", FeedErrors.ACTION_RESTORE_DRAFT));
    }

    /** Snackbar“恢复” */
    public void restoreDraft() {
        Draft d = pendingDraft;
        pendingDraft = null;
        if (d == null) {
            return;
        }
        if (d.feedId != null) {
            onFeedSelectedById(d.feedId);
        }
        actualQty.setValue(d.actualQty == null ? "" : d.actualQty);
        if (d.executorId != null) {
            onExecutorSelectedById(d.executorId);
        }
        remark.setValue(d.remark == null ? "" : d.remark);
        idem.restore(d.idemKey, d.idemSnapshot);
        recompute();
    }

    /** 放弃或提交成功后删除草稿 */
    public void discardDraft() {
        pendingDraft = null;
        String key = draftKey();
        if (key == null) {
            return;
        }
        disposables.add(drafts.delete(PAGE_CODE, farmContext.farmIdOrZero(), key).subscribe(() -> {
        }, t -> {
        }));
    }

    /** 实投框改动：清掉字段错误（旧的幂等键由 keyFor 按请求内容判断失效） */
    public void onActualChanged() {
        clearFieldError(F_ACTUAL);
    }
}
