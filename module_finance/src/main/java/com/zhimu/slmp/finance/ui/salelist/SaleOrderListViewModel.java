package com.zhimu.slmp.finance.ui.salelist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderPageVO;
import com.zhimu.slmp.finance.ui.common.FinanceListViewModel;
import com.zhimu.slmp.finance.util.NavTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * E14 出栏单列表：当前养殖场全部出栏单，按收款状态筛选、按批次号或收购商搜索（防抖 300 毫秒），按出栏月份分组（API-E17）。
 */
public class SaleOrderListViewModel extends FinanceListViewModel<SaleOrderListViewModel.Filter,
        SaleOrderItemVO> {

    static final long SEARCH_DEBOUNCE_MS = 300;
    static final int KEYWORD_MAX = 30;
    public static final String[] STATUSES = {FinanceConst.TYPE_ALL, FinanceConst.PAY_UNPAID,
            FinanceConst.PAY_PARTIAL, FinanceConst.PAY_PAID, FinanceConst.PAY_VOIDED};

    /** 筛选：状态 + 关键词 */
    public static final class Filter {
        @NonNull
        public final String status;
        @Nullable
        public final String keyword;

        public Filter(@NonNull String status, @Nullable String keyword) {
            this.status = status;
            this.keyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Filter)) {
                return false;
            }
            Filter f = (Filter) o;
            return status.equals(f.status) && Objects.equals(keyword, f.keyword);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, keyword);
        }
    }

    private final SaleOrderRepository repository;

    private final MutableLiveData<String> status = new MutableLiveData<>(FinanceConst.TYPE_ALL);
    /** 搜索框（双向） */
    public final MutableLiveData<String> keyword = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> searchMode = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, Integer>> tabCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Map<String, SaleOrderPageVO.MonthGroup>> monthGroups =
            new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Long> highlightId = new MutableLiveData<>();
    private final PublishSubject<String> searchInput = PublishSubject.create();
    /** 当前数据源逐页合并的月份分组（io 线程写入） */
    private final Map<String, SaleOrderPageVO.MonthGroup> groups = new LinkedHashMap<>();
    /** 数据源代数：旧数据源迟到的页不再合并 */
    private int generation;

    @Inject
    public SaleOrderListViewModel(@NonNull SaleOrderRepository repository, @NonNull FarmContext farm) {
        super(farm, new Filter(FinanceConst.TYPE_ALL, null));
        this.repository = repository;
        disposables.add(searchInput
                .debounce(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS, Schedulers.computation())
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::applyKeyword, e -> {
                }));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        denyStaff();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, SaleOrderItemVO> createPagingSource(@Nullable Filter filter) {
        Filter f = filter == null ? new Filter(FinanceConst.TYPE_ALL, null) : filter;
        final int gen;
        synchronized (groups) {
            gen = ++generation;
        }
        return repository.pagingOrders(f.status, f.keyword, p -> onPage(gen, p));
    }

    /** 每页到达（io 线程）：第 1 页重置分组并更新 Chip 数量，其余页合并月份分组 */
    void onPage(int gen, @NonNull SaleOrderPageVO p) {
        synchronized (groups) {
            if (gen != generation) {
                return;
            }
            if (p.getPage() <= 1) {
                groups.clear();
                tabCounts.postValue(p.getTabCounts() == null ? Collections.emptyMap() : p.getTabCounts());
            }
            for (SaleOrderPageVO.MonthGroup g : p.getMonthGroups()) {
                groups.put(g.getMonth(), g);
            }
            monthGroups.postValue(new LinkedHashMap<>(groups));
        }
    }

    public LiveData<String> getStatus() {
        return status;
    }

    public LiveData<Boolean> getSearchMode() {
        return searchMode;
    }

    public LiveData<Map<String, Integer>> getTabCounts() {
        return tabCounts;
    }

    public LiveData<Map<String, SaleOrderPageVO.MonthGroup>> getMonthGroups() {
        return monthGroups;
    }

    public LiveData<Long> getHighlightId() {
        return highlightId;
    }

    @NonNull
    private Filter filter() {
        Filter f = currentFilter();
        return f == null ? new Filter(FinanceConst.TYPE_ALL, null) : f;
    }

    public void setStatus(@NonNull String s) {
        status.setValue(s);
        setFilter(new Filter(s, filter().keyword));
    }

    public void enterSearch() {
        searchMode.setValue(true);
    }

    /** 退出搜索：清空关键词，列表恢复 */
    public void exitSearch() {
        searchMode.setValue(false);
        keyword.setValue("");
        setFilter(new Filter(filter().status, null));
    }

    public void search(@Nullable String text) {
        String k = text == null ? "" : text.trim();
        if (k.length() > KEYWORD_MAX) {
            k = k.substring(0, KEYWORD_MAX);
        }
        searchInput.onNext(k);
    }

    void applyKeyword(@NonNull String k) {
        if (Boolean.TRUE.equals(searchMode.getValue())) {
            setFilter(new Filter(filter().status, k));
        }
    }

    /** 搜索无结果时“在全部出栏单中搜索”：切到“全部”并保留关键词 */
    public void searchInAll() {
        setStatus(FinanceConst.TYPE_ALL);
    }

    @Nullable
    public String currentKeyword() {
        return filter().keyword;
    }

    public void openDetail(long id) {
        open(NavTarget.of(RouterPath.E07).put(RouterPath.EXTRA_SALE_ORDER_ID, id).forResult());
    }

    public void openCreate() {
        open(NavTarget.of(RouterPath.E06).forResult());
    }

    /** 从 E07 返回：刷新（卡片就地更新，不符合筛选的移除），保持滚动位置 */
    public void onDetailReturned() {
        refresh();
    }

    /** 从 E06 新增后返回：新单出现在对应月份最上方并高亮 */
    public void onCreated(long id) {
        highlightId.setValue(id);
        refresh();
    }

    public void consumeHighlight() {
        highlightId.setValue(null);
    }

    /** 状态 Chip 文字“全部 14” */
    @NonNull
    public static String chipText(@NonNull String statusCode, @Nullable Map<String, Integer> counts) {
        String name;
        switch (statusCode) {
            case FinanceConst.PAY_UNPAID:
                name = "未收款";
                break;
            case FinanceConst.PAY_PARTIAL:
                name = "部分收款";
                break;
            case FinanceConst.PAY_PAID:
                name = "已收款";
                break;
            case FinanceConst.PAY_VOIDED:
                name = "已作废";
                break;
            default:
                name = "全部";
                break;
        }
        Integer n = counts == null ? null : counts.get(statusCode);
        return n == null ? name : name + " " + n;
    }

    /** 筛选空状态 */
    @NonNull
    public static String emptyText(@NonNull String statusCode, @Nullable String keyword) {
        if (keyword != null) {
            return "没有找到与“" + keyword + "”相关的出栏单，换个批次号或收购商试试。";
        }
        switch (statusCode) {
            case FinanceConst.PAY_UNPAID:
                return "没有未收款的出栏单";
            case FinanceConst.PAY_PARTIAL:
                return "没有部分收款的出栏单";
            case FinanceConst.PAY_PAID:
                return "没有已收款的出栏单";
            case FinanceConst.PAY_VOIDED:
                return "没有已作废的出栏单";
            default:
                return "还没有出栏单。批次可以出栏时，点“登记出栏单”记录出栏和收款。";
        }
    }
}
