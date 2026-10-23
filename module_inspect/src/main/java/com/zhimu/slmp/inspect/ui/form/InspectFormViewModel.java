package com.zhimu.slmp.inspect.ui.form;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;

import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.model.LatLng;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.OptionVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.data.InspectionRepository;
import com.zhimu.slmp.inspect.model.request.InspectionSubmitRequest;
import com.zhimu.slmp.inspect.model.vo.InspectionDetailVO;
import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSubmitResultVO;
import com.zhimu.slmp.inspect.ui.common.Args;
import com.zhimu.slmp.inspect.ui.common.ClientRequestKeeper;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.common.NavTarget;
import com.zhimu.slmp.inspect.ui.common.SiteJudge;
import com.zhimu.slmp.map.location.OnceLocator;
import com.zhimu.slmp.map.model.vo.MapPoint;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.upload.MediaQueueController;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.network.GsonFactory;

import java.net.SocketTimeoutException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * I02 / I03 登记、修改、补录巡检的基类（模块07 文档 2.4.2）：表单上下文 API-I06、逐项单选、检查结果异常联动
 * “标记为异常”开关、异常原因、说明、实际执行人、单次定位与现场判定、照片和视频（上传队列）、提交与弱网处理
 * （client_request_id 沿用、超时查 API-I25、断网保留内容）、放弃确认。子类实现登记（I07）或补录（I09）的差异。
 */
public abstract class InspectFormViewModel extends BaseViewModel {

    public static final int MAX_MEDIA = 6;
    public static final int MAX_VIDEO = 1;
    public static final String MSG_NETWORK_KEEP = "网络连接失败，已保留填写的内容";
    public static final String MSG_SUBMIT_UNFINISHED = "提交没有完成，请重试";
    public static final String MSG_REPORT_SAVE_FAILED = "巡检没有保存，请重试";
    public static final String MSG_CONFLICT = "内容已更新，请刷新后再修改";
    public static final String ACTION_RETRY = "inspect_form_retry";
    public static final String ACTION_REFRESH = "inspect_form_refresh";

    private static final String STATE_SELECTIONS = "inspect_form_selections";
    private static final String STATE_MANUAL = "inspect_form_manual";

    protected final InspectionRepository repository;
    protected final FarmContext farmContext;
    protected final Context context;
    private final MediaQueueController.Factory mediaFactory;
    @Nullable
    protected MediaQueueController media;
    @Nullable
    protected ClientRequestKeeper requestKeeper;

    protected final MutableLiveData<InspectionFormVO> formCtx = new MutableLiveData<>();
    protected final MutableLiveData<InspectionDetailVO> detail = new MutableLiveData<>();
    protected final MutableLiveData<String> titleText = new MutableLiveData<>("");
    protected final MutableLiveData<String> primaryText = new MutableLiveData<>("提交");
    private final MutableLiveData<String> registeredNotice = new MutableLiveData<>();
    private final MutableLiveData<String> deadlineNotice = new MutableLiveData<>();
    private final MutableLiveData<String> registrantText = new MutableLiveData<>("");
    private final MutableLiveData<String> registrantTail = new MutableLiveData<>("自动记录");
    protected final MutableLiveData<String> infoTitle = new MutableLiveData<>("");
    protected final MutableLiveData<String> infoSubtitle = new MutableLiveData<>("");
    private final MutableLiveData<String> timeText = new MutableLiveData<>("");
    private final MutableLiveData<List<InspectionFormVO.TemplateItem>> templateItems =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Map<Long, Long>> selections = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> checkAbnormal = new MutableLiveData<>(false);
    private final MutableLiveData<String> abnormalBanner = new MutableLiveData<>();
    private final MutableLiveData<Boolean> manualMarked = new MutableLiveData<>(false);
    private final MutableLiveData<String> manualReason = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> switchLocked = new MutableLiveData<>(false);
    private final MutableLiveData<String> lockedText = new MutableLiveData<>("各项都正常但你发现其他问题时打开");
    private final MutableLiveData<Boolean> manualCardVisible = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> reasonVisible = new MutableLiveData<>(false);
    private final MutableLiveData<String> reasonHelper = new MutableLiveData<>("必填");
    private final MutableLiveData<String> readOnlyManualText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> canChooseExecutor = new MutableLiveData<>(false);
    private final MutableLiveData<List<OptionVO>> members = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> executorId = new MutableLiveData<>();
    private final MutableLiveData<String> remark = new MutableLiveData<>("");
    private final MutableLiveData<LocationUi> location = new MutableLiveData<>(LocationUi.none(
            InspectionSubmitRequest.LocationRequest.DENIED));
    private final MutableLiveData<Boolean> locationNoticeVisible = new MutableLiveData<>(false);
    private final MutableLiveData<String> mediaCountText = new MutableLiveData<>("0 / 6");
    private final MutableLiveData<Boolean> hasVideo = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> reportVisible = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    protected final MutableLiveData<Boolean> readOnly = new MutableLiveData<>(false);

    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<UiMessage> finishEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> scrollTo = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> requestLocation = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDiscard = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> focusReason = new SingleLiveEvent<>();

    protected long penId;
    @Nullable
    protected Long inspectionId;
    @Nullable
    protected Long todoId;
    /** 当前表单模式 REGISTER / MODIFY / BACKFILL */
    @NonNull
    protected String mode = InspectionFormVO.MODE_REGISTER;
    /** 用户自己打开的手动标记（检查异常锁定时保留原值） */
    private boolean manualUser;
    private boolean reportedLock;
    @Nullable
    private String othersMarkedBy;
    @Nullable
    private String othersReason;
    @Nullable
    private String initialSnapshot;
    private boolean finished;
    /** 等待上传完成或定位结束后继续提交：null 不等待，否则为 thenReport */
    @Nullable
    private Boolean pendingSubmit;
    private boolean waitingUpload;
    @Nullable
    private Observer<List<UploadItem>> mediaObserver;

    protected InspectFormViewModel(@NonNull InspectionRepository repository, @NonNull FarmContext farmContext,
                                   @NonNull Context context, @NonNull MediaQueueController.Factory mediaFactory) {
        this.repository = repository;
        this.farmContext = farmContext;
        this.context = context.getApplicationContext();
        this.mediaFactory = mediaFactory;
    }

    // ===== 子类 =====

    /** 上传队列的页面编号 */
    @NonNull
    protected abstract String pageCode();

    /** 补录页（I03）：不能转上报，执行人与日期必填 */
    protected abstract boolean isBackfill();

    /** 首次加载 */
    protected abstract void init(@NonNull SavedStateHandle handle);

    /** 新登记（非修改）时的提交（I07 或 I09） */
    protected abstract void submitNew(boolean thenReport, @NonNull List<InspectionSubmitRequest.ItemResultRequest> items,
                                      @NonNull List<Long> fileIds);

    @NonNull
    protected abstract String defaultTitle();

    @NonNull
    protected abstract String defaultPrimaryText();

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        media = mediaFactory.create(handle, "inspect_media", MediaScene.INSPECTION, pageCode(),
                farmContext.farmIdOrZero(), MAX_MEDIA, MAX_VIDEO);
        mediaObserver = list -> {
            int n = list == null ? 0 : list.size();
            mediaCountText.setValue(String.format(Locale.ROOT, "%d / %d", n, MAX_MEDIA));
            boolean video = false;
            if (list != null) {
                for (UploadItem i : list) {
                    video |= i.isVideo();
                }
            }
            hasVideo.setValue(video);
            onMediaChanged();
        };
        media.getItems().observeForever(mediaObserver);
        requestKeeper = new ClientRequestKeeper(handle, "inspect_client_request_id");
        Long pen = Args.getLong(handle, RouterPath.EXTRA_PEN_ID);
        penId = pen == null ? 0 : pen;
        inspectionId = Args.getLong(handle, RouterPath.EXTRA_INSPECTION_ID);
        todoId = Args.getLong(handle, RouterPath.EXTRA_TODO_ID);
        titleText.setValue(defaultTitle());
        primaryText.setValue(defaultPrimaryText());
        init(handle);
    }

    // ===== 对外 =====

    public LiveData<InspectionFormVO> getFormCtx() { return formCtx; }

    public LiveData<InspectionDetailVO> getDetail() { return detail; }

    public LiveData<String> getTitleText() { return titleText; }

    public LiveData<String> getPrimaryText() { return primaryText; }

    /** “今天 09:12 已登记，本次保存会修改这条记录。” */
    public LiveData<String> getRegisteredNotice() { return registeredNotice; }

    /** “已过今天的截止时间 18:00，提交后标为“超时”。” */
    public LiveData<String> getDeadlineNotice() { return deadlineNotice; }

    public LiveData<String> getRegistrantText() { return registrantText; }

    /** 登记人行右端：“自动记录”或“代登记” */
    public LiveData<String> getRegistrantTail() { return registrantTail; }

    public LiveData<String> getInfoTitle() { return infoTitle; }

    public LiveData<String> getInfoSubtitle() { return infoSubtitle; }

    /** 位置行右端的登记时间 */
    public LiveData<String> getTimeText() { return timeText; }

    public LiveData<List<InspectionFormVO.TemplateItem>> getTemplateItems() { return templateItems; }

    public LiveData<Map<Long, Long>> getSelections() { return selections; }

    public LiveData<Boolean> getCheckAbnormal() { return checkAbnormal; }

    public LiveData<String> getAbnormalBanner() { return abnormalBanner; }

    /** “标记为异常”开关显示的状态（手动开启、检查结果异常或已转上报时为开） */
    public LiveData<Boolean> getManualMarked() { return manualMarked; }

    public MutableLiveData<String> getManualReason() { return manualReason; }

    public LiveData<Boolean> getSwitchLocked() { return switchLocked; }

    /** 开关卡副文字 */
    public LiveData<String> getLockedText() { return lockedText; }

    public LiveData<Boolean> getManualCardVisible() { return manualCardVisible; }

    public LiveData<Boolean> getReasonVisible() { return reasonVisible; }

    /** 异常原因辅助文字：“必填”或“选填” */
    public LiveData<String> getReasonHelper() { return reasonHelper; }

    /** 只读查看手动标记的记录：“手动标记异常 · 个别仔猪咳嗽，舍温偏低” */
    public LiveData<String> getReadOnlyManualText() { return readOnlyManualText; }

    public LiveData<Boolean> getCanChooseExecutor() { return canChooseExecutor; }

    public LiveData<List<OptionVO>> getMembers() { return members; }

    public MutableLiveData<Long> getExecutorId() { return executorId; }

    public MutableLiveData<String> getRemark() { return remark; }

    public LiveData<LocationUi> getLocation() { return location; }

    public LiveData<Boolean> getLocationNoticeVisible() { return locationNoticeVisible; }

    public LiveData<String> getMediaCountText() { return mediaCountText; }

    public LiveData<Boolean> getHasVideo() { return hasVideo; }

    public LiveData<Boolean> getReportVisible() { return reportVisible; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public LiveData<Boolean> getReadOnly() { return readOnly; }

    public SingleLiveEvent<NavTarget> getNavEvent() { return navEvent; }

    public SingleLiveEvent<UiMessage> getFinishEvent() { return finishEvent; }

    public SingleLiveEvent<String> getScrollTo() { return scrollTo; }

    /** 开始定位：true 先显示定位用途 Notice（首次登记），false 直接申请权限并定位 */
    public SingleLiveEvent<Boolean> getRequestLocation() { return requestLocation; }

    public SingleLiveEvent<Void> getShowDiscard() { return showDiscard; }

    public SingleLiveEvent<Void> getFocusReason() { return focusReason; }

    @Nullable
    public MediaQueueController getMedia() { return media; }

    public long getPenId() { return penId; }

    @Nullable
    public Long getInspectionId() { return inspectionId; }

    public boolean isModifyMode() { return InspectionFormVO.MODE_MODIFY.equals(mode); }

    // ===== 表单上下文 =====

    /** API-I06 的结果写入页面 */
    protected void applyContext(@NonNull InspectionFormVO ctx) {
        formCtx.setValue(ctx);
        mode = ctx.getMode() == null ? mode : ctx.getMode();
        InspectionDetailVO record = ctx.getRecord();
        detail.setValue(record);
        if (record != null) {
            inspectionId = record.getInspectionId();
        }
        templateItems.setValue(ctx.sortedItems());
        Map<Long, Long> sel = new LinkedHashMap<>();
        Map<Long, Long> saved = restoredSelections();
        if (saved != null) {
            sel.putAll(saved);
        } else if (record != null) {
            for (InspectionDetailVO.ItemResult r : record.getItemResults()) {
                sel.put(r.getItemId(), r.getOptionId());
            }
        }
        selections.setValue(sel);
        canChooseExecutor.setValue(ctx.isCanChooseExecutor());
        members.setValue(ctx.getMembers());
        Boolean savedManual = getSavedState().get(STATE_MANUAL);
        if (record != null) {
            manualUser = savedManual != null ? savedManual : record.isManualMarked();
            if (manualReason.getValue() == null || manualReason.getValue().isEmpty()) {
                manualReason.setValue(record.getManualReason() == null ? "" : record.getManualReason());
            }
            if (remark.getValue() == null || remark.getValue().isEmpty()) {
                remark.setValue(record.getRemark() == null ? "" : record.getRemark());
            }
            UserBriefVO executor = record.getExecutor();
            if (executorId.getValue() == null) {
                executorId.setValue(executor != null ? executor.getUserId() : ctx.getDefaultExecutorId());
            }
            if (media != null) {
                media.setInitialFiles(record.getMedia());
            }
            // 员工修改被他人手动标记的记录：不显示开关，显示横幅并保留标记
            if (!ctx.isCanManualMark() && record.isManualMarked()) {
                othersMarkedBy = record.getManualMarkedByName() == null ? "" : record.getManualMarkedByName();
                othersReason = record.getManualReason();
            }
        } else {
            manualUser = Boolean.TRUE.equals(savedManual);
            if (executorId.getValue() == null) {
                executorId.setValue(ctx.getDefaultExecutorId());
            }
        }
        reportedLock = InspectionFormVO.LOCKED_REPORTED.equals(ctx.getAbnormalLockedReason());
        InspectionFormVO.FormPen pen = ctx.getPen();
        infoTitle.setValue(pen == null ? "" : pen.getPenName());
        infoSubtitle.setValue(buildSubtitle(ctx));
        UserBriefVO reg = ctx.getRegistrant();
        registrantText.setValue(reg == null || reg.getName() == null ? "" : reg.getName());
        if (record != null && record.getRegisteredAt() != null) {
            timeText.setValue(InspectFormat.time(record.getRegisteredAt()));
        } else if (!isBackfill()) {
            timeText.setValue(LocalTime.now(DateFormatter.ZONE).format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        boolean modify = InspectionFormVO.MODE_MODIFY.equals(mode);
        titleText.setValue(modify ? "修改巡检" : defaultTitle());
        primaryText.setValue(modify ? "保存修改" : defaultPrimaryText());
        deadlineNotice.setValue(!modify && !isBackfill() && ctx.isDeadlinePassed()
                ? "已过今天的截止时间 " + ctx.getDeadlineTime() + "，提交后标为“超时”。" : null);
        if (modify && record != null) {
            location.setValue(LocationUi.recorded(record.getLocationStatus(), record.getDistanceM()));
        } else if (location.getValue() == null || !location.getValue().isLocating()) {
            requestLocation.setValue(true);
        }
        recompute();
        if (initialSnapshot == null) {
            initialSnapshot = snapshot();
        }
    }

    /** 信息卡副文字：“ZM2610-031 · 存栏 200 头 · 10月19日 周一” */
    @NonNull
    protected String buildSubtitle(@NonNull InspectionFormVO ctx) {
        return ctx.batchNoText() + InspectFormat.DOT + "存栏 " + ctx.getStockCount() + " 头" + InspectFormat.DOT
                + InspectFormat.dateWithWeek(ctx.getInspectDate());
    }

    /** 再次登记同一栏舍时的提示 */
    protected void setRegisteredNotice(@Nullable String text) {
        registeredNotice.setValue(text);
    }

    /** 只读：用巡检详情生成只含已选项的项目 */
    protected void applyReadOnly(@NonNull InspectionDetailVO d) {
        readOnly.setValue(true);
        detail.setValue(d);
        titleText.setValue("巡检详情");
        JsonArray items = new JsonArray();
        Map<Long, Long> sel = new LinkedHashMap<>();
        for (InspectionDetailVO.ItemResult r : d.getItemResults()) {
            JsonObject opt = new JsonObject();
            opt.addProperty("option_id", r.getOptionId());
            opt.addProperty("option_name", r.getOptionName());
            opt.addProperty("is_abnormal", r.isOptionAbnormal());
            opt.addProperty("sort_no", 1);
            JsonArray opts = new JsonArray();
            opts.add(opt);
            JsonObject item = new JsonObject();
            item.addProperty("item_id", r.getItemId());
            item.addProperty("item_name", r.getItemName());
            item.addProperty("sort_no", r.getSortNo());
            item.add("options", opts);
            items.add(item);
            sel.put(r.getItemId(), r.getOptionId());
        }
        List<InspectionFormVO.TemplateItem> list = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            list.add(GsonFactory.get().fromJson(items.get(i), InspectionFormVO.TemplateItem.class));
        }
        templateItems.setValue(list);
        selections.setValue(sel);
        infoTitle.setValue(d.getPenName());
        infoSubtitle.setValue(InspectFormat.join(d.getBatchNos(), "") + InspectFormat.DOT
                + InspectFormat.dateWithWeek(d.getInspectDate()));
        UserBriefVO reg = d.getRegistrant();
        registrantText.setValue(reg == null || reg.getName() == null ? "" : reg.getName());
        registrantTail.setValue(d.isProxy() ? "代登记" : "自动记录");
        timeText.setValue(InspectFormat.time(d.getRegisteredAt()));
        location.setValue(LocationUi.recorded(d.getLocationStatus(), d.getDistanceM()));
        remark.setValue(d.getRemark() == null ? "" : d.getRemark());
        readOnlyManualText.setValue(d.isManualMarked() && d.getManualReason() != null
                ? "手动标记异常 · " + d.getManualReason() : null);
        checkAbnormal.setValue(InspectFormRules.checkAbnormal(list, sel));
        manualCardVisible.setValue(false);
        reportVisible.setValue(false);
        if (media != null) {
            media.setInitialFiles(d.getMedia());
        }
    }

    @Nullable
    private Map<Long, Long> restoredSelections() {
        Object raw = getSavedState().get(STATE_SELECTIONS);
        if (!(raw instanceof long[])) {
            return null;
        }
        long[] arr = (long[]) raw;
        Map<Long, Long> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < arr.length; i += 2) {
            m.put(arr[i], arr[i + 1]);
        }
        return m;
    }

    private void saveSelections(@NonNull Map<Long, Long> sel) {
        long[] arr = new long[sel.size() * 2];
        int i = 0;
        for (Map.Entry<Long, Long> e : sel.entrySet()) {
            arr[i++] = e.getKey();
            arr[i++] = e.getValue();
        }
        getSavedState().set(STATE_SELECTIONS, arr);
    }

    // ===== 点选与异常联动 =====

    /** 同一项目内单选，替换原选择；不能取消选中 */
    public void selectOption(long itemId, long optionId) {
        if (Boolean.TRUE.equals(readOnly.getValue())) {
            return;
        }
        Map<Long, Long> copy = new LinkedHashMap<>(selections.getValue() == null ? Collections.emptyMap()
                : selections.getValue());
        if (Objects.equals(copy.get(itemId), optionId)) {
            return;
        }
        copy.put(itemId, optionId);
        selections.setValue(copy);
        saveSelections(copy);
        clearFieldError(InspectFormRules.itemKey(itemId));
        recompute();
    }

    /** 点开关或卡片头部：锁定时提示原因，否则切换手动标记；打开后光标进入原因框 */
    public void clickManualSwitch() {
        if (Boolean.TRUE.equals(readOnly.getValue())) {
            return;
        }
        if (reportedLock) {
            showMessage(InspectFormRules.MSG_LOCKED_REPORTED);
            return;
        }
        if (Boolean.TRUE.equals(checkAbnormal.getValue())) {
            showMessage(InspectFormRules.MSG_LOCKED_CHECK);
            return;
        }
        manualUser = !manualUser;
        getSavedState().set(STATE_MANUAL, manualUser);
        if (!manualUser) {
            clearFieldError(InspectFormRules.F_MANUAL_REASON);
        }
        recompute();
        if (manualUser) {
            focusReason.call();
        }
    }

    /** 重新计算异常状态、开关、横幅与“转上报” */
    protected void recompute() {
        InspectionFormVO ctx = formCtx.getValue();
        List<InspectionFormVO.TemplateItem> items = templateItems.getValue() == null ? Collections.emptyList()
                : templateItems.getValue();
        Map<Long, Long> sel = selections.getValue() == null ? Collections.emptyMap() : selections.getValue();
        boolean check = InspectFormRules.checkAbnormal(items, sel);
        checkAbnormal.setValue(check);
        boolean canManual = ctx != null && ctx.isCanManualMark() && !Boolean.TRUE.equals(readOnly.getValue());
        manualCardVisible.setValue(canManual);
        boolean locked = check || reportedLock;
        switchLocked.setValue(locked);
        manualMarked.setValue(locked || manualUser);
        lockedText.setValue(reportedLock ? InspectFormRules.MSG_LOCKED_REPORTED
                : check ? "已按检查结果自动标为异常"
                : manualUser ? "填写异常原因后，栏舍标为异常" : "各项都正常但你发现其他问题时打开");
        String reason = manualReason.getValue() == null ? "" : manualReason.getValue().trim();
        reasonVisible.setValue(canManual && (manualUser || (locked && !reason.isEmpty())));
        reasonHelper.setValue(manualUser && !check ? "必填" : "选填");
        String summary = InspectFormRules.abnormalSummary(items, sel);
        abnormalBanner.setValue(InspectFormRules.banner(summary, canManual && (manualUser || reportedLock),
                isBackfill(), othersMarkedBy, othersReason));
        boolean abnormal = check || (canManual && manualUser) || reportedLock || othersMarkedBy != null;
        reportVisible.setValue(abnormal && ctx != null && ctx.isCanReport() && !isBackfill()
                && !Boolean.TRUE.equals(readOnly.getValue()));
        UserBriefVO reg = ctx == null ? null : ctx.getRegistrant();
        Long exec = executorId.getValue();
        registrantTail.setValue(reg != null && exec != null && exec != reg.getUserId() ? "代登记" : "自动记录");
    }

    /** 执行人下拉选择 */
    public void onExecutorSelected(long userId) {
        executorId.setValue(userId);
        clearFieldError(InspectFormRules.F_EXECUTOR);
        recompute();
    }

    /** 原因输入后错误状态消失 */
    public void onReasonChanged() {
        clearFieldError(InspectFormRules.F_MANUAL_REASON);
        reasonHelper.setValue(manualUser && !Boolean.TRUE.equals(checkAbnormal.getValue()) ? "必填" : "选填");
    }

    // ===== 定位 =====

    /** 首次登记：先显示定位用途 Notice，用户点“允许定位”之前位置为“无位置” */
    public void showLocationNotice() {
        locationNoticeVisible.setValue(true);
        location.setValue(LocationUi.none(InspectionSubmitRequest.LocationRequest.DENIED));
    }

    public void setLocationNoticeVisible(boolean visible) {
        locationNoticeVisible.setValue(visible);
    }

    /** 定位权限结果 */
    public void onLocationPermission(boolean granted) {
        locationNoticeVisible.setValue(false);
        if (!granted) {
            location.setValue(LocationUi.none(InspectionSubmitRequest.LocationRequest.DENIED));
            resumePendingSubmit();
            return;
        }
        location.setValue(LocationUi.locating());
        launch(OnceLocator.locate(context), this::onLocation, e -> {
            location.setValue(LocationUi.none(InspectionSubmitRequest.LocationRequest.FAILED));
            resumePendingSubmit();
        });
    }

    /** 单次定位结果：与养殖场坐标比较（高德 AMapUtils.calculateLineDistance，≤ 1000 米现场） */
    public void onLocation(@NonNull MapPoint point) {
        double lat = point.getLatitude().doubleValue();
        double lng = point.getLongitude().doubleValue();
        InspectionFormVO ctx = formCtx.getValue();
        Double farmLat = ctx == null ? null : ctx.getFarmLatitude();
        Double farmLng = ctx == null ? null : ctx.getFarmLongitude();
        Integer distance = null;
        if (farmLat != null && farmLng != null) {
            try {
                distance = Math.round(AMapUtils.calculateLineDistance(new LatLng(lat, lng),
                        new LatLng(farmLat, farmLng)));
            } catch (Throwable t) {
                distance = SiteJudge.distance(lat, lng, farmLat, farmLng);
            }
        }
        location.setValue(new LocationUi(SiteJudge.judge(distance), distance, lat, lng,
                point.getAccuracy() == null ? null : point.getAccuracy().doubleValue(),
                InspectionSubmitRequest.LocationRequest.OK));
        resumePendingSubmit();
    }

    // ===== 照片和视频 =====

    private void onMediaChanged() {
        if (waitingUpload && media != null && !media.hasUploading()) {
            waitingUpload = false;
            Boolean thenReport = pendingSubmit;
            pendingSubmit = null;
            submitting.setValue(false);
            if (thenReport != null) {
                submit(thenReport);
            }
        }
    }

    // ===== 提交 =====

    @NonNull
    protected Map<String, String> validate() {
        InspectFormRules.Input in = new InspectFormRules.Input();
        in.items = templateItems.getValue() == null ? Collections.emptyList() : templateItems.getValue();
        in.selections = selections.getValue() == null ? Collections.emptyMap() : selections.getValue();
        InspectionFormVO ctx = formCtx.getValue();
        in.manualOn = manualUser && ctx != null && ctx.isCanManualMark();
        in.manualReason = manualReason.getValue();
        in.remark = remark.getValue();
        in.requireExecutor = isBackfill() && ctx != null && ctx.isCanChooseExecutor();
        in.executorId = executorId.getValue();
        return InspectFormRules.validate(in);
    }

    /** 点“提交”（thenReport = false）或“转上报”（true） */
    public void submit(boolean thenReport) {
        if (Boolean.TRUE.equals(submitting.getValue()) || Boolean.TRUE.equals(readOnly.getValue())
                || formCtx.getValue() == null) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            scrollTo.setValue(errors.keySet().iterator().next());
            return;
        }
        MediaQueueController grid = media;
        if (grid != null && grid.hasUploading()) {
            // 照片和视频还在上传：按钮保持加载，上传完成后自动继续
            submitting.setValue(true);
            waitingUpload = true;
            pendingSubmit = thenReport;
            return;
        }
        String block = grid == null ? null : grid.getSubmitBlockMessage();
        if (block != null) {
            showMessage(block);
            return;
        }
        LocationUi loc = location.getValue();
        if (!isModifyMode() && loc != null && loc.isLocating()) {
            // 最多等定位结束（10 秒超时）
            submitting.setValue(true);
            pendingSubmit = thenReport;
            return;
        }
        submitting.setValue(true);
        List<InspectionSubmitRequest.ItemResultRequest> items = itemResults();
        List<Long> fileIds = grid == null ? Collections.emptyList() : grid.getFileIds();
        if (isModifyMode() && inspectionId != null) {
            InspectionSubmitRequest req = new InspectionSubmitRequest();
            fillCommon(req, items, fileIds);
            InspectionDetailVO record = detail.getValue();
            req.version = record == null ? null : record.getVersion();
            long id = inspectionId;
            launch(repository.modify(id, req), r -> onSaved(r, thenReport), e -> onSubmitError(e, thenReport));
        } else {
            submitNew(thenReport, items, fileIds);
        }
    }

    private void resumePendingSubmit() {
        if (pendingSubmit != null && !waitingUpload) {
            boolean thenReport = pendingSubmit;
            pendingSubmit = null;
            submitting.setValue(false);
            submit(thenReport);
        }
    }

    @NonNull
    protected List<InspectionSubmitRequest.ItemResultRequest> itemResults() {
        List<InspectionSubmitRequest.ItemResultRequest> list = new ArrayList<>();
        Map<Long, Long> sel = selections.getValue() == null ? Collections.emptyMap() : selections.getValue();
        for (InspectionFormVO.TemplateItem item : templateItems.getValue() == null
                ? Collections.<InspectionFormVO.TemplateItem>emptyList() : templateItems.getValue()) {
            Long o = sel.get(item.getItemId());
            if (o != null) {
                list.add(new InspectionSubmitRequest.ItemResultRequest(item.getItemId(), o));
            }
        }
        return list;
    }

    /** 登记与修改共用的字段（手动标记只在 can_manual_mark 时发送，执行人只在 can_choose_executor 时发送） */
    protected void fillCommon(@NonNull InspectionSubmitRequest req,
                              @NonNull List<InspectionSubmitRequest.ItemResultRequest> items, @NonNull List<Long> fileIds) {
        InspectionFormVO ctx = formCtx.getValue();
        req.itemResults = items;
        if (ctx != null && ctx.isCanManualMark()) {
            req.isManualMarked = manualUser;
            String reason = manualReason.getValue() == null ? "" : manualReason.getValue().trim();
            req.manualReason = manualUser && !reason.isEmpty() ? reason : null;
        }
        String r = remark.getValue() == null ? "" : remark.getValue().trim();
        req.remark = r.isEmpty() ? null : r;
        if (ctx != null && ctx.isCanChooseExecutor()) {
            req.executorId = executorId.getValue();
        }
        req.mediaFileIds = fileIds;
    }

    @NonNull
    protected InspectionSubmitRequest.LocationRequest locationRequest() {
        LocationUi loc = location.getValue();
        return loc == null ? LocationUi.none(InspectionSubmitRequest.LocationRequest.FAILED).toRequest() : loc.toRequest();
    }

    @Nullable
    protected ClientRequestKeeper keeper() {
        return requestKeeper;
    }

    /** 保存成功 */
    protected void onSaved(@NonNull InspectionSubmitResultVO r, boolean thenReport) {
        submitting.setValue(false);
        finished = true;
        if (media != null) {
            media.clearAfterSubmit();
        }
        if (requestKeeper != null) {
            requestKeeper.reset();
        }
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(todoId, InspectExtras.BIZ_INSPECTION, r.getInspectionId(),
                    farmContext.farmIdOrZero()));
        }
        if (thenReport) {
            navEvent.setValue(NavTarget.of(RouterPath.I05)
                    .put(RouterPath.EXTRA_INSPECTION_ID, r.getInspectionId())
                    .put(RouterPath.EXTRA_PEN_ID, penId)
                    .finishCurrent());
            return;
        }
        finishEvent.setValue(UiMessage.of(successMessage(r)));
    }

    @NonNull
    protected String successMessage(@NonNull InspectionSubmitResultVO r) {
        if (isModifyMode()) {
            return "已保存修改";
        }
        String pen = r.getPenName() != null ? r.getPenName() : infoTitle.getValue();
        return r.isAbnormal() ? "已登记巡检，" + pen + "已标为异常" : "已登记 " + pen + "的巡检";
    }

    /** 提交失败：超时查 API-I25；断网保留内容并提供“重试”；40902 提供“刷新”；字段错误写入输入框 */
    protected void onSubmitError(@NonNull ApiException e, boolean thenReport) {
        int code = e.getCode();
        if (!isModifyMode() && isTimeout(e) && requestKeeper != null && requestKeeper.current() != null) {
            confirmAfterTimeout(thenReport);
            return;
        }
        submitting.setValue(false);
        if (e.isNetwork()) {
            showMessage(UiMessage.withAction(MSG_NETWORK_KEEP, "重试", ACTION_RETRY));
            return;
        }
        if (code == ErrorCodes.VERSION_CONFLICT) {
            showMessage(UiMessage.withAction(MSG_CONFLICT, "刷新", ACTION_REFRESH));
            return;
        }
        if (code == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
            Map<String, String> m = new LinkedHashMap<>(e.getFieldErrors());
            fieldErrors.setValue(m);
            scrollTo.setValue(m.keySet().iterator().next());
            return;
        }
        if (onBusinessError(e)) {
            return;
        }
        if (thenReport) {
            showMessage(e.getMessage() != null ? e.getMessage() : MSG_REPORT_SAVE_FAILED);
            return;
        }
        if (ErrorCodes.isNoPermission(code)) {
            showMessage(e.getMessage());
            return;
        }
        showMessage(e.getMessage());
    }

    /** 子类处理特有的业务错误（如补录的 40903、40307）；返回 true 表示已处理 */
    protected boolean onBusinessError(@NonNull ApiException e) {
        return false;
    }

    /** API-I25：超时后确认是否已保存；40401 表示未保存，才允许重试 */
    public void confirmAfterTimeout(boolean thenReport) {
        String id = requestKeeper == null ? null : requestKeeper.current();
        if (id == null) {
            submitting.setValue(false);
            return;
        }
        launch(repository.findByRequest(id), r -> onSaved(r, thenReport), err -> {
            submitting.setValue(false);
            showMessage(MSG_SUBMIT_UNFINISHED);
        });
    }

    static boolean isTimeout(@NonNull ApiException e) {
        Throwable c = e.getCause();
        while (c != null) {
            if (c instanceof SocketTimeoutException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (formCtx.getValue() != null || Boolean.TRUE.equals(readOnly.getValue())) {
            showMessage(e.getMessage());
        } else {
            super.onNoPermission(e);
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
        Map<String, String> copy = new LinkedHashMap<>(fieldErrors.getValue() == null ? Collections.emptyMap()
                : fieldErrors.getValue());
        copy.put(field, message);
        fieldErrors.setValue(copy);
        scrollTo.setValue(field);
    }

    protected void setSubmitting(boolean value) {
        submitting.setValue(value);
    }

    // ===== 返回 =====

    @NonNull
    protected String snapshot() {
        return selections.getValue() + "|" + manualUser + "|" + manualReason.getValue() + "|" + remark.getValue()
                + "|" + executorId.getValue() + "|" + (media == null ? 0 : media.current().size());
    }

    public boolean isDirty() {
        return !finished && initialSnapshot != null && !initialSnapshot.equals(snapshot());
    }

    /** 有改动时返回：“放弃这次登记？”；返回 true 表示已拦截 */
    public boolean onBackPressed() {
        if (isDirty() && !Boolean.TRUE.equals(readOnly.getValue())) {
            showDiscard.call();
            return true;
        }
        return false;
    }

    /** 选“放弃”：清理本页上传任务 */
    public void onDiscardConfirmed() {
        finished = true;
    }

    /** 字段在页面中的说明（供 TextUtils 判空） */
    protected static boolean isBlank(@Nullable String s) {
        return TextUtils.isEmpty(s == null ? null : s.trim());
    }

    @Override
    protected void onCleared() {
        if (media != null) {
            if (mediaObserver != null) {
                media.getItems().removeObserver(mediaObserver);
            }
            media.release();
        }
        super.onCleared();
    }
}
