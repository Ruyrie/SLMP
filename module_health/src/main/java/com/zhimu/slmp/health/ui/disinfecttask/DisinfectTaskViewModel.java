package com.zhimu.slmp.health.ui.disinfecttask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.PagingData;
import androidx.paging.PagingDataTransforms;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.data.DisinfectRepository;
import com.zhimu.slmp.health.model.vo.DisinfectTaskPageVO;
import com.zhimu.slmp.health.model.vo.DisinfectTaskVO;
import com.zhimu.slmp.network.model.PageVO;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * D09 消毒任务：状态筛选（ALL / OVERDUE / PENDING / DONE，数量取 tab_counts）；“全部”下按 已逾期 → 待执行 → 已执行 分组
 * （insertSeparators 插入分组标题），逾期分组之后显示逾期提醒横幅（员工不返回）；点待执行任务登记，点“补录”逾期补录，
 * 点已执行任务查看记录；FAB“登记临时消毒”。
 */
public class DisinfectTaskViewModel extends BaseListViewModel<String, DisinfectTaskVO> {

    public static final String ALL = "ALL";
    public static final String OVERDUE = "OVERDUE";
    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    /** Chip 顺序（原型：全部、待执行、已执行、已逾期） */
    public static final String[] STATUSES = {ALL, PENDING, DONE, OVERDUE};

    private final DisinfectRepository repository;
    private final FarmContext farmContext;
    private final Executor executor = command -> Schedulers.computation().scheduleDirect(command);

    private final MutableLiveData<String> statusFilter = new MutableLiveData<>(ALL);
    private final MutableLiveData<Map<String, Integer>> tabCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<DisinfectTaskPageVO.OverdueBanner> overdueBanner = new MutableLiveData<>();
    private final LiveData<PagingData<DisinfectTaskRow>> tasks;
    private final MutableLiveData<Boolean> isManager = new MutableLiveData<>(false);
    private final MediatorLiveData<String> emptyText = new MediatorLiveData<>();

    private final SingleLiveEvent<DisinfectTaskVO> openRegister = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRecord = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openTemp = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openPlans = new SingleLiveEvent<>();

    /** 第 1 页到达时写入（io 线程），分组时读取 */
    private volatile String bannerText;

    @Inject
    public DisinfectTaskViewModel(@NonNull DisinfectRepository repository, @NonNull FarmContext farmContext) {
        super(ALL);
        this.repository = repository;
        this.farmContext = farmContext;
        isManager.setValue(farmContext.isOwnerOrManager());
        tasks = Transformations.map(getPagingData(), this::toRows);
        emptyText.addSource(statusFilter, v -> emptyText.setValue(computeEmptyText()));
    }

    // ===== 对外 =====

    public LiveData<String> getStatusFilter() { return statusFilter; }

    public LiveData<Map<String, Integer>> getTabCounts() { return tabCounts; }

    public LiveData<DisinfectTaskPageVO.OverdueBanner> getOverdueBanner() { return overdueBanner; }

    public LiveData<PagingData<DisinfectTaskRow>> getTasks() { return tasks; }

    public LiveData<Boolean> getIsManager() { return isManager; }

    public LiveData<String> getEmptyText() { return emptyText; }

    public SingleLiveEvent<DisinfectTaskVO> getOpenRegister() { return openRegister; }

    public SingleLiveEvent<Long> getOpenRecord() { return openRecord; }

    public SingleLiveEvent<Void> getOpenTemp() { return openTemp; }

    public SingleLiveEvent<Void> getOpenPlans() { return openPlans; }

    public boolean isStaff() { return farmContext.isStaff(); }

    // ===== 列表 =====

    @NonNull
    @Override
    protected PagingSource<Integer, DisinfectTaskVO> createPagingSource(@Nullable String filter) {
        return repository.taskPager(filter == null ? ALL : filter, null, this::onFirstPage);
    }

    private void onFirstPage(@NonNull PageVO<DisinfectTaskVO> page) {
        tabCounts.postValue(page.getTabCounts());
        DisinfectTaskPageVO.OverdueBanner banner = page instanceof DisinfectTaskPageVO
                ? ((DisinfectTaskPageVO) page).getOverdueBanner() : null;
        boolean show = banner != null && banner.isShow() && banner.getText() != null && !isStaff();
        bannerText = show ? banner.getText() : null;
        overdueBanner.postValue(show ? banner : null);
    }

    /** “全部”下插入分组标题；逾期分组后带逾期提醒横幅 */
    @NonNull
    PagingData<DisinfectTaskRow> toRows(@NonNull PagingData<DisinfectTaskVO> data) {
        boolean grouped = ALL.equals(statusFilter.getValue());
        PagingData<DisinfectTaskRow> rows = PagingDataTransforms.map(data, executor, DisinfectTaskRow::task);
        return PagingDataTransforms.insertSeparators(rows, executor, (before, after) -> separator(before, after, grouped,
                bannerText));
    }

    /** 两行之间的分隔：分组标题（可带横幅）或只有横幅的行；不需要时为 null */
    @Nullable
    static DisinfectTaskRow separator(@Nullable DisinfectTaskRow before, @Nullable DisinfectTaskRow after,
                                      boolean grouped, @Nullable String banner) {
        String prev = before == null ? null : before.getStatus();
        String next = after == null ? null : after.getStatus();
        boolean afterOverdue = OVERDUE.equals(prev) && !OVERDUE.equals(next);
        String bannerHere = afterOverdue ? banner : null;
        if (after == null) {
            return bannerHere == null ? null : DisinfectTaskRow.banner(bannerHere);
        }
        if (grouped && !Objects.equals(prev, next)) {
            return DisinfectTaskRow.header(next == null ? PENDING : next, bannerHere);
        }
        return bannerHere == null ? null : DisinfectTaskRow.banner(bannerHere);
    }

    public void selectStatus(@NonNull String status) {
        statusFilter.setValue(status);
        setFilter(status);
    }

    /** tab_counts 键：pending、done、overdue；“全部”不带数字 */
    public int countOf(@NonNull String status) {
        Map<String, Integer> m = tabCounts.getValue();
        Integer v = m == null ? null : m.get(status.toLowerCase(java.util.Locale.ROOT));
        return v == null ? 0 : v;
    }

    @NonNull
    public static String statusLabel(@NonNull String status) {
        switch (status) {
            case OVERDUE:
                return "已逾期";
            case PENDING:
                return "待执行";
            case DONE:
                return "已执行";
            default:
                return "全部";
        }
    }

    @NonNull
    String computeEmptyText() {
        String status = statusFilter.getValue();
        if (status != null && !ALL.equals(status)) {
            return "没有" + statusLabel(status) + "的任务。";
        }
        return isStaff() ? "你负责的栏舍暂时没有消毒任务。" : "暂时没有消毒任务。新建消毒计划后，系统会按周期生成任务。";
    }

    // ===== 操作 =====

    /** 点任务：待执行登记、已逾期补录（养殖户），已执行且有记录时查看 */
    public void onTaskClick(@NonNull DisinfectTaskVO t) {
        if (DONE.equals(t.getStatus())) {
            if (t.getRecordId() != null) {
                openRecord.setValue(t.getRecordId());
            }
            return;
        }
        if (t.getActions().contains(HealthExtras.ACTION_REGISTER) || t.getActions().contains(HealthExtras.ACTION_BACKFILL)) {
            openRegister.setValue(t);
        }
    }

    public void onFabClick() {
        openTemp.call();
    }

    public void onPlansClick() {
        openPlans.call();
    }
}
