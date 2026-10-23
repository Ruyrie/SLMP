package com.zhimu.slmp.feed.ui.plan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.FeedExtras;
import com.zhimu.slmp.feed.data.FeedRepository;
import com.zhimu.slmp.feed.model.request.FeedPlanSaveRequest;
import com.zhimu.slmp.feed.model.request.FeedPlanStageRequest;
import com.zhimu.slmp.feed.model.vo.FeedOptionBriefVO;
import com.zhimu.slmp.feed.model.vo.FeedPlanBatchOptionVO;
import com.zhimu.slmp.feed.model.vo.FeedPlanStageVO;
import com.zhimu.slmp.feed.model.vo.FeedPlanVO;
import com.zhimu.slmp.feed.model.vo.FeedStdApplyVO;
import com.zhimu.slmp.feed.ui.common.FeedErrors;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.util.FeedPlanValidator;
import com.zhimu.slmp.feed.util.MealNameResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * G03 饲喂方案（所有者、场长；员工调用返回 40303 显示无权查看）。入参 EXTRA_BATCH_ID，没有时先取 API-G09 选批次。
 * 套用品种标准（API-G11，只读不落库）前存快照可撤销，只替换可编辑的阶段；编辑饲料、每头日投喂量、餐次与投喂时间，
 * 汇总行按“每头日投喂量 × 当日存栏（÷ 餐次）”实时计算；投喂异常判定只写本页，随保存提交（API-G12，靠 version 防重）。
 */
public class FeedPlanViewModel extends BaseViewModel {

    public static final String ACTION_UNDO = "feed_plan_undo";
    static final String MSG_SAVED = "已保存饲喂方案";

    private final FeedRepository repository;

    private final MutableLiveData<FeedPlanVO> plan = new MutableLiveData<>();
    private final MutableLiveData<String> batchHeader = new MutableLiveData<>("");
    private final MutableLiveData<String> batchSummary = new MutableLiveData<>("");
    private final MutableLiveData<String> stdTitle = new MutableLiveData<>("");
    private final MutableLiveData<String> stdCardText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> showApplyStd = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> noPlanNotice = new MutableLiveData<>(false);
    private final MutableLiveData<List<FeedStageForm>> stages = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> expandedIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<Integer> adjustedCount = new MutableLiveData<>(0);
    private final MutableLiveData<List<FeedOptionBriefVO>> feedOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<BigDecimal> abnormalThreshold = new MutableLiveData<>();
    private final MutableLiveData<String> thresholdText = new MutableLiveData<>("");
    private final MutableLiveData<Map<String, String>> stageErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<List<FeedPlanBatchOptionVO>> batchOptions =
            new MutableLiveData<>(Collections.emptyList());

    private final SingleLiveEvent<Integer> scrollToError = new SingleLiveEvent<>();
    private final SingleLiveEvent<int[]> showTimePicker = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showThresholdSheet = new SingleLiveEvent<>();
    /** 打开修改记录面板（U14），值为 plan_id，方案还没有保存时为 0 */
    private final SingleLiveEvent<Long> showHistorySheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDiscardDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showBatchSheet = new SingleLiveEvent<>();

    @Nullable
    private Long batchId;
    @Nullable
    private Long appliedStdVersionId;
    @Nullable
    private String appliedStdName;
    @Nullable
    private List<FeedStageForm> undoStages;
    @Nullable
    private BigDecimal undoThreshold;
    @Nullable
    private String initialSnapshot;
    private boolean saved;

    @Inject
    public FeedPlanViewModel(@NonNull FeedRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = FeedExtras.readId(handle.get(RouterPath.EXTRA_BATCH_ID));
        if (id != null) {
            load(id);
        } else {
            loadBatchOptions();
        }
    }

    // ===================== 对外 =====================

    public LiveData<FeedPlanVO> getPlan() {
        return plan;
    }

    public LiveData<String> getBatchHeader() {
        return batchHeader;
    }

    public LiveData<String> getBatchSummary() {
        return batchSummary;
    }

    public LiveData<String> getStdTitle() {
        return stdTitle;
    }

    public LiveData<String> getStdCardText() {
        return stdCardText;
    }

    public LiveData<Boolean> getShowApplyStd() {
        return showApplyStd;
    }

    public LiveData<Boolean> getNoPlanNotice() {
        return noPlanNotice;
    }

    public LiveData<List<FeedStageForm>> getStages() {
        return stages;
    }

    public LiveData<Integer> getExpandedIndex() {
        return expandedIndex;
    }

    public LiveData<Integer> getAdjustedCount() {
        return adjustedCount;
    }

    public LiveData<List<FeedOptionBriefVO>> getFeedOptions() {
        return feedOptions;
    }

    public LiveData<BigDecimal> getAbnormalThreshold() {
        return abnormalThreshold;
    }

    public LiveData<String> getThresholdText() {
        return thresholdText;
    }

    public LiveData<Map<String, String>> getStageErrors() {
        return stageErrors;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public LiveData<List<FeedPlanBatchOptionVO>> getBatchOptions() {
        return batchOptions;
    }

    public SingleLiveEvent<Integer> getScrollToError() {
        return scrollToError;
    }

    /** {阶段序号, 餐次序号}；餐次序号等于当前餐数时表示新增的一餐 */
    public SingleLiveEvent<int[]> getShowTimePicker() {
        return showTimePicker;
    }

    public SingleLiveEvent<Void> getShowThresholdSheet() {
        return showThresholdSheet;
    }

    public SingleLiveEvent<Long> getShowHistorySheet() {
        return showHistorySheet;
    }

    public SingleLiveEvent<Void> getShowDiscardDialog() {
        return showDiscardDialog;
    }

    public SingleLiveEvent<Void> getShowBatchSheet() {
        return showBatchSheet;
    }

    /** 保存过方案（返回时 setResult 让 G01 刷新） */
    public boolean isSaved() {
        return saved;
    }

    @Nullable
    public Integer currentCount() {
        FeedPlanVO p = plan.getValue();
        return p == null || p.getBatch() == null ? null : p.getBatch().getCurrentCount();
    }

    @Nullable
    private Integer currentDayAge() {
        FeedPlanVO p = plan.getValue();
        return p == null || p.getBatch() == null ? null : p.getBatch().getDayAge();
    }

    // ===================== 加载 =====================

    /** 没有带批次：API-G09，一个批次直接加载，多个先选 */
    private void loadBatchOptions() {
        load(repository.getPlanBatchOptions(), list -> {
            batchOptions.setValue(list);
            if (list.size() == 1) {
                load(list.get(0).getBatchId());
            } else if (!list.isEmpty()) {
                showBatchSheet.call();
            }
        });
    }

    /** API-G10 */
    public void load(long id) {
        batchId = id;
        load(repository.getFeedPlan(id), this::applyPlan);
    }

    @Override
    public void retry() {
        if (batchId != null) {
            load(batchId);
        } else {
            loadBatchOptions();
        }
    }

    private void applyPlan(@NonNull FeedPlanVO p) {
        plan.setValue(p);
        appliedStdVersionId = null;
        appliedStdName = null;
        undoStages = null;
        FeedPlanVO.FeedPlanBatchVO b = p.getBatch();
        batchHeader.setValue(b == null ? "" : FeedFormat.orEmpty(b.getBatchNo()));
        batchSummary.setValue(b == null ? "" : FeedFormat.join(b.getPenNames(), b.getBreedName(),
                b.getDayAge() == null ? null : b.getDayAge() + " 日龄",
                b.getCurrentCount() == null ? null : "存栏 " + FeedFormat.head(b.getCurrentCount())));
        FeedPlanVO.FeedStdCardVO std = p.getStdCard();
        stdTitle.setValue(std == null || std.getStdName() == null ? (b == null ? "" : FeedFormat.orEmpty(
                b.getBreedName()) + "标准") : std.getStdName());
        showApplyStd.setValue(std != null && std.isStdAvailable());
        noPlanNotice.setValue(!p.isHasPlan());
        feedOptions.setValue(p.getFeedOptions());
        abnormalThreshold.setValue(p.getAbnormalThreshold() == null ? new BigDecimal("0.1000")
                : p.getAbnormalThreshold());
        List<FeedStageForm> list = new ArrayList<>();
        int expand = -1;
        for (FeedPlanStageVO s : p.getStages()) {
            FeedStageForm f = FeedStageForm.from(s);
            if (f.isCurrent() && expand < 0) {
                expand = list.size();
            }
            list.add(f);
        }
        if (expand < 0) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).editable) {
                    expand = i;
                    break;
                }
            }
        }
        stageErrors.setValue(Collections.emptyMap());
        stages.setValue(list);
        expandedIndex.setValue(expand);
        refreshDerived();
        initialSnapshot = snapshot();
    }

    // ===================== 套用标准 =====================

    /** API-G11：存快照，只替换可编辑的阶段 */
    public void applyStandard() {
        if (batchId == null) {
            return;
        }
        launch(repository.getFeedStd(batchId), std -> {
            if (!std.isStdAvailable() || std.getStages().isEmpty()) {
                showMessage("平台还没有这个品种的饲喂标准，请手动设置各阶段的饲料和投喂量");
                return;
            }
            undoStages = copyOf(stages.getValue());
            undoThreshold = abnormalThreshold.getValue();
            List<FeedStageForm> current = stages.getValue() == null ? Collections.emptyList() : stages.getValue();
            List<FeedStageForm> next = new ArrayList<>();
            for (FeedStdApplyVO.FeedStdStageVO s : std.getStages()) {
                FeedStageForm existing = findByStart(current, s.getDayAgeStart());
                if (existing != null && !existing.editable) {
                    next.add(existing.copy());
                } else {
                    FeedStageForm f = FeedStageForm.fromStd(s, existing, currentDayAge());
                    syncFeed(f);
                    next.add(f);
                }
            }
            appliedStdVersionId = std.getStdVersionId();
            appliedStdName = std.getStdName();
            if (std.getDefaultAbnormalThreshold() != null) {
                abnormalThreshold.setValue(std.getDefaultAbnormalThreshold());
            }
            stageErrors.setValue(Collections.emptyMap());
            stages.setValue(next);
            int expand = -1;
            for (int i = 0; i < next.size(); i++) {
                if (next.get(i).isCurrent()) {
                    expand = i;
                }
            }
            expandedIndex.setValue(expand);
            refreshDerived();
            String name = std.getStdName() == null ? "品种标准" : std.getStdName();
            showMessage(UiMessage.withAction("已套用" + name + "，保存后生效", "撤销", ACTION_UNDO));
        });
    }

    /** Snackbar“撤销”：恢复套用前的值 */
    public void undoApply() {
        List<FeedStageForm> snapshot = undoStages;
        if (snapshot == null) {
            return;
        }
        undoStages = null;
        appliedStdVersionId = null;
        appliedStdName = null;
        stages.setValue(copyOf(snapshot));
        abnormalThreshold.setValue(undoThreshold);
        refreshDerived();
    }

    /** 按本场饲料选项补齐名称与“库存不足”标记 */
    private void syncFeed(@NonNull FeedStageForm f) {
        List<FeedOptionBriefVO> options = feedOptions.getValue();
        if (f.feedId == null || options == null) {
            return;
        }
        for (FeedOptionBriefVO o : options) {
            if (o.getFeedId() == f.feedId) {
                f.feedName = o.getFeedName();
                f.lowStock = o.isLowStock();
            }
        }
    }

    @Nullable
    private static FeedStageForm findByStart(@NonNull List<FeedStageForm> list, @Nullable Integer start) {
        if (start == null) {
            return null;
        }
        for (FeedStageForm f : list) {
            if (f.dayAgeStart == start) {
                return f;
            }
        }
        return null;
    }

    @NonNull
    private static List<FeedStageForm> copyOf(@Nullable List<FeedStageForm> list) {
        List<FeedStageForm> out = new ArrayList<>();
        if (list != null) {
            for (FeedStageForm f : list) {
                out.add(f.copy());
            }
        }
        return out;
    }

    // ===================== 编辑 =====================

    /** 同一时间只展开一个阶段；已结束的阶段不能展开 */
    public void expandStage(int index) {
        List<FeedStageForm> list = stages.getValue();
        if (list == null || index < 0 || index >= list.size() || !list.get(index).editable) {
            return;
        }
        Integer cur = expandedIndex.getValue();
        if (cur == null || cur != index) {
            expandedIndex.setValue(index);
        }
    }

    public void setFeed(int index, long feedId) {
        FeedStageForm f = editableAt(index);
        if (f == null) {
            return;
        }
        f.feedId = feedId;
        for (FeedOptionBriefVO o : feedOptions.getValue() == null ? Collections.<FeedOptionBriefVO>emptyList()
                : feedOptions.getValue()) {
            if (o.getFeedId() == feedId) {
                f.feedName = o.getFeedName();
                f.lowStock = o.isLowStock();
            }
        }
        clearStageError(index, FeedPlanValidator.F_FEED);
        commit();
    }

    /** 每头日投喂量输入（文本，按输入实时重算汇总） */
    public void setDailyQty(int index, @Nullable String text) {
        FeedStageForm f = editableAt(index);
        String t = text == null ? "" : text;
        if (f == null || t.equals(f.dailyQtyText)) {
            return;
        }
        f.dailyQtyText = t;
        clearStageError(index, FeedPlanValidator.F_DAILY_QTY);
        commit();
    }

    /** 餐次 1–4：增加时在末尾追加默认时间，减少时去掉最后一个 */
    public void setMeals(int index, int meals) {
        FeedStageForm f = editableAt(index);
        if (f == null || meals < 1 || meals > 4 || meals == f.mealsPerDay) {
            return;
        }
        List<String> times = new ArrayList<>(f.mealTimes);
        while (times.size() < meals) {
            times.add(MealNameResolver.nextDefaultTime(times));
        }
        while (times.size() > meals) {
            times.remove(times.size() - 1);
        }
        Collections.sort(times);
        f.mealsPerDay = meals;
        f.mealTimes = times;
        clearStageError(index, FeedPlanValidator.F_MEALS);
        clearStageError(index, FeedPlanValidator.F_MEAL_TIMES);
        commit();
    }

    public void onTimeChipClick(int index, int mealIndex) {
        if (editableAt(index) != null) {
            showTimePicker.setValue(new int[]{index, mealIndex});
        }
    }

    /** 投喂时间：确定后按时间先后重新排序；与其他餐次重复时 Chip 行下方显示“投喂时间不能重复” */
    public void setMealTime(int index, int mealIndex, @NonNull String time) {
        FeedStageForm f = editableAt(index);
        if (f == null) {
            return;
        }
        List<String> times = new ArrayList<>(f.mealTimes);
        if (mealIndex >= 0 && mealIndex < times.size()) {
            times.set(mealIndex, time);
        } else {
            times.add(time);
        }
        Collections.sort(times);
        f.mealTimes = times;
        f.mealsPerDay = times.size();
        String err = FeedPlanValidator.checkMealTimes(times, f.mealsPerDay);
        if (FeedPlanValidator.ERR_TIME_DUP.equals(err)) {
            putStageError(index, FeedPlanValidator.F_MEAL_TIMES, err);
        } else {
            clearStageError(index, FeedPlanValidator.F_MEAL_TIMES);
        }
        commit();
    }

    /** 投喂异常判定面板：百分比 ÷ 100，只写本页，随保存提交 */
    public void setThreshold(@NonNull BigDecimal percent) {
        abnormalThreshold.setValue(percent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        refreshDerived();
    }

    /** 面板中的偏差比例输入校验（1–100） */
    @Nullable
    public static String checkPercent(@Nullable BigDecimal percent) {
        if (percent == null) {
            return FeedPlanValidator.ERR_THRESHOLD;
        }
        return FeedPlanValidator.checkThreshold(percent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    public void onThresholdEntryClick() {
        showThresholdSheet.call();
    }

    @Nullable
    private FeedStageForm editableAt(int index) {
        List<FeedStageForm> list = stages.getValue();
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        FeedStageForm f = list.get(index);
        return f.editable ? f : null;
    }

    private void commit() {
        List<FeedStageForm> list = stages.getValue();
        stages.setValue(list == null ? Collections.emptyList() : new ArrayList<>(list));
        refreshDerived();
    }

    private void refreshDerived() {
        int n = 0;
        List<FeedStageForm> list = stages.getValue();
        if (list != null) {
            for (FeedStageForm f : list) {
                n += f.adjustedCount();
            }
        }
        adjustedCount.setValue(n);
        stdCardText.setValue(stdCardTextOf(plan.getValue(), appliedStdVersionId != null, n));
        BigDecimal t = abnormalThreshold.getValue();
        thresholdText.setValue("实际偏离计划量超过 " + FeedFormat.thresholdPercent(t) + "% 时标为投喂异常");
    }

    /** 品种标准卡次文字：“10月1日套用 · 你调整了 1 项 · 标准已更新” */
    @NonNull
    static String stdCardTextOf(@Nullable FeedPlanVO p, boolean appliedNow, int adjusted) {
        FeedPlanVO.FeedStdCardVO std = p == null ? null : p.getStdCard();
        if (std != null && !std.isStdAvailable()) {
            return "平台还没有这个品种的饲喂标准，请手动设置各阶段的饲料和投喂量";
        }
        String adjustedText = adjusted > 0 ? "你调整了 " + adjusted + " 项" : null;
        if (appliedNow) {
            return FeedFormat.join("已套用，保存后生效", adjustedText);
        }
        if (std == null || std.getAppliedAt() == null) {
            return "还没有套用品种标准";
        }
        return FeedFormat.join(FeedFormat.date(std.getAppliedAt()) + "套用", adjustedText,
                std.isStdUpdated() ? "标准已更新" : null);
    }

    private void putStageError(int index, @NonNull String field, @NonNull String message) {
        Map<String, String> copy = new LinkedHashMap<>(stageErrors.getValue() == null ? Collections.emptyMap()
                : stageErrors.getValue());
        copy.put(FeedPlanValidator.key(index, field), message);
        stageErrors.setValue(copy);
    }

    private void clearStageError(int index, @NonNull String field) {
        Map<String, String> m = stageErrors.getValue();
        String key = FeedPlanValidator.key(index, field);
        if (m != null && m.containsKey(key)) {
            Map<String, String> copy = new LinkedHashMap<>(m);
            copy.remove(key);
            stageErrors.setValue(copy);
        }
    }

    // ===================== 保存 =====================

    @NonNull
    List<FeedPlanStageRequest> requests() {
        List<FeedPlanStageRequest> out = new ArrayList<>();
        List<FeedStageForm> list = stages.getValue();
        if (list != null) {
            for (FeedStageForm f : list) {
                out.add(f.toRequest());
            }
        }
        return out;
    }

    /** FeedPlanValidator 校验后 API-G12；校验失败或 40001 时展开出错阶段并滚动到第一个错误 */
    public void save() {
        if (batchId == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        List<FeedPlanStageRequest> reqs = requests();
        Map<String, String> errors = new LinkedHashMap<>(FeedPlanValidator.validate(reqs, currentDayAge()));
        String thresholdError = FeedPlanValidator.checkThreshold(abnormalThreshold.getValue());
        if (reqs.isEmpty()) {
            showMessage("请先套用品种标准或设置饲喂阶段");
            return;
        }
        if (!errors.isEmpty() || thresholdError != null) {
            stageErrors.setValue(errors);
            if (thresholdError != null) {
                showMessage(thresholdError);
            }
            focusFirstError(errors);
            return;
        }
        FeedPlanSaveRequest req = new FeedPlanSaveRequest();
        req.appliedStdVersionId = appliedStdVersionId;
        req.abnormalThreshold = abnormalThreshold.getValue();
        req.stages = reqs;
        FeedPlanVO p = plan.getValue();
        req.version = p == null ? null : p.getVersion();
        saving.setValue(true);
        launch(repository.saveFeedPlan(batchId, req), resp -> {
            saving.setValue(false);
            saved = true;
            if (resp.getData() != null) {
                applyPlan(resp.getData());
            } else {
                initialSnapshot = snapshot();
            }
            String msg = resp.getMessage();
            showMessage(msg == null || msg.isEmpty() || "ok".equalsIgnoreCase(msg) ? MSG_SAVED : msg);
        }, e -> {
            saving.setValue(false);
            onSaveError(e);
        });
    }

    private void onSaveError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.VERSION_CONFLICT) {
            showMessage(UiMessage.withAction(FeedErrors.MSG_VERSION, "刷新", FeedErrors.ACTION_REFRESH));
            return;
        }
        if (e.isNetwork()) {
            showMessage(FeedErrors.MSG_NETWORK_KEEP);
            return;
        }
        if (ErrorCodes.isNoPermission(e.getCode())) {
            showMessage(e.getMessage());
            return;
        }
        handleError(e);
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        stageErrors.setValue(new LinkedHashMap<>(errors));
        focusFirstError(errors);
        return true;
    }

    /** 40903 批次已结束等：提示后重新加载（只读） */
    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        retry();
    }

    private void focusFirstError(@NonNull Map<String, String> errors) {
        for (String key : errors.keySet()) {
            int i = FeedPlanValidator.stageIndexOf(key);
            if (i >= 0) {
                expandStage(i);
                scrollToError.setValue(i);
                return;
            }
        }
    }

    // ===================== 修改记录 =====================

    /** 顶部栏 history：打开公共修改记录面板（API-U14，biz_type FEED_PLAN，biz_id plan_id） */
    public void onHistoryClick() {
        FeedPlanVO p = plan.getValue();
        showHistorySheet.setValue(p == null || p.getPlanId() == null ? 0L : p.getPlanId());
    }

    // ===================== 返回 =====================

    @NonNull
    private String snapshot() {
        StringBuilder sb = new StringBuilder();
        List<FeedStageForm> list = stages.getValue();
        if (list != null) {
            for (FeedStageForm f : list) {
                sb.append(f.feedId).append(',').append(f.dailyQtyText).append(',').append(f.mealsPerDay).append(',')
                        .append(f.mealTimes).append(';');
            }
        }
        BigDecimal t = abnormalThreshold.getValue();
        sb.append(t == null ? "" : t.stripTrailingZeros().toPlainString());
        return sb.toString();
    }

    public boolean isDirty() {
        return initialSnapshot != null && !initialSnapshot.equals(snapshot());
    }

    /** 有改动返回：“放弃这次填写？” */
    public boolean onBack() {
        if (isDirty() && !Boolean.TRUE.equals(saving.getValue())) {
            showDiscardDialog.call();
            return true;
        }
        return false;
    }
}
