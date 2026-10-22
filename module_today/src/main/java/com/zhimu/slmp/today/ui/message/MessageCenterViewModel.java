package com.zhimu.slmp.today.ui.message;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.PagingData;
import androidx.paging.PagingDataTransforms;
import androidx.paging.rxjava3.PagingRx;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.IFarmInviteService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.today.data.MessageRepository;
import com.zhimu.slmp.today.model.request.MessageReadAllRequest;
import com.zhimu.slmp.today.model.vo.MessageActionVO;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.MessageOpenCoordinator;
import com.zhimu.slmp.today.util.MessageOverrides;
import com.zhimu.slmp.today.util.MessageRow;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.Texts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * T06 消息中心（MessageCenterActivity、MessageFilterSheet），技术文档 2.4.5、2.6.6、2.6.7。
 * Pager（API-T12）cachedIn 到 ViewModel 范围，再与本地覆盖表 MessageOverrides 做 combineLatest：先去掉已删除、再套用已读与邀请状态，
 * 最后按 date_group 插入“今天 / 昨天 / 更早”分组标题；本地操作无需重新请求，下拉刷新清空覆盖表。
 */
public class MessageCenterViewModel extends BaseViewModel {

    private static final String TAG = "MessageCenterVM";

    /** “全部类型” */
    public static final String CATEGORY_ALL = "";
    public static final String SHEET_CATEGORY = "category";
    public static final String SHEET_FARM = "farm";
    /** Snackbar 操作：撤销删除、切换过去 */
    public static final String ACTION_UNDO = "undo:";
    public static final String ACTION_SWITCH = "switch:";

    static final List<String> OWNER_CATEGORIES = Collections.unmodifiableList(Arrays.asList(CATEGORY_ALL,
            MessageBriefVO.CATEGORY_TODO, MessageBriefVO.CATEGORY_AI, MessageBriefVO.CATEGORY_AUDIT,
            MessageBriefVO.CATEGORY_MEMBER, MessageBriefVO.CATEGORY_LOT, MessageBriefVO.CATEGORY_NOTICE,
            MessageBriefVO.CATEGORY_OTHER));
    static final List<String> STAFF_CATEGORIES = Collections.unmodifiableList(Arrays.asList(CATEGORY_ALL,
            MessageBriefVO.CATEGORY_TODO, MessageBriefVO.CATEGORY_AI, MessageBriefVO.CATEGORY_MEMBER,
            MessageBriefVO.CATEGORY_NOTICE));

    static final String MSG_ALL_READ = "已全部标为已读";
    static final String MSG_NO_UNREAD = "没有未读消息";
    static final String MSG_DELETED = "已删除这条消息";
    static final String MSG_UNDO = "撤销";
    static final String MSG_REJECTED = "已拒绝邀请";
    static final String MSG_SWITCH = "切换过去";
    static final String MSG_NOT_FOUND = "记录不存在或无权查看";
    private static final String STATE_CATEGORY = "today_msg_category";
    private static final String STATE_UNREAD = "today_msg_unread";

    private final MessageRepository repository;
    private final MessageOpenCoordinator coordinator;
    private final TodayServiceImpl todoService;
    private final FarmContext farmContext;

    private final MutableLiveData<PagingData<MessageRow>> messagePaging = new MutableLiveData<>();
    private final MutableLiveData<String> category = new MutableLiveData<>(null);
    private final MutableLiveData<FarmBriefVO> farmFilter = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> unreadOnly = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> filteredUnread = new MutableLiveData<>(0);
    private final MutableLiveData<List<String>> categoryOptions = new MutableLiveData<>(OWNER_CATEGORIES);
    private final MutableLiveData<List<FarmBriefVO>> farmOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> showFarmFilter = new MutableLiveData<>(false);
    private final MutableLiveData<Long> actingActionId = new MutableLiveData<>(null);

    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> openFilterSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<MessageBriefVO> openRejectDialog = new SingleLiveEvent<>();

    private final BehaviorProcessor<Filter> filter = BehaviorProcessor.create();
    private final BehaviorProcessor<MessageOverrides> overrides = BehaviorProcessor.createDefault(MessageOverrides.empty());
    /** 养殖场筛选的选项：当前养殖场 + 消息中出现的养殖场（不含邀请中的养殖场） */
    private final Map<Long, FarmBriefVO> knownFarms = Collections.synchronizedMap(new LinkedHashMap<>());
    @Nullable
    private Disposable pagingDisposable;
    private int nonce;
    private boolean staff;
    @Nullable
    private MessageBriefVO pendingReject;

    @Inject
    public MessageCenterViewModel(@NonNull MessageRepository repository, @NonNull MessageOpenCoordinator coordinator,
                                  @NonNull TodayServiceImpl todoService, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.coordinator = coordinator;
        this.todoService = todoService;
        this.farmContext = farmContext;
        staff = farmContext.isStaff();
        showFarmFilter.setValue(!staff);
        categoryOptions.setValue(staff ? STAFF_CATEGORIES : OWNER_CATEGORIES);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        String c = handle.get(STATE_CATEGORY);
        Boolean u = handle.get(STATE_UNREAD);
        category.setValue(Texts.isEmpty(c) ? null : c);
        unreadOnly.setValue(Boolean.TRUE.equals(u));
        applyFilter();
    }

    // ===================== 对外 LiveData =====================

    /** 分页数据（首次读取时开始分页） */
    public LiveData<PagingData<MessageRow>> getMessagePaging() {
        startPaging();
        return messagePaging;
    }

    public LiveData<String> getCategory() {
        return category;
    }

    public LiveData<FarmBriefVO> getFarmFilter() {
        return farmFilter;
    }

    public LiveData<Boolean> getUnreadOnly() {
        return unreadOnly;
    }

    public LiveData<Integer> getFilteredUnread() {
        return filteredUnread;
    }

    public LiveData<List<String>> getCategoryOptions() {
        return categoryOptions;
    }

    public LiveData<List<FarmBriefVO>> getFarmOptions() {
        return farmOptions;
    }

    public LiveData<Boolean> getShowFarmFilter() {
        return showFarmFilter;
    }

    public LiveData<Long> getActingActionId() {
        return actingActionId;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    public SingleLiveEvent<String> getOpenFilterSheet() {
        return openFilterSheet;
    }

    public SingleLiveEvent<MessageBriefVO> getOpenRejectDialog() {
        return openRejectDialog;
    }

    public boolean isStaff() {
        return staff;
    }

    /** 是否有筛选条件（决定空状态文案） */
    public boolean hasFilter() {
        return category.getValue() != null || farmFilter.getValue() != null || Boolean.TRUE.equals(unreadOnly.getValue());
    }

    @VisibleForTesting
    @NonNull
    MessageOverrides currentOverrides() {
        MessageOverrides o = overrides.getValue();
        return o == null ? MessageOverrides.empty() : o;
    }

    // ===================== 筛选 =====================

    public void onCategoryChipClick() {
        openFilterSheet.setValue(SHEET_CATEGORY);
    }

    public void onFarmChipClick() {
        publishFarmOptions();
        openFilterSheet.setValue(SHEET_FARM);
    }

    /** category 为空或 CATEGORY_ALL 表示全部类型 */
    public void setCategory(@Nullable String value) {
        String v = Texts.isEmpty(value) ? null : value;
        if (Objects.equals(v, category.getValue())) {
            return;
        }
        category.setValue(v);
        getSavedState().set(STATE_CATEGORY, v);
        applyFilter();
    }

    /** farm 为空表示全部养殖场（只筛选消息，不切换当前养殖场） */
    public void setFarm(@Nullable FarmBriefVO farm) {
        Long old = farmFilter.getValue() == null ? null : farmFilter.getValue().getFarmId();
        Long now = farm == null ? null : farm.getFarmId();
        if (Objects.equals(old, now)) {
            return;
        }
        farmFilter.setValue(farm);
        applyFilter();
    }

    public void setFarmById(long farmId) {
        setFarm(farmId <= 0 ? null : knownFarms.get(farmId));
    }

    public void toggleUnread() {
        boolean v = !Boolean.TRUE.equals(unreadOnly.getValue());
        unreadOnly.setValue(v);
        getSavedState().set(STATE_UNREAD, v);
        applyFilter();
    }

    public void clearFilters() {
        category.setValue(null);
        farmFilter.setValue(null);
        unreadOnly.setValue(false);
        getSavedState().set(STATE_CATEGORY, null);
        getSavedState().set(STATE_UNREAD, false);
        applyFilter();
    }

    /** 下拉刷新：清空覆盖表，以服务端为准 */
    public void refresh() {
        overrides.onNext(MessageOverrides.empty());
        nonce++;
        applyFilter();
    }

    @Override
    public void retry() {
        refresh();
    }

    private void applyFilter() {
        Long farmId = staff || farmFilter.getValue() == null ? null : farmFilter.getValue().getFarmId();
        filter.onNext(new Filter(category.getValue(), farmId, Boolean.TRUE.equals(unreadOnly.getValue()), nonce));
        loadUnread();
    }

    /** API-T13 当前筛选的未读数；失败静默 */
    private void loadUnread() {
        Filter f = filter.getValue();
        if (f == null) {
            return;
        }
        launch(repository.getUnreadCount(f.category, f.farmId), vo -> {
            filteredUnread.setValue(vo.getFilteredUnread());
            todoService.setUnread(vo.getTotalUnread());
        }, e -> Log.i(TAG, "未读数获取失败：" + e.getMessage()));
    }

    private void startPaging() {
        if (pagingDisposable != null) {
            return;
        }
        if (filter.getValue() == null) {
            applyFilter();
        }
        Executor executor = command -> Schedulers.computation().scheduleDirect(command);
        Flowable<PagingData<MessageBriefVO>> paged = filter.distinctUntilChanged()
                .switchMap(f -> repository.pager(f.category, f.farmId, f.unreadOnly,
                        page -> filteredUnread.postValue(page.getFilteredUnread())));
        Flowable<PagingData<MessageBriefVO>> cached = PagingRx.cachedIn(paged, ViewModelKt.getViewModelScope(this));
        pagingDisposable = Flowable.combineLatest(cached, overrides, (data, ov) -> transform(data, ov, executor))
                .subscribe(messagePaging::postValue, t -> Log.w(TAG, "消息分页失败", t));
        disposables.add(pagingDisposable);
    }

    @NonNull
    private PagingData<MessageRow> transform(@NonNull PagingData<MessageBriefVO> data, @NonNull MessageOverrides ov,
                                             @NonNull Executor executor) {
        PagingData<MessageBriefVO> visible = PagingDataTransforms.filter(data, executor,
                m -> !ov.isDeleted(m.getMessageId()));
        PagingData<MessageRow> rows = PagingDataTransforms.map(visible, executor, m -> {
            rememberFarm(m);
            return ov.apply(m);
        });
        return PagingDataTransforms.insertSeparators(rows, executor, (before, after) -> {
            if (after == null || after.getMessage() == null) {
                return null;
            }
            String group = after.getMessage().getDateGroup();
            String prev = before == null || before.getMessage() == null ? null : before.getMessage().getDateGroup();
            if (before == null || !Objects.equals(prev, group)) {
                return MessageRow.header(group == null ? "EARLIER" : group);
            }
            return null;
        });
    }

    private void rememberFarm(@NonNull MessageBriefVO m) {
        FarmBriefVO farm = m.getFarm();
        if (farm != null && farm.getFarmId() > 0 && !m.isInvite()) {
            knownFarms.put(farm.getFarmId(), farm);
        }
    }

    private void publishFarmOptions() {
        List<FarmBriefVO> list;
        synchronized (knownFarms) {
            list = new ArrayList<>(knownFarms.values());
        }
        farmOptions.setValue(Collections.unmodifiableList(list));
    }

    /** 当前养殖场 ID（筛选面板标出“当前”） */
    public long currentFarmId() {
        return farmContext.farmIdOrZero();
    }

    // ===================== 全部已读 =====================

    public void markAllRead() {
        Filter f = filter.getValue();
        MessageReadAllRequest body = new MessageReadAllRequest(f == null ? null : f.category, f == null ? null : f.farmId);
        launch(repository.readAll(body), result -> {
            todoService.setUnread(result.getTotalUnread());
            if (result.getUpdatedCount() == 0) {
                showMessage(MSG_NO_UNREAD);
                return;
            }
            overrides.onNext(currentOverrides().markAllRead());
            filteredUnread.setValue(0);
            showMessage(MSG_ALL_READ);
        });
    }

    // ===================== 打开消息 =====================

    public void openMessage(@NonNull MessageBriefVO m) {
        if (m.isInvite()) {
            return;
        }
        overrides.onNext(currentOverrides().markRead(m.getMessageId()));
        launch(coordinator.open(m).defaultIfEmpty(NavTarget.of(NavTarget.ROUTE_BACK)), target -> {
            if (!NavTarget.ROUTE_BACK.equals(target.getRoute())) {
                navEvent.setValue(target);
            }
            loadUnread();
        }, e -> {
            if (e.getCode() == ErrorCodes.NOT_FOUND) {
                showMessage(MSG_NOT_FOUND);
                refresh();
                return;
            }
            handleError(e);
        });
    }

    /** T07 删除后返回 */
    public void onNoticeDeleted(long messageId) {
        overrides.onNext(currentOverrides().delete(messageId));
        showMessage(UiMessage.withAction(MSG_DELETED, MSG_UNDO, ACTION_UNDO + messageId));
    }

    // ===================== 邀请 =====================

    public void acceptInvite(@NonNull MessageBriefVO m) {
        MessageActionVO action = m.getAction();
        if (action == null || actingActionId.getValue() != null) {
            return;
        }
        IFarmInviteService service = ServiceHub.get(IFarmInviteService.class);
        if (service == null) {
            showMessage(Router.MSG_NOT_READY);
            return;
        }
        actingActionId.setValue(action.getActionId());
        launch(service.acceptInvite(action.getActionId()), ctx -> {
            actingActionId.setValue(null);
            overrides.onNext(currentOverrides().setActionStatus(m.getMessageId(), MessageRow.STATUS_ACCEPTED));
            String name = m.getFarm() != null && m.getFarm().getName() != null ? m.getFarm().getName() : "";
            long farmId = m.getFarm() == null ? (ctx == null ? 0 : ctx.getFarmId()) : m.getFarm().getFarmId();
            showMessage(UiMessage.withAction("已加入" + name, MSG_SWITCH, ACTION_SWITCH + farmId));
        }, e -> onInviteError(m, e));
    }

    /** 点“拒绝”：先确认 */
    public void rejectInvite(@NonNull MessageBriefVO m) {
        if (m.getAction() != null && actingActionId.getValue() == null) {
            pendingReject = m;
            openRejectDialog.setValue(m);
        }
    }

    /** 拒绝对话框确认（对应最近一次点“拒绝”的邀请） */
    public void confirmPendingReject() {
        MessageBriefVO m = pendingReject;
        pendingReject = null;
        if (m != null) {
            confirmReject(m);
        }
    }

    /** 拒绝对话框确认 */
    public void confirmReject(@NonNull MessageBriefVO m) {
        MessageActionVO action = m.getAction();
        if (action == null) {
            return;
        }
        IFarmInviteService service = ServiceHub.get(IFarmInviteService.class);
        if (service == null) {
            showMessage(Router.MSG_NOT_READY);
            return;
        }
        actingActionId.setValue(action.getActionId());
        launch(service.rejectInvite(action.getActionId()), () -> {
            actingActionId.setValue(null);
            overrides.onNext(currentOverrides().setActionStatus(m.getMessageId(), MessageRow.STATUS_REJECTED));
            showMessage(MSG_REJECTED);
        }, e -> onInviteError(m, e));
    }

    private void onInviteError(@NonNull MessageBriefVO m, @NonNull ApiException e) {
        actingActionId.setValue(null);
        String text = e.getMessage();
        // 邀请已过期：按返回 message 提示，该消息副文字改为“员工 · 已过期”（错误码以模块02 为准，见待定事项 4）
        if (text != null && text.contains("过期")) {
            overrides.onNext(currentOverrides().setActionStatus(m.getMessageId(), MessageRow.STATUS_EXPIRED));
            showMessage(text);
            return;
        }
        handleError(e);
    }

    /** 操作在列表内进行，无权时只提示 */
    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        showMessage(e.getMessage());
    }

    /** Snackbar 操作：撤销删除、切换过去 */
    public void onMessageAction(@NonNull String actionId) {
        if (actionId.startsWith(ACTION_UNDO)) {
            undoDelete(parseLong(actionId.substring(ACTION_UNDO.length())));
        } else if (actionId.startsWith(ACTION_SWITCH)) {
            switchTo(parseLong(actionId.substring(ACTION_SWITCH.length())));
        }
    }

    private void switchTo(long farmId) {
        IFarmContextService service = ServiceHub.get(IFarmContextService.class);
        if (service == null || farmId <= 0) {
            showMessage(Router.MSG_NOT_READY);
            return;
        }
        launch(service.switchTo(farmId), ctx -> showMessage("已切换到" + Texts.orEmpty(ctx.getName())));
    }

    private static long parseLong(@NonNull String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ===================== 删除与撤销 =====================

    /** 长按菜单“删除消息”：API-T17 成功后加入已删除集合，Snackbar 带“撤销”4 秒（服务端 60 秒内可撤销） */
    public void deleteMessage(@NonNull MessageBriefVO m) {
        long id = m.getMessageId();
        launch(repository.delete(id), result -> {
            overrides.onNext(currentOverrides().delete(id));
            showMessage(UiMessage.withAction(MSG_DELETED, MSG_UNDO, ACTION_UNDO + id));
            loadUnread();
        }, e -> {
            if (e.getCode() == ErrorCodes.NOT_FOUND) {
                refresh();
            }
            handleError(e);
        });
    }

    /** 撤销：API-T18 成功后移出已删除集合并刷新；40903“已超过撤销时限” */
    public void undoDelete(long messageId) {
        if (messageId <= 0) {
            return;
        }
        launch(repository.restore(messageId), vo -> {
            overrides.onNext(currentOverrides().restore(messageId));
            repository.invalidate();
            loadUnread();
        }, this::handleError);
    }

    /** 数据已变化（40902 / 40903）：只提示，不整页重新加载 */
    @Override
    protected void onDataChanged(@NonNull ApiException e) {
    }

    // ===================== 筛选文字 =====================

    /** 类型名称（Chip 与面板） */
    @NonNull
    public static String categoryName(@Nullable String code) {
        if (code == null || code.isEmpty()) {
            return "全部类型";
        }
        switch (code) {
            case MessageBriefVO.CATEGORY_TODO:
                return "待办提醒";
            case MessageBriefVO.CATEGORY_AI:
                return "AI 诊断";
            case MessageBriefVO.CATEGORY_AUDIT:
                return "审核结果";
            case MessageBriefVO.CATEGORY_MEMBER:
                return "成员与养殖场";
            case MessageBriefVO.CATEGORY_LOT:
                return "批号停用";
            case MessageBriefVO.CATEGORY_NOTICE:
                return "平台通知";
            case MessageBriefVO.CATEGORY_OTHER:
            default:
                return "其他";
        }
    }

    /** 当前查询条件 */
    static final class Filter {
        @Nullable
        final String category;
        @Nullable
        final Long farmId;
        final boolean unreadOnly;
        final int nonce;

        Filter(@Nullable String category, @Nullable Long farmId, boolean unreadOnly, int nonce) {
            this.category = category;
            this.farmId = farmId;
            this.unreadOnly = unreadOnly;
            this.nonce = nonce;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Filter)) {
                return false;
            }
            Filter f = (Filter) o;
            return unreadOnly == f.unreadOnly && nonce == f.nonce && Objects.equals(category, f.category)
                    && Objects.equals(farmId, f.farmId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, farmId, unreadOnly, nonce);
        }
    }

    @VisibleForTesting
    @Nullable
    Filter currentFilter() {
        return filter.getValue();
    }
}
