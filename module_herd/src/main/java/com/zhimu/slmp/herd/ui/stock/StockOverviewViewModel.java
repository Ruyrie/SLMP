package com.zhimu.slmp.herd.ui.stock;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.PagingData;
import androidx.paging.rxjava3.PagingRx;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.data.StockRepository;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.CategoryStockVO;
import com.zhimu.slmp.herd.model.vo.PenStockItemVO;
import com.zhimu.slmp.herd.model.vo.StockSummaryVO;
import com.zhimu.slmp.herd.ui.common.NavTarget;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import javax.inject.Inject;

/**
 * L03 存栏总览：栏舍 / 品类 / 批次三视图、汇总卡、搜索与排序。
 */
public class StockOverviewViewModel extends BaseViewModel {

    public static final String SORT_CODE = "CODE";
    public static final String SORT_STOCK = "STOCK";
    public static final String SORT_OCCUPANCY = "OCCUPANCY";
    public static final String SORT_ENTRY_DATE = "ENTRY_DATE";
    public static final String SORT_DAY_AGE = "DAY_AGE";
    /** 关键词最多 20 字 */
    public static final int KEYWORD_MAX = 20;

    private final StockRepository stockRepository;
    private final BatchRepository batchRepository;

    private final MutableLiveData<String> viewType = new MutableLiveData<>(HerdExtras.VIEW_PEN);
    private final MutableLiveData<String> penSort = new MutableLiveData<>(SORT_CODE);
    private final MutableLiveData<String> batchSort = new MutableLiveData<>(SORT_ENTRY_DATE);
    private final MutableLiveData<String> keyword = new MutableLiveData<>("");
    private final MutableLiveData<StockSummaryVO> summary = new MutableLiveData<>();
    private final MutableLiveData<PagingData<PenStockItemVO>> penPaging = new MutableLiveData<>();
    private final MutableLiveData<List<CategoryStockVO>> categories = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PagingData<BatchListItemVO>> batchPaging = new MutableLiveData<>();
    private final MutableLiveData<Integer> listTotal = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> hasPen = new MutableLiveData<>(true);
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();

    private final Map<String, Integer> totals = new HashMap<>();
    private final PublishSubject<String> keywordInput = PublishSubject.create();
    @Nullable
    private Disposable penDisposable;
    @Nullable
    private Disposable batchDisposable;
    @Nullable
    private Disposable categoryDisposable;
    /** 各视图数据对应的关键词（切换视图时关键词变过则重建） */
    private final Map<String, String> builtKeyword = new HashMap<>();

    @Inject
    public StockOverviewViewModel(@NonNull StockRepository stockRepository, @NonNull BatchRepository batchRepository) {
        this.stockRepository = stockRepository;
        this.batchRepository = batchRepository;
        disposables.add(keywordInput
                .debounce(300, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(k -> {
                    keyword.setValue(k);
                    rebuildCurrent();
                }, t -> {
                }));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        String type = handle.get(RouterPath.EXTRA_VIEW_TYPE);
        if (HerdExtras.VIEW_BATCH.equals(type) || HerdExtras.VIEW_CATEGORY.equals(type)) {
            viewType.setValue(type);
        }
        loadSummary();
        rebuildCurrent();
    }

    // ===== 对外 LiveData =====

    public LiveData<String> getViewType() {
        return viewType;
    }

    public LiveData<String> getPenSort() {
        return penSort;
    }

    public LiveData<String> getBatchSort() {
        return batchSort;
    }

    /** 搜索框（双向） */
    public MutableLiveData<String> getKeyword() {
        return keyword;
    }

    public LiveData<StockSummaryVO> getSummary() {
        return summary;
    }

    public LiveData<PagingData<PenStockItemVO>> getPenPaging() {
        return penPaging;
    }

    public LiveData<List<CategoryStockVO>> getCategories() {
        return categories;
    }

    public LiveData<PagingData<BatchListItemVO>> getBatchPaging() {
        return batchPaging;
    }

    public LiveData<Integer> getListTotal() {
        return listTotal;
    }

    public LiveData<Boolean> getHasPen() {
        return hasPen;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    @NonNull
    public String currentViewType() {
        String v = viewType.getValue();
        return v == null ? HerdExtras.VIEW_PEN : v;
    }

    @NonNull
    private String currentKeyword() {
        String k = keyword.getValue();
        return k == null ? "" : k.trim();
    }

    // ===== 操作 =====

    /** 切换视图：不清空搜索词和排序；关键词变化过的视图重新加载 */
    public void setViewType(@NonNull String type) {
        if (type.equals(viewType.getValue())) {
            return;
        }
        viewType.setValue(type);
        Integer t = totals.get(type);
        listTotal.setValue(t == null ? 0 : t);
        if (!currentKeyword().equals(builtKeyword.get(type))) {
            rebuildCurrent();
        }
    }

    /** 排序：栏舍视图 CODE / STOCK / OCCUPANCY，批次视图 ENTRY_DATE / DAY_AGE；从第 1 页重新加载 */
    public void setSort(@NonNull String sort) {
        if (HerdExtras.VIEW_BATCH.equals(currentViewType())) {
            if (!sort.equals(batchSort.getValue())) {
                batchSort.setValue(sort);
                buildBatchPaging();
            }
        } else if (HerdExtras.VIEW_PEN.equals(currentViewType())) {
            if (!sort.equals(penSort.getValue())) {
                penSort.setValue(sort);
                buildPenPaging();
            }
        }
    }

    /** 搜索框输入：停止输入 300 毫秒后按当前视图过滤 */
    public void onKeywordChanged(@Nullable String text) {
        String k = text == null ? "" : text.trim();
        if (k.length() > KEYWORD_MAX) {
            k = k.substring(0, KEYWORD_MAX);
        }
        keywordInput.onNext(k);
    }

    /** API-L03 汇总卡 */
    public void loadSummary() {
        load(stockRepository.getStockSummary(), summary::setValue);
    }

    @Override
    public void retry() {
        resetContentLoaded();
        loadSummary();
        rebuildCurrent();
    }

    /** 下拉刷新：汇总与当前视图 */
    public void refresh() {
        loadSummary();
        rebuildCurrent();
    }

    private void rebuildCurrent() {
        String type = currentViewType();
        if (HerdExtras.VIEW_CATEGORY.equals(type)) {
            loadCategories();
        } else if (HerdExtras.VIEW_BATCH.equals(type)) {
            buildBatchPaging();
        } else {
            buildPenPaging();
        }
    }

    /** API-L04 栏舍视图分页 */
    public void buildPenPaging() {
        if (penDisposable != null) {
            penDisposable.dispose();
        }
        String k = currentKeyword();
        builtKeyword.put(HerdExtras.VIEW_PEN, k);
        penDisposable = PagingRx.cachedIn(stockRepository.pagePenStocks(penSort.getValue(), k, first -> {
                    setTotal(HerdExtras.VIEW_PEN, first.getTotal());
                    if (k.isEmpty()) {
                        hasPen.postValue(first.getTotal() > 0);
                    }
                }), ViewModelKt.getViewModelScope(this))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(penPaging::setValue, t -> {
                });
        disposables.add(penDisposable);
    }

    /** API-L06 批次视图分页（只含在养批次） */
    public void buildBatchPaging() {
        if (batchDisposable != null) {
            batchDisposable.dispose();
        }
        String k = currentKeyword();
        builtKeyword.put(HerdExtras.VIEW_BATCH, k);
        batchDisposable = PagingRx.cachedIn(batchRepository.pageBatches(BatchRepository.STATUS_ACTIVE, k,
                        batchSort.getValue(), first -> setTotal(HerdExtras.VIEW_BATCH, first.getTotal())),
                        ViewModelKt.getViewModelScope(this))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(batchPaging::setValue, t -> {
                });
        disposables.add(batchDisposable);
    }

    /** API-L05 品类视图 */
    public void loadCategories() {
        if (categoryDisposable != null) {
            categoryDisposable.dispose();
        }
        String k = currentKeyword();
        builtKeyword.put(HerdExtras.VIEW_CATEGORY, k);
        categoryDisposable = launch(stockRepository.getCategoryStocks(k), list -> {
            categories.setValue(list);
            setTotal(HerdExtras.VIEW_CATEGORY, list.size());
        });
    }

    private void setTotal(@NonNull String type, int total) {
        synchronized (totals) {
            totals.put(type, total);
        }
        if (type.equals(viewType.getValue())) {
            listTotal.postValue(total);
        }
    }

    /** 栏舍行：种畜栏舍且无批次 → B01，其余 → L04 */
    public void onPenClick(@NonNull PenStockItemVO item) {
        Integer count = item.getBatchCount();
        if (item.isBreedingPen() && (count == null || count == 0)) {
            navEvent.setValue(NavTarget.of(RouterPath.B01).put(RouterPath.EXTRA_PEN_ID, item.getPenId()));
            return;
        }
        navEvent.setValue(NavTarget.of(RouterPath.L04).put(RouterPath.EXTRA_PEN_ID, item.getPenId()).forResult());
    }

    /** 批次行 → L06 */
    public void onBatchClick(@NonNull BatchListItemVO item) {
        navEvent.setValue(NavTarget.of(RouterPath.L06).put(RouterPath.EXTRA_BATCH_ID, item.getBatchId()).forResult());
    }

    /** 顶部栏 add → L05 新增 */
    public void onAddClick() {
        navEvent.setValue(NavTarget.of(RouterPath.L05).forResult());
    }

    /** FAB“批次入栏” → L08 */
    public void onFabClick() {
        navEvent.setValue(NavTarget.of(RouterPath.L08).forResult());
    }

    /** Snackbar“去分配” → F07 成员管理 */
    public void onAssignClick() {
        navEvent.setValue(NavTarget.of(RouterPath.F07));
    }

    /**
     * L05、L08（经 L09）、L04、L06 返回：刷新汇总与当前视图。成功提示由子页面关闭时经 GlobalEvents 显示
     * （“已新增 6 号育肥舍”+“去分配”）。
     */
    public void onChildResult(int code, @Nullable String text) {
        if (code == Activity.RESULT_OK) {
            loadSummary();
            rebuildCurrent();
        }
    }
}
