package com.zhimu.slmp.herd.ui.pendetail;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.PenRepository;
import com.zhimu.slmp.herd.model.request.PenStatusRequest;
import com.zhimu.slmp.herd.model.vo.PenDetailVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.MenuItemSpec;
import com.zhimu.slmp.herd.ui.common.NavTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * L04 栏舍详情：容量占用、栏舍信息、负责员工、在栏批次；更多菜单编辑、修改记录、停用 / 启用。
 */
public class PenDetailViewModel extends BaseViewModel {

    public static final int MENU_EDIT = 1;
    public static final int MENU_LOG = 2;
    public static final int MENU_DISABLE = 3;
    public static final int MENU_ENABLE = 4;

    private final PenRepository repository;
    private final BaseDataRepository baseData;

    private final MutableLiveData<PenDetailVO> pen = new MutableLiveData<>();
    private final LiveData<String> headerSub;
    private final LiveData<String> capacityHint;
    /** 修改记录（API-U14 biz_type = PEN）；lib_common 的 BaseDataRepository 返回整页列表，不分页 */
    private final MutableLiveData<List<RecordLogVO>> changeLogs = new MutableLiveData<>();
    private final MutableLiveData<Boolean> statusSubmitting = new MutableLiveData<>(false);
    private final SingleLiveEvent<List<MenuItemSpec>> menuEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> disableConfirmEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> changeLogEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();

    private long penId;

    @Inject
    public PenDetailViewModel(@NonNull PenRepository repository, @NonNull BaseDataRepository baseData) {
        this.repository = repository;
        this.baseData = baseData;
        headerSub = Transformations.map(pen, p -> p == null ? "" : p.getCategoryName() + HerdFormat.DOT
                + statusText(p));
        capacityHint = Transformations.map(pen, p -> {
            if (p == null || p.isDisabled()) {
                return null;
            }
            String unit = p.getUnitText();
            if (p.getOverCapacityCount() > 0) {
                return "已超出容量 " + HerdFormat.qty(p.getOverCapacityCount(), unit);
            }
            return "还可转入 " + HerdFormat.qty(p.getRemainingCapacity(), unit) + "，超过容量时需要确认后才能保存";
        });
    }

    @NonNull
    static String statusText(@NonNull PenDetailVO p) {
        if (p.isDisabled()) {
            return "已停用";
        }
        return p.getDisplayStatusText() != null && !PenDetailVO.STATUS_DISABLED.equals(p.getDisplayStatus())
                && !"EMPTY".equals(p.getDisplayStatus()) ? p.getDisplayStatusText() : "在用";
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Object id = handle.get(RouterPath.EXTRA_PEN_ID);
        penId = id instanceof Number ? ((Number) id).longValue() : 0L;
        load(penId);
    }

    // ===== 对外 =====

    public LiveData<PenDetailVO> getPen() {
        return pen;
    }

    public LiveData<String> getHeaderSub() {
        return headerSub;
    }

    public LiveData<String> getCapacityHint() {
        return capacityHint;
    }

    public LiveData<List<RecordLogVO>> getChangeLogs() {
        return changeLogs;
    }

    public LiveData<Boolean> getStatusSubmitting() {
        return statusSubmitting;
    }

    public SingleLiveEvent<List<MenuItemSpec>> getMenuEvent() {
        return menuEvent;
    }

    public SingleLiveEvent<String> getDisableConfirmEvent() {
        return disableConfirmEvent;
    }

    public SingleLiveEvent<Void> getChangeLogEvent() {
        return changeLogEvent;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    // ===== 加载 =====

    /** API-L08 */
    public void load(long penId) {
        this.penId = penId;
        load(repository.getPenDetail(penId), pen::setValue);
    }

    @Override
    public void retry() {
        load(penId);
    }

    /** L05 编辑、L06 返回后刷新 */
    public void onChildResult(int code) {
        if (code == Activity.RESULT_OK) {
            load(penId);
        }
    }

    // ===== 更多菜单 =====

    /** 按 status、can_disable、disable_block_reason、can_enable 组装 */
    public void onMoreClick() {
        PenDetailVO p = pen.getValue();
        if (p == null) {
            return;
        }
        List<MenuItemSpec> items = new ArrayList<>();
        items.add(MenuItemSpec.normal(MENU_EDIT, "编辑栏舍", R.drawable.ic_edit));
        items.add(MenuItemSpec.normal(MENU_LOG, "查看修改记录", R.drawable.ic_history));
        if (p.isDisabled()) {
            // 启用栏舍：分隔线后、primary 色，不是危险项，不弹确认对话框
            items.add(new MenuItemSpec(MENU_ENABLE, "启用栏舍", R.drawable.ic_restart_alt, null, false,
                    p.isCanEnable(), false, true));
        } else {
            items.add(MenuItemSpec.danger(MENU_DISABLE, "停用栏舍", R.drawable.ic_block, p.isCanDisable(),
                    p.isCanDisable() ? null : p.getDisableBlockReason()));
        }
        menuEvent.setValue(items);
    }

    public void onMenuSelected(int id) {
        PenDetailVO p = pen.getValue();
        if (p == null) {
            return;
        }
        switch (id) {
            case MENU_EDIT:
                navEvent.setValue(NavTarget.of(RouterPath.L05).put(RouterPath.EXTRA_PEN_ID, p.getPenId()).forResult());
                break;
            case MENU_LOG:
                openChangeLog();
                break;
            case MENU_DISABLE:
                disableConfirmEvent.setValue(p.getPenName());
                break;
            case MENU_ENABLE:
                enable();
                break;
            default:
                break;
        }
    }

    /** API-L13 停用（确认对话框之后） */
    public void confirmDisable() {
        PenDetailVO p = pen.getValue();
        if (p == null || Boolean.TRUE.equals(statusSubmitting.getValue())) {
            return;
        }
        statusSubmitting.setValue(true);
        launch(repository.disablePen(p.getPenId(), new PenStatusRequest(p.getVersion())), vo -> {
            statusSubmitting.setValue(false);
            pen.setValue(vo);
            showMessage("已停用 " + vo.getPenName());
        }, e -> {
            statusSubmitting.setValue(false);
            handleError(e);
        });
    }

    /** API-L27 启用（不弹确认） */
    public void enable() {
        PenDetailVO p = pen.getValue();
        if (p == null || Boolean.TRUE.equals(statusSubmitting.getValue())) {
            return;
        }
        statusSubmitting.setValue(true);
        launch(repository.enablePen(p.getPenId(), new PenStatusRequest(p.getVersion())), vo -> {
            statusSubmitting.setValue(false);
            pen.setValue(vo);
            showMessage("已启用 " + vo.getPenName());
        }, e -> {
            statusSubmitting.setValue(false);
            handleError(e);
        });
    }

    /** API-U14 修改记录（底部面板） */
    public void openChangeLog() {
        changeLogs.setValue(null);
        changeLogEvent.call();
        launch(baseData.getRecordLogs(HerdExtras.BIZ_PEN, penId), list -> changeLogs.setValue(list),
                e -> {
                    changeLogs.setValue(Collections.emptyList());
                    showMessage(e.getMessage());
                });
    }

    // ===== 点击 =====

    public void onStaffClick(@NonNull UserBriefVO user) {
        navEvent.setValue(NavTarget.of(RouterPath.F09).put(RouterPath.EXTRA_USER_ID, user.getUserId()));
    }

    public void onAssignClick() {
        navEvent.setValue(NavTarget.of(RouterPath.F07));
    }

    public void onBatchClick(long batchId) {
        navEvent.setValue(NavTarget.of(RouterPath.L06).put(RouterPath.EXTRA_BATCH_ID, batchId).forResult());
    }
}
