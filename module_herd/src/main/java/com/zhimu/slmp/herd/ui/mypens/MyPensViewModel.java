package com.zhimu.slmp.herd.ui.mypens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.data.StockRepository;
import com.zhimu.slmp.herd.model.vo.MyPenBatchVO;
import com.zhimu.slmp.herd.model.vo.MyPenItemVO;
import com.zhimu.slmp.herd.model.vo.MyPensVO;
import com.zhimu.slmp.herd.model.vo.TodoBriefVO;
import com.zhimu.slmp.herd.ui.common.NavTarget;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

/**
 * L02 我的栏舍（员工）：负责栏舍、在栏批次与今日任务。
 */
public class MyPensViewModel extends BaseViewModel {

    /**
     * 显示“待产与待出雏”入口的栏舍类型：产房、母猪舍、种鸡舍。PEN_TYPE 编码由管理端-03 维护尚未给出（待定事项 3），
     * 暂按 Mock 字典编码。
     */
    static final Set<String> BREEDING_PEN_TYPES = new HashSet<>(Arrays.asList("FARROWING", "SOW", "BREEDER"));

    private final StockRepository repository;

    private final MutableLiveData<MyPensVO> myPens = new MutableLiveData<>();
    private final LiveData<String> subtitle;
    private final LiveData<List<MyPenItemVO>> pens;
    private final LiveData<Boolean> showEmpty;
    private final LiveData<Boolean> showBreedingEntry;
    private final LiveData<Long> currentFarmId;
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<MyPenItemVO> pickBatchEvent = new SingleLiveEvent<>();

    @Nullable
    private Long loadedFarmId;
    private boolean loadedOnce;

    @Inject
    public MyPensViewModel(@NonNull StockRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        subtitle = Transformations.map(myPens, v -> v == null ? ""
                : v.getFarmName() + " · 你负责 " + v.getPenCount() + " 个栏舍");
        pens = Transformations.map(myPens, v -> v == null ? Collections.emptyList() : v.getPens());
        showEmpty = Transformations.map(myPens, v -> v != null && v.getPens().isEmpty());
        showBreedingEntry = Transformations.map(myPens, v -> {
            if (v == null) {
                return false;
            }
            for (MyPenItemVO p : v.getPens()) {
                if (BREEDING_PEN_TYPES.contains(p.getPenType())) {
                    return true;
                }
            }
            return false;
        });
        currentFarmId = Transformations.distinctUntilChanged(Transformations.map(farmContext.getContext(),
                c -> c == null ? null : c.getFarmId()));
    }

    public LiveData<MyPensVO> getMyPens() {
        return myPens;
    }

    public LiveData<String> getSubtitle() {
        return subtitle;
    }

    public LiveData<List<MyPenItemVO>> getPens() {
        return pens;
    }

    public LiveData<Boolean> getShowEmpty() {
        return showEmpty;
    }

    public LiveData<Boolean> getShowBreedingEntry() {
        return showBreedingEntry;
    }

    public LiveData<Long> getCurrentFarmId() {
        return currentFarmId;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    public SingleLiveEvent<MyPenItemVO> getPickBatchEvent() {
        return pickBatchEvent;
    }

    // ===== 加载 =====

    public void onFarmChanged(@Nullable Long farmId) {
        if (loadedOnce && Objects.equals(farmId, loadedFarmId)) {
            return;
        }
        loadedOnce = true;
        loadedFarmId = farmId;
        resetContentLoaded();
        load();
    }

    /** API-L02 */
    public void load() {
        load(repository.getMyPens(), myPens::setValue);
    }

    /** 下拉刷新；登记完成（ITodoService.todoChanges）后也调用 */
    public void refresh() {
        load();
    }

    @Override
    public void retry() {
        load();
    }

    /** 没有负责栏舍时在扫码条下方显示说明，不切换整页空状态 */
    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data == null;
    }

    // ===== 点击 =====

    /** 栏舍卡头部：1 个批次进 L06，多个弹面板选择，空栏不处理 */
    public void onPenHeaderClick(@NonNull MyPenItemVO pen) {
        List<MyPenBatchVO> batches = pen.getBatches();
        if (pen.isEmpty() || batches.isEmpty()) {
            return;
        }
        if (batches.size() == 1) {
            onBatchClick(batches.get(0));
        } else {
            pickBatchEvent.setValue(pen);
        }
    }

    public void onBatchClick(@NonNull MyPenBatchVO batch) {
        onBatchPicked(batch.getBatchId());
    }

    public void onBatchPicked(long batchId) {
        navEvent.setValue(NavTarget.of(RouterPath.L06).put(RouterPath.EXTRA_BATCH_ID, batchId));
    }

    /** 任务行：按 route 打开登记页（G02 / D03 / D08 / G05 / I02），带栏舍、批次与待办 */
    public void onTaskClick(@NonNull TodoBriefVO task, @NonNull MyPenItemVO pen) {
        NavTarget t = NavTarget.ofPage(task.getRoute());
        if (t == null) {
            return;
        }
        Long batchId = task.getBatchId();
        if (batchId == null && !pen.getBatches().isEmpty()) {
            batchId = pen.getBatches().get(0).getBatchId();
        }
        if ("D03".equals(task.getRoute())) {
            t.put(RouterPath.EXTRA_PLAN_ITEM_ID, task.getTaskId());
        }
        navEvent.setValue(t.put(RouterPath.EXTRA_PEN_ID, task.getPenId() != null ? task.getPenId() : pen.getPenId())
                .put(RouterPath.EXTRA_BATCH_ID, batchId)
                .put(RouterPath.EXTRA_TODO_ID, task.getTodoId())
                .put(RouterPath.EXTRA_TASK_ID, task.getTaskId()));
    }

    /** 扫码条 → L07 */
    public void onScanClick() {
        navEvent.setValue(NavTarget.of(RouterPath.L07));
    }

    /** “待产与待出雏” → B02 员工只读版 */
    public void onBreedingEntryClick() {
        navEvent.setValue(NavTarget.of(RouterPath.B02));
    }
}
