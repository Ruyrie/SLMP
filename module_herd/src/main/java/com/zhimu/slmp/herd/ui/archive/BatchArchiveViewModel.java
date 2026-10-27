package com.zhimu.slmp.herd.ui.archive;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.PagingData;
import androidx.paging.rxjava3.PagingRx;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.TimelineItemVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.MenuItemSpec;
import com.zhimu.slmp.herd.ui.common.NavTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * L06 批次档案：头部信息卡、指标、入栏信息、休药横幅、生产记录时间线；养殖户更多菜单（转栏、登记死淘、提交修改申请、删除批次），
 * 员工 FAB“登记死淘”；跨场扫码打开时显示养殖场标签与“切回”Snackbar。
 */
public class BatchArchiveViewModel extends BaseViewModel {

    public static final int MENU_TRANSFER = 1;
    public static final int MENU_DEATH = 2;
    public static final int MENU_CHANGE_REQUEST = 3;
    public static final int MENU_DELETE = 4;
    public static final int MENU_EDIT_ENTRY = 5;
    public static final int MENU_RECORD_EDIT = 11;
    public static final int MENU_RECORD_REQUEST = 12;
    public static final int MENU_RECORD_VIEW_SALE = 13;
    /** Snackbar“切回东山猪场”的 actionId */
    public static final String ACTION_SWITCH_BACK = "herd_switch_back";
    static final String KEY_SWITCH_SHOWN = "herd_switch_shown";

    /** 长按记录的菜单 */
    public static final class RecordMenu {
        public final TimelineItemVO item;
        public final List<MenuItemSpec> items;

        RecordMenu(@NonNull TimelineItemVO item, @NonNull List<MenuItemSpec> items) {
            this.item = item;
            this.items = items;
        }
    }

    private final BatchRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<BatchDetailVO> detail = new MutableLiveData<>();
    private final LiveData<Boolean> isBreeding;
    private final LiveData<Boolean> showFab;
    private final LiveData<Boolean> showMore;
    private final LiveData<String> withdrawalTitle;
    private final LiveData<String> withdrawalText;
    private final LiveData<Integer> pendingCount;
    private final MutableLiveData<String> timelineType = new MutableLiveData<>(null);
    private final MutableLiveData<PagingData<TimelineItemVO>> timelinePaging = new MutableLiveData<>();
    private final MutableLiveData<Set<Long>> expandedIds = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<Boolean> deleting = new MutableLiveData<>(false);
    private final MutableLiveData<String> crossFarmName = new MutableLiveData<>();
    private final SingleLiveEvent<List<MenuItemSpec>> menuEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<RecordMenu> recordMenuEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> deleteConfirmEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<BatchDetailVO> qrEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<UiMessage> finishEvent = new SingleLiveEvent<>();

    private long batchId;
    @Nullable
    private Long fromFarmId;
    @Nullable
    private String fromFarmName;
    @Nullable
    private Disposable timelineDisposable;

    @Inject
    public BatchArchiveViewModel(@NonNull BatchRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.farmContext = farmContext;
        isBreeding = Transformations.map(detail, d -> d != null && d.isBreeding());
        showFab = Transformations.map(detail, d -> d != null && isStaff() && d.isActive()
                && d.getActions() != null && d.getActions().isCanRegisterDeath());
        showMore = Transformations.map(detail, d -> d != null && !isStaff() && d.getActions() != null
                && d.getActions().any());
        withdrawalTitle = Transformations.map(detail, d -> {
            BatchDetailVO.WithdrawalInfo w = d == null ? null : d.getWithdrawal();
            if (w == null || !w.isInWithdrawal()) {
                return null;
            }
            return w.getRemainingDays() == null ? "休药中。" : "休药中，剩 " + w.getRemainingDays() + " 天。";
        });
        withdrawalText = Transformations.map(detail, d -> {
            BatchDetailVO.WithdrawalInfo w = d == null ? null : d.getWithdrawal();
            if (w == null || !w.isInWithdrawal()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            if (w.getSellableDate() != null) {
                sb.append("可出栏日期 ").append(HerdFormat.date(w.getSellableDate())).append("。");
            }
            if (w.getLastDrugName() != null) {
                sb.append(w.getLastDrugName()).append("末次用药 ").append(HerdFormat.date(w.getLastDrugDate()));
                if (w.getWithdrawalDays() != null) {
                    sb.append("，休药期 ").append(w.getWithdrawalDays()).append(" 天");
                }
                sb.append("。");
            }
            return sb.toString();
        });
        pendingCount = Transformations.map(detail, d -> {
            Integer c = d == null ? null : d.getPendingChangeRequestCount();
            return c == null ? 0 : c;
        });
    }

    public boolean isStaff() {
        return farmContext.isStaff();
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Object id = handle.get(RouterPath.EXTRA_BATCH_ID);
        batchId = id instanceof Number ? ((Number) id).longValue() : 0L;
        Object from = handle.get(RouterPath.EXTRA_FROM_FARM_ID);
        fromFarmId = from instanceof Number ? ((Number) from).longValue() : null;
        fromFarmName = handle.get(RouterPath.EXTRA_FROM_FARM_NAME);
        String cross = handle.get(RouterPath.EXTRA_CROSS_FARM_NAME);
        crossFarmName.setValue(cross);
        load();
        setTimelineType(null);
        if (fromFarmId != null && fromFarmName != null && !Boolean.TRUE.equals(handle.get(KEY_SWITCH_SHOWN))) {
            handle.set(KEY_SWITCH_SHOWN, true);
            FarmContextVO c = farmContext.current();
            String now = cross != null ? cross : c == null ? "" : c.getName();
            showMessage(UiMessage.withAction("已切换到" + now, "切回" + fromFarmName, ACTION_SWITCH_BACK));
        }
    }

    // ===== 对外 =====

    public LiveData<BatchDetailVO> getDetail() {
        return detail;
    }

    public LiveData<Boolean> getIsBreeding() {
        return isBreeding;
    }

    public LiveData<Boolean> getShowFab() {
        return showFab;
    }

    public LiveData<Boolean> getShowMore() {
        return showMore;
    }

    public LiveData<String> getWithdrawalTitle() {
        return withdrawalTitle;
    }

    public LiveData<String> getWithdrawalText() {
        return withdrawalText;
    }

    public LiveData<Integer> getPendingCount() {
        return pendingCount;
    }

    public LiveData<String> getTimelineType() {
        return timelineType;
    }

    public LiveData<PagingData<TimelineItemVO>> getTimelinePaging() {
        return timelinePaging;
    }

    public LiveData<Set<Long>> getExpandedIds() {
        return expandedIds;
    }

    public LiveData<Boolean> getDeleting() {
        return deleting;
    }

    /** 跨场扫码打开时批次所属养殖场（头部养殖场标签），否则为 null */
    public LiveData<String> getCrossFarmName() {
        return crossFarmName;
    }

    public SingleLiveEvent<List<MenuItemSpec>> getMenuEvent() {
        return menuEvent;
    }

    public SingleLiveEvent<RecordMenu> getRecordMenuEvent() {
        return recordMenuEvent;
    }

    public SingleLiveEvent<String> getDeleteConfirmEvent() {
        return deleteConfirmEvent;
    }

    public SingleLiveEvent<BatchDetailVO> getQrEvent() {
        return qrEvent;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    public SingleLiveEvent<UiMessage> getFinishEvent() {
        return finishEvent;
    }

    // ===== 加载 =====

    /** API-L15 */
    public void load() {
        load(repository.getBatchDetail(batchId), detail::setValue);
    }

    /** 下拉刷新、从 L10 / L11 / U01 返回：档案与时间线 */
    public void refresh() {
        load();
        setTimelineType(timelineType.getValue());
    }

    @Override
    public void retry() {
        refresh();
    }

    public void onChildResult(int code) {
        if (code == Activity.RESULT_OK) {
            refresh();
        }
    }

    /** API-L16 按类型筛选，从第 1 页重新加载 */
    public void setTimelineType(@Nullable String type) {
        timelineType.setValue(type);
        expandedIds.setValue(Collections.emptySet());
        if (timelineDisposable != null) {
            timelineDisposable.dispose();
        }
        timelineDisposable = PagingRx.cachedIn(repository.pageTimeline(batchId, type),
                        ViewModelKt.getViewModelScope(this))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(timelinePaging::setValue, t -> {
                });
        disposables.add(timelineDisposable);
    }

    // ===== 时间线 =====

    /** expandable 时展开，有 route 时跳转（称重 G06、免疫 D01、用药 D05、巡检 I04、出栏 E07） */
    public void onRecordClick(@NonNull TimelineItemVO item) {
        if (item.isExpandable()) {
            Set<Long> ids = new HashSet<>(Objects.requireNonNull(expandedIds.getValue()));
            if (!ids.remove(item.getId())) {
                ids.add(item.getId());
            }
            expandedIds.setValue(ids);
            return;
        }
        NavTarget t = NavTarget.ofPage(item.getRoute());
        if (t != null) {
            navEvent.setValue(t.put(RouterPath.EXTRA_BATCH_ID, batchId)
                    .put(RouterPath.EXTRA_BIZ_TYPE, item.getBizType())
                    .put(RouterPath.EXTRA_BIZ_ID, item.getBizId()));
        }
    }

    /** EDIT“修改记录”；CHANGE_REQUEST“提交修改申请 需审核”；NONE 不弹 */
    public void onRecordLongClick(@NonNull TimelineItemVO item) {
        List<MenuItemSpec> items = new ArrayList<>();
        if (TimelineItemVO.TYPE_SALE.equals(item.getRecordType()) && item.getBizId() > 0) {
            items.add(MenuItemSpec.normal(MENU_RECORD_VIEW_SALE, "查看出栏单", R.drawable.ic_edit_note));
        } else if (TimelineItemVO.ACTION_EDIT.equals(item.getAction())) {
            items.add(MenuItemSpec.normal(MENU_RECORD_EDIT, "修改记录", R.drawable.ic_edit));
        } else if (TimelineItemVO.ACTION_CHANGE_REQUEST.equals(item.getAction())) {
            items.add(MenuItemSpec.review(MENU_RECORD_REQUEST, "提交修改申请", R.drawable.ic_edit_note));
        }
        if (!items.isEmpty()) {
            recordMenuEvent.setValue(new RecordMenu(item, items));
        }
    }

    /** 入栏 → L08（batch_id、entry_event_id），转栏 → L10、死淘 → L11（event_id）；申请 → U01 */
    public void onRecordMenuSelected(@NonNull TimelineItemVO item, int menuId) {
        String type = item.getRecordType();
        if (item.getBizId() <= 0) {
            return;
        }
        if (TimelineItemVO.TYPE_SALE.equals(type)) {
            // 已收款与未收款的编辑规则不同，交由出栏详情的 actions 决定。
            if (menuId == MENU_RECORD_VIEW_SALE || menuId == MENU_RECORD_REQUEST || menuId == MENU_RECORD_EDIT) {
                navEvent.setValue(NavTarget.of(RouterPath.E07)
                        .put(RouterPath.EXTRA_SALE_ORDER_ID, item.getBizId()).forResult());
            }
            return;
        }
        if (menuId == MENU_RECORD_REQUEST) {
            if (!TimelineItemVO.ACTION_CHANGE_REQUEST.equals(item.getAction())) {
                return;
            }
            String bizType = TimelineItemVO.TYPE_ENTRY.equals(type) ? HerdExtras.BIZ_BATCH_ENTRY
                    : TimelineItemVO.TYPE_TRANSFER.equals(type) ? HerdExtras.BIZ_BATCH_TRANSFER
                    : TimelineItemVO.TYPE_DEATH.equals(type) ? HerdExtras.BIZ_BATCH_DEATH : null;
            if (bizType == null) {
                return;
            }
            navEvent.setValue(NavTarget.of(RouterPath.U01).put(RouterPath.EXTRA_BIZ_TYPE, bizType)
                    .put(RouterPath.EXTRA_BIZ_ID, item.getBizId()).forResult());
            return;
        }
        if (menuId != MENU_RECORD_EDIT || !TimelineItemVO.ACTION_EDIT.equals(item.getAction())) {
            return;
        }
        if (TimelineItemVO.TYPE_ENTRY.equals(type)) {
            navEvent.setValue(NavTarget.of(RouterPath.L08).put(RouterPath.EXTRA_BATCH_ID, batchId)
                    .put(RouterPath.EXTRA_ENTRY_EVENT_ID, item.getBizId()).forResult());
        } else if (TimelineItemVO.TYPE_TRANSFER.equals(type)) {
            navEvent.setValue(NavTarget.of(RouterPath.L10).put(RouterPath.EXTRA_BATCH_ID, batchId)
                    .put(RouterPath.EXTRA_EVENT_ID, item.getBizId()).forResult());
        } else if (TimelineItemVO.TYPE_DEATH.equals(type)) {
            navEvent.setValue(NavTarget.of(RouterPath.L11).put(RouterPath.EXTRA_BATCH_ID, batchId)
                    .put(RouterPath.EXTRA_EVENT_ID, item.getBizId()).forResult());
        }
    }

    // ===== 更多菜单 =====

    /** 按 actions 组装；员工不显示更多菜单 */
    public void onMoreClick() {
        BatchDetailVO d = detail.getValue();
        if (d == null || isStaff() || d.getActions() == null) {
            return;
        }
        BatchDetailVO.BatchActions a = d.getActions();
        List<MenuItemSpec> items = new ArrayList<>();
        if (a.isCanTransfer()) {
            items.add(MenuItemSpec.normal(MENU_TRANSFER, "转栏", R.drawable.ic_swap_horiz));
        }
        if (a.isCanRegisterDeath()) {
            items.add(MenuItemSpec.normal(MENU_DEATH, "登记死淘", R.drawable.ic_heart_minus));
        }
        if (a.isCanEditEntry()) {
            items.add(MenuItemSpec.normal(MENU_EDIT_ENTRY, "修改入栏记录", R.drawable.ic_edit));
        } else if (a.isCanSubmitChangeRequest()) {
            items.add(MenuItemSpec.review(MENU_CHANGE_REQUEST, "提交修改申请", R.drawable.ic_edit_note));
        }
        if (a.isCanDelete()) {
            items.add(MenuItemSpec.danger(MENU_DELETE, "删除批次", R.drawable.ic_delete, true, null));
        }
        menuEvent.setValue(items);
    }

    public void onMenuSelected(int id) {
        BatchDetailVO d = detail.getValue();
        if (d == null) {
            return;
        }
        switch (id) {
            case MENU_TRANSFER:
                navEvent.setValue(NavTarget.of(RouterPath.L10).put(RouterPath.EXTRA_BATCH_ID, batchId).forResult());
                break;
            case MENU_DEATH:
                onFabClick();
                break;
            case MENU_EDIT_ENTRY:
                if (d.getActions() != null && d.getActions().isCanEditEntry() && d.getEntryEventId() > 0) {
                    navEvent.setValue(NavTarget.of(RouterPath.L08).put(RouterPath.EXTRA_BATCH_ID, batchId)
                            .put(RouterPath.EXTRA_ENTRY_EVENT_ID, d.getEntryEventId()).forResult());
                }
                break;
            case MENU_CHANGE_REQUEST:
                navEvent.setValue(NavTarget.of(RouterPath.U01).put(RouterPath.EXTRA_BIZ_TYPE, HerdExtras.BIZ_BATCH_ENTRY)
                        .put(RouterPath.EXTRA_BIZ_ID, d.getEntryEventId()).forResult());
                break;
            case MENU_DELETE:
                deleteConfirmEvent.setValue(d.getBatchNo());
                break;
            default:
                break;
        }
    }

    /** 员工 FAB / 菜单“登记死淘” → L11 预选本批次 */
    public void onFabClick() {
        navEvent.setValue(NavTarget.of(RouterPath.L11).put(RouterPath.EXTRA_BATCH_ID, batchId).forResult());
    }

    /** 顶部栏 qr_code_2、“批次二维码”按钮 */
    public void onQrClick() {
        BatchDetailVO d = detail.getValue();
        if (d != null) {
            qrEvent.setValue(d);
        }
    }

    /** 待审核 Notice“查看” → U02 */
    public void onPendingClick() {
        navEvent.setValue(NavTarget.of(RouterPath.U02));
    }

    /** API-L28 删除批次（确认对话框之后） */
    public void confirmDelete() {
        BatchDetailVO d = detail.getValue();
        if (d == null || Boolean.TRUE.equals(deleting.getValue())) {
            return;
        }
        deleting.setValue(true);
        launch(repository.deleteBatch(d.getBatchId(), d.getVersion()), () -> {
            deleting.setValue(false);
            finishEvent.setValue(UiMessage.of("已删除批次 " + d.getBatchNo()));
        }, e -> {
            deleting.setValue(false);
            handleError(e);
            if (e.getCode() == ErrorCodes.CHANGE_REQUEST_REQUIRED || e.getCode() == ErrorCodes.STATE_NOT_ALLOWED) {
                load();
            }
        });
    }

    /** Snackbar“切回东山猪场”：IFarmContextService.switchTo（API-F03），关闭本档案 */
    public void switchBack() {
        Long target = fromFarmId;
        if (target == null) {
            return;
        }
        IFarmContextService service = ServiceHub.get(IFarmContextService.class);
        if (service == null) {
            showMessage("暂时无法切换养殖场，请从顶部养殖场切换器切换");
            return;
        }
        launch(service.switchTo(target), ctx -> finishEvent.setValue(UiMessage.of("已切换到" + ctx.getName())));
    }

    /** 41001 批次已删除 → T08 */
    public static boolean isDeleted(@Nullable ApiException e) {
        return e != null && e.getCode() == ErrorCodes.RECORD_DELETED;
    }
}
