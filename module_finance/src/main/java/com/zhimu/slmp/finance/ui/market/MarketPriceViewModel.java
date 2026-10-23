package com.zhimu.slmp.finance.ui.market;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.data.MarketRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.MarketPriceQuery;
import com.zhimu.slmp.finance.model.vo.DailyPriceVO;
import com.zhimu.slmp.finance.model.vo.MarketFilterVO;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.ui.common.FinanceListViewModel;
import com.zhimu.slmp.finance.util.NavTarget;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.Collections;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * E11 市场行情：地区、品类、品种筛选，出栏价 / 种苗价，7 / 30 / 90 天走势与区间统计（API-E30、E31），每日价格分页（API-E32）。
 */
public class MarketPriceViewModel extends FinanceListViewModel<MarketPriceQuery, DailyPriceVO> {

    public static final int DEFAULT_RANGE = 30;
    public static final String PICK_PROVINCE = "province";
    public static final String PICK_CATEGORY = "category";
    public static final String PICK_BREED = "breed";

    private final MarketRepository repository;

    private final MutableLiveData<MarketFilterVO> filters = new MutableLiveData<>();
    /** 当前筛选（含区间）；分页的筛选对象区间固定为 0，切换区间不重建每日价格 */
    private final MutableLiveData<MarketPriceQuery> query = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isDefault = new MutableLiveData<>(true);
    private final MutableLiveData<MarketTrendVO> trend = new MutableLiveData<>();
    private final MutableLiveData<Boolean> trendLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> riseIsGood = new MutableLiveData<>(true);
    private final SingleLiveEvent<String> showPicker = new SingleLiveEvent<>();

    @Inject
    public MarketPriceViewModel(@NonNull MarketRepository repository, @NonNull FarmContext farm) {
        super(farm, null);
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        init();
    }

    public void init() {
        if (denyStaff()) {
            return;
        }
        load(repository.getFilters(), f -> {
            filters.setValue(f);
            MarketFilterVO.MarketDefaults d = f.getDefaults();
            applyQuery(new MarketPriceQuery(d.getProvinceCode(), d.getCategoryId(), d.getBreedId(),
                    d.getPriceType() == null ? FinanceConst.PRICE_SLAUGHTER : d.getPriceType(), DEFAULT_RANGE));
        });
    }

    @Override
    public void retry() {
        if (filters.getValue() == null) {
            init();
        } else {
            loadTrend();
            refresh();
        }
    }

    @NonNull
    @Override
    protected PagingSource<Integer, DailyPriceVO> createPagingSource(@Nullable MarketPriceQuery q) {
        if (q == null) {
            return new PagedSource<>((page, size) -> Single.just(new PageVO<>(Collections.emptyList(), 0, page, size,
                    false, null)));
        }
        return repository.pagingDailyPrices(q);
    }

    public LiveData<MarketPriceQuery> getQuery() {
        return query;
    }

    public LiveData<MarketFilterVO> getFilters() {
        return filters;
    }

    public LiveData<Boolean> getIsDefault() {
        return isDefault;
    }

    public LiveData<MarketTrendVO> getTrend() {
        return trend;
    }

    public LiveData<Boolean> getTrendLoading() {
        return trendLoading;
    }

    public LiveData<Boolean> getRiseIsGood() {
        return riseIsGood;
    }

    public SingleLiveEvent<String> getShowPicker() {
        return showPicker;
    }

    private void applyQuery(@NonNull MarketPriceQuery q) {
        query.setValue(q);
        setFilter(q.withRange(0));
        MarketFilterVO f = filters.getValue();
        MarketFilterVO.MarketDefaults d = f == null ? null : f.getDefaults();
        isDefault.setValue(d == null || q.sameFilter(new MarketPriceQuery(d.getProvinceCode(), d.getCategoryId(),
                d.getBreedId(), d.getPriceType() == null ? FinanceConst.PRICE_SLAUGHTER : d.getPriceType(),
                q.getRangeDays())));
        riseIsGood.setValue(!FinanceConst.PRICE_SEEDLING.equals(q.getPriceType()));
        loadTrend();
    }

    void loadTrend() {
        MarketPriceQuery q = query.getValue();
        if (q == null) {
            return;
        }
        trendLoading.setValue(true);
        launch(repository.getTrend(q), t -> {
            trendLoading.setValue(false);
            if (Objects.equals(q, query.getValue())) {
                trend.setValue(t);
            }
        }, e -> {
            trendLoading.setValue(false);
            handleError(e);
        });
    }

    public void openPicker(@NonNull String which) {
        if (filters.getValue() != null) {
            showPicker.setValue(which);
        }
    }

    public void selectProvince(@NonNull String code) {
        MarketPriceQuery q = query.getValue();
        if (q != null && !code.equals(q.getProvinceCode())) {
            applyQuery(q.withProvince(code));
        }
    }

    /** 换品类时品种归 0（全部品种） */
    public void selectCategory(long id) {
        MarketPriceQuery q = query.getValue();
        if (q != null && id != q.getCategoryId()) {
            applyQuery(q.withCategory(id));
        }
    }

    public void selectBreed(long id) {
        MarketPriceQuery q = query.getValue();
        if (q != null && id != q.getBreedId()) {
            applyQuery(q.withBreed(id));
        }
    }

    public void setPriceType(@NonNull String type) {
        MarketPriceQuery q = query.getValue();
        if (q != null && !type.equals(q.getPriceType())) {
            applyQuery(q.withPriceType(type));
        }
    }

    public void setRange(int days) {
        MarketPriceQuery q = query.getValue();
        if (q != null && days != q.getRangeDays()) {
            // 区间只影响走势图，每日价格不重建
            applyQuery(q.withRange(days));
        }
    }

    public void resetDefault() {
        MarketFilterVO f = filters.getValue();
        MarketPriceQuery q = query.getValue();
        if (f == null || q == null) {
            return;
        }
        MarketFilterVO.MarketDefaults d = f.getDefaults();
        applyQuery(new MarketPriceQuery(d.getProvinceCode(), d.getCategoryId(), d.getBreedId(),
                d.getPriceType() == null ? FinanceConst.PRICE_SLAUGHTER : d.getPriceType(), q.getRangeDays()));
    }

    public void openAdvice() {
        open(NavTarget.of(RouterPath.E12));
    }

    // ===== 显示文字 =====

    @NonNull
    public String provinceName() {
        MarketFilterVO f = filters.getValue();
        MarketPriceQuery q = query.getValue();
        if (f != null && q != null) {
            for (MarketFilterVO.Province p : f.getProvinces()) {
                if (p.getCode().equals(q.getProvinceCode())) {
                    return p.getName();
                }
            }
        }
        return "";
    }

    @NonNull
    public String categoryName() {
        MarketFilterVO f = filters.getValue();
        MarketPriceQuery q = query.getValue();
        if (f != null && q != null) {
            for (MarketFilterVO.CategoryOption c : f.getCategories()) {
                if (c.getId() == q.getCategoryId()) {
                    return c.getName();
                }
            }
        }
        return "";
    }

    @NonNull
    public String breedName() {
        MarketFilterVO f = filters.getValue();
        MarketPriceQuery q = query.getValue();
        if (f != null && q != null && q.getBreedId() != 0) {
            for (MarketFilterVO.BreedOption b : f.getBreeds()) {
                if (b.getBreedId() == q.getBreedId()) {
                    return b.getName();
                }
            }
        }
        return "全部品种";
    }

    /** 价格卡标题“育肥猪 · 出栏价 · 内蒙古” */
    @NonNull
    public String cardTitle() {
        MarketPriceQuery q = query.getValue();
        boolean seedling = q != null && FinanceConst.PRICE_SEEDLING.equals(q.getPriceType());
        String breed = q != null && q.getBreedId() != 0 ? breedName() : categoryName();
        return breed + " · " + (seedling ? "种苗价" : "出栏价") + " · " + provinceName();
    }

    /** 空状态“内蒙古暂时没有育肥猪出栏价，可以切换地区或品类查看。” */
    @NonNull
    public String emptyText() {
        MarketPriceQuery q = query.getValue();
        boolean seedling = q != null && FinanceConst.PRICE_SEEDLING.equals(q.getPriceType());
        return provinceName() + "暂时没有" + categoryName() + (seedling ? "种苗价" : "出栏价") + "，可以切换地区或品类查看。";
    }
}
