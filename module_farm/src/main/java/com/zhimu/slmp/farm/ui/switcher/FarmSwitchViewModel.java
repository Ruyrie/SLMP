package com.zhimu.slmp.farm.ui.switcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.model.vo.FarmListItemVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.service.FarmContextServiceImpl;

import javax.inject.Inject;

/**
 * F01 切换养殖场（模块02 技术文档 2.4.1）：宿主 Activity 范围，由 IFarmContextService.showSwitcher 打开。
 * 点已通过的养殖场调 API-F03（经 FarmContextServiceImpl.switchTo，成功即写入 FarmContext）；
 * 点待审核 / 已驳回进入 F03；点已冻结进入 U03；“全部养殖场”只在 T03、T06、E02、E13 可选。
 */
public class FarmSwitchViewModel extends BaseViewModel {

    static final String MSG_FROZEN = "%s已被平台冻结，暂时不能使用。原因见消息中心。";
    static final String MSG_NOT_MEMBER = "你已不是%s的成员";
    static final String MSG_CANNOT_SWITCH_PENDING = "%s审核中，暂不能切换";
    static final String MSG_CANNOT_SWITCH_REJECTED = "%s未通过审核，暂不能切换";
    static final String MSG_CANNOT_SWITCH = "%s暂时不能切换";

    private final FarmRepository repository;
    private final FarmContextServiceImpl contextService;
    private final FarmContext farmContext;

    @Nullable
    private LiveData<PagingData<FarmListItemVO>> farms;
    @Nullable
    private PagingSource<Integer, FarmListItemVO> currentSource;

    private final MutableLiveData<Boolean> allFarmsEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> allFarmsChecked = new MutableLiveData<>(false);
    private final MutableLiveData<Long> switchingFarmId = new MutableLiveData<>(null);

    private final SingleLiveEvent<FarmContextVO> switched = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openAudit = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> openFrozen = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> allFarmsChosen = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openCreate = new SingleLiveEvent<>();

    @Inject
    public FarmSwitchViewModel(@NonNull FarmRepository repository, @NonNull FarmContextServiceImpl contextService,
                               @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.contextService = contextService;
        this.farmContext = farmContext;
    }

    /** 面板打开时传入启动参数（ViewModel 在宿主 Activity 范围，参数来自面板 arguments） */
    public void setup(boolean allowAllFarms, boolean inAllFarms) {
        allFarmsEnabled.setValue(allowAllFarms);
        allFarmsChecked.setValue(allowAllFarms && inAllFarms);
        switchingFarmId.setValue(null);
    }

    /** API-F01（relation = ALL）分页数据，首次读取时创建 */
    @NonNull
    public LiveData<PagingData<FarmListItemVO>> getFarms() {
        if (farms == null) {
            Pager<Integer, FarmListItemVO> pager = new Pager<>(
                    new PagingConfig(BaseListViewModel.PAGE_SIZE, BaseListViewModel.PREFETCH_DISTANCE, false,
                            BaseListViewModel.PAGE_SIZE),
                    () -> {
                        currentSource = repository.farmPagingSource(FarmRepository.RELATION_ALL);
                        return currentSource;
                    });
            farms = PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        }
        return farms;
    }

    /** 重新加载（再次打开面板、“重新加载”） */
    public void load() {
        if (currentSource != null) {
            currentSource.invalidate();
        }
    }

    @Override
    public void retry() {
        load();
    }

    public void onFarmClick(@NonNull FarmListItemVO item) {
        if (switchingFarmId.getValue() != null) {
            return;
        }
        String status = item.getStatus();
        if (FarmContextVO.STATUS_PENDING.equals(status) || FarmContextVO.STATUS_REJECTED.equals(status)) {
            openAudit.setValue(item.getFarmId());
            return;
        }
        if (FarmContextVO.STATUS_FROZEN.equals(status)) {
            openFrozen.setValue(frozenText(item.getName()));
            return;
        }
        FarmContextVO current = farmContext.current();
        if (item.isCurrent() && current != null && current.getFarmId() == item.getFarmId()
                && !Boolean.TRUE.equals(allFarmsChecked.getValue())) {
            switched.setValue(current);
            return;
        }
        if (!item.isCanSwitch()) {
            showMessage(cannotSwitchText(item));
            return;
        }
        switchingFarmId.setValue(item.getFarmId());
        launch(contextService.switchTo(item.getFarmId()), vo -> {
            switchingFarmId.setValue(null);
            if (vo.getFarmId() != item.getFarmId()) {
                showMessage(String.format(MSG_NOT_MEMBER, nameOf(item.getName())));
            }
            allFarmsChecked.setValue(false);
            switched.setValue(vo);
        }, e -> {
            switchingFarmId.setValue(null);
            onSwitchError(item, e);
        });
    }

    private void onSwitchError(@NonNull FarmListItemVO item, @NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.FARM_STATUS_FORBIDDEN) {
            openAudit.setValue(item.getFarmId());
        } else if (code == ErrorCodes.FARM_FROZEN) {
            openFrozen.setValue(frozenText(item.getName()));
        } else {
            handleError(e);
            if (ErrorCodes.isNoPermission(code)) {
                load();
            }
        }
    }

    /** 面板内操作无权限时只提示，不切换整页状态 */
    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        showMessage(e.getMessage());
    }

    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        load();
    }

    public void onAllFarmsClick() {
        if (!Boolean.TRUE.equals(allFarmsEnabled.getValue())) {
            return;
        }
        allFarmsChecked.setValue(true);
        allFarmsChosen.call();
    }

    public void onCreateClick() {
        openCreate.call();
    }

    /** 服务端标记不能切换时按状态提示（待审核、已驳回一般先进入审核结果页，这里兜底） */
    @NonNull
    static String cannotSwitchText(@NonNull FarmListItemVO item) {
        String status = item.getStatus();
        String name = nameOf(item.getName());
        if (FarmContextVO.STATUS_PENDING.equals(status)) {
            return String.format(MSG_CANNOT_SWITCH_PENDING, name);
        }
        if (FarmContextVO.STATUS_REJECTED.equals(status)) {
            return String.format(MSG_CANNOT_SWITCH_REJECTED, name);
        }
        if (FarmContextVO.STATUS_FROZEN.equals(status)) {
            return frozenText(item.getName());
        }
        return String.format(MSG_CANNOT_SWITCH, name);
    }

    @NonNull
    static String frozenText(@Nullable String name) {
        return String.format(MSG_FROZEN, nameOf(name));
    }

    @NonNull
    private static String nameOf(@Nullable String name) {
        return name == null ? "该养殖场" : name;
    }

    // ===== 对外 LiveData =====

    public LiveData<Boolean> getAllFarmsEnabled() {
        return allFarmsEnabled;
    }

    public LiveData<Boolean> getAllFarmsChecked() {
        return allFarmsChecked;
    }

    public LiveData<Long> getSwitchingFarmId() {
        return switchingFarmId;
    }

    /** 当前养殖场（MainActivity 同时观察 FarmContext 重建导航） */
    public LiveData<FarmContextVO> getCurrentFarm() {
        return farmContext.getContext();
    }

    public SingleLiveEvent<FarmContextVO> getSwitched() {
        return switched;
    }

    public SingleLiveEvent<Long> getOpenAudit() {
        return openAudit;
    }

    public SingleLiveEvent<String> getOpenFrozen() {
        return openFrozen;
    }

    public SingleLiveEvent<Void> getAllFarmsChosen() {
        return allFarmsChosen;
    }

    public SingleLiveEvent<Void> getOpenCreate() {
        return openCreate;
    }
}
