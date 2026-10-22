package com.zhimu.slmp.common.ui.change;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.ui.change.model.vo.ChangeRequestItemVO;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

/**
 * U02 我的修改申请（02 号第十一节）：API-U03 分页（每页 20 条，按提交时间倒序），筛选“全部 / 待审核 / 已通过 / 已驳回”；
 * 点卡片进入原记录（属于其他养殖场时先切换），已驳回的“重新提交”进入 U01。
 */
public class MyChangeRequestsViewModel extends BaseListViewModel<String, ChangeRequestItemVO> {

    public static final String ACTION_SWITCH_BACK = "switch_back";

    private final ChangeRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<Map<String, Integer>> tabCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Integer> total = new MutableLiveData<>(0);
    private final SingleLiveEvent<ChangeRequestItemVO> openRecord = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openResubmit = new SingleLiveEvent<>();

    @Nullable
    private Long switchBackFarmId;

    @Inject
    public MyChangeRequestsViewModel(@NonNull ChangeRepository repository, @NonNull FarmContext farmContext) {
        super(null);
        this.repository = repository;
        this.farmContext = farmContext;
    }

    @NonNull
    @Override
    protected PagingSource<Integer, ChangeRequestItemVO> createPagingSource(@Nullable String status) {
        return repository.listSource(status, page -> {
            tabCounts.postValue(page.getTabCounts());
            total.postValue(page.getTotal());
        });
    }

    public LiveData<Map<String, Integer>> getTabCounts() {
        return tabCounts;
    }

    /** 当前筛选条件下的总数（“已显示全部 4 条”） */
    public LiveData<Integer> getTotal() {
        return total;
    }

    public SingleLiveEvent<ChangeRequestItemVO> getOpenRecord() {
        return openRecord;
    }

    public SingleLiveEvent<Long> getOpenResubmit() {
        return openResubmit;
    }

    /** 全部状态下的申请数（为 0 时不显示筛选行） */
    public int allCount() {
        Map<String, Integer> m = tabCounts.getValue();
        Integer all = m == null ? null : m.get("ALL");
        if (all != null) {
            return all;
        }
        int sum = 0;
        if (m != null) {
            for (Integer v : m.values()) {
                sum += v == null ? 0 : v;
            }
        }
        return sum;
    }

    /** 点筛选 Chip（null 为全部） */
    public void selectStatus(@Nullable String status) {
        setFilter(status);
    }

    /** 点申请卡片：原记录属于其他养殖场时先切换（Snackbar“已切换到西岭鸡场”+“切回东山猪场”） */
    public void onItemClick(@NonNull ChangeRequestItemVO item) {
        Long current = farmContext.currentFarmId();
        IFarmContextService farm = ServiceHub.get(IFarmContextService.class);
        if (item.getFarmId() == 0 || current == null || current == item.getFarmId() || farm == null) {
            openRecord.setValue(item);
            return;
        }
        FarmContextVO before = farmContext.current();
        launch(farm.switchTo(item.getFarmId()), ctx -> {
            switchBackFarmId = current;
            String name = item.getFarmName() == null ? ctx.getName() : item.getFarmName();
            String backName = before == null || before.getName() == null ? "" : before.getName();
            showMessage(UiMessage.withAction("已切换到" + name, "切回" + backName, ACTION_SWITCH_BACK));
            openRecord.setValue(item);
        });
    }

    /** Snackbar“切回东山猪场” */
    public void switchBack() {
        IFarmContextService farm = ServiceHub.get(IFarmContextService.class);
        Long back = switchBackFarmId;
        if (farm == null || back == null) {
            return;
        }
        switchBackFarmId = null;
        launch(farm.switchTo(back), ctx -> showMessage("已切换到" + ctx.getName()));
    }

    public void onResubmitClick(@NonNull ChangeRequestItemVO item) {
        openResubmit.setValue(item.getId());
    }
}
