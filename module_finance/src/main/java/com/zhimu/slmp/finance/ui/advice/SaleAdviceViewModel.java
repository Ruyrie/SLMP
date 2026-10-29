package com.zhimu.slmp.finance.ui.advice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.DirectAdviceRepository;
import com.zhimu.slmp.finance.data.MarketRepository;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.MarketPriceQuery;
import com.zhimu.slmp.finance.model.request.SaleAdviceRequest;
import com.zhimu.slmp.finance.model.vo.MarketFilterVO;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.ui.common.FinanceListViewModel;
import com.zhimu.slmp.finance.util.NavTarget;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * E12 AI 出栏建议：选择在栏批次，最新结论、建议时间段、理由与风险；重新生成；历史建议（API-E33–E36，GENERATING 时每 2 秒轮询）。
 * 测试直连开启时（debug 包且配置了 Key）由 {@link DirectAdviceRepository} 直连千问生成，历史建议仍取 API-E35。
 */
public class SaleAdviceViewModel extends FinanceListViewModel<String, SaleAdviceVO> {

    static final String HISTORY_ALL = "ALL";

    private final SaleOrderRepository saleRepository;
    private final MarketRepository marketRepository;
    private final DirectAdviceRepository direct;

    private final MutableLiveData<List<SaleBatchOptionVO>> batchOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> selectedBatchId = new MutableLiveData<>();
    private final MutableLiveData<SaleAdviceVO> advice = new MutableLiveData<>();
    private final MutableLiveData<Boolean> generating = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> failed = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> noBatches = new MutableLiveData<>(false);
    private final SingleLiveEvent<SaleAdviceVO> showHistorySheet = new SingleLiveEvent<>();
    @Nullable
    private Disposable running;
    private long presetBatchId;
    /** 正在“重新生成”（完成后提示） */
    private boolean regenerating;

    @Inject
    public SaleAdviceViewModel(@NonNull SaleOrderRepository saleRepository, @NonNull MarketRepository marketRepository,
                               @NonNull DirectAdviceRepository direct, @NonNull FarmContext farm) {
        super(farm, HISTORY_ALL);
        this.saleRepository = saleRepository;
        this.marketRepository = marketRepository;
        this.direct = direct;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long b = handle.get(RouterPath.EXTRA_BATCH_ID);
        presetBatchId = b == null ? 0 : b;
        init();
    }

    public void init() {
        if (denyStaff()) {
            return;
        }
        load(saleRepository.getBatchOptions(), list -> {
            batchOptions.setValue(list);
            noBatches.setValue(list.isEmpty());
            if (list.isEmpty()) {
                return;
            }
            long target = list.get(0).getBatchId();
            for (SaleBatchOptionVO o : list) {
                if (o.getBatchId() == presetBatchId) {
                    target = presetBatchId;
                }
            }
            selectBatch(target);
        });
    }

    /** 没有在养批次时整页仍显示（AI 卡位置显示空状态），不切换为 EMPTY */
    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data == null;
    }

    @Override
    public void retry() {
        init();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, SaleAdviceVO> createPagingSource(@Nullable String filter) {
        return marketRepository.pagingAdviceHistory(null);
    }

    public LiveData<List<SaleBatchOptionVO>> getBatchOptions() {
        return batchOptions;
    }

    public LiveData<Long> getSelectedBatchId() {
        return selectedBatchId;
    }

    public LiveData<SaleAdviceVO> getAdvice() {
        return advice;
    }

    public LiveData<Boolean> getGenerating() {
        return generating;
    }

    public LiveData<Boolean> getFailed() {
        return failed;
    }

    public LiveData<Boolean> getNoBatches() {
        return noBatches;
    }

    public SingleLiveEvent<SaleAdviceVO> getShowHistorySheet() {
        return showHistorySheet;
    }

    public boolean isDirect() {
        return direct.isEnabled();
    }

    @Nullable
    public SaleBatchOptionVO selectedBatch() {
        Long id = selectedBatchId.getValue();
        for (SaleBatchOptionVO o : batchOptions.getValue()) {
            if (id != null && o.getBatchId() == id) {
                return o;
            }
        }
        return null;
    }

    /** “杜长大育肥猪 · 96 日龄 · 存栏 200 头” */
    @NonNull
    public static String batchHelper(@NonNull SaleBatchOptionVO b) {
        return (b.getBreedName() == null ? "" : b.getBreedName()) + b.getCategoryName() + " · " + b.getDayAge()
                + " 日龄 · 存栏 " + b.getCurrentCount() + " " + b.getCountUnitText();
    }

    // ===== 建议 =====

    /** 切换批次：显示该批次最近一次建议；当天还没有建议时自动生成 */
    public void selectBatch(long batchId) {
        if (Objects.equals(selectedBatchId.getValue(), batchId) && advice.getValue() != null) {
            return;
        }
        cancelRunning();
        selectedBatchId.setValue(batchId);
        advice.setValue(null);
        failed.setValue(false);
        regenerating = false;
        if (direct.isEnabled()) {
            SaleAdviceVO cached = direct.cached(batchId);
            if (cached != null) {
                show(cached);
            } else {
                generateDirect();
            }
            return;
        }
        generating.setValue(true);
        running = launch(marketRepository.getLatestAdvice(batchId), this::onAdvice, this::onAdviceError);
    }

    /** 重新生成（生成中不可点） */
    public void regenerate() {
        Long id = selectedBatchId.getValue();
        if (id == null || Boolean.TRUE.equals(generating.getValue())) {
            return;
        }
        cancelRunning();
        regenerating = true;
        failed.setValue(false);
        if (direct.isEnabled()) {
            generateDirect();
            return;
        }
        generating.setValue(true);
        running = launch(marketRepository.regenerateAdvice(new SaleAdviceRequest(id)), this::onAdvice,
                this::onAdviceError);
    }

    private void onAdvice(@NonNull SaleAdviceVO a) {
        if (FinanceConst.GEN_GENERATING.equals(a.getStatus())) {
            advice.setValue(a);
            generating.setValue(true);
            poll(a.getId());
        } else {
            show(a);
        }
    }

    private void onAdviceError(@NonNull ApiException e) {
        generating.setValue(false);
        regenerating = false;
        if (advice.getValue() == null) {
            failed.setValue(true);
        }
        handleError(e);
    }

    /** API-E36 轮询；150 秒后仍在生成按失败处理 */
    private void poll(long id) {
        cancelRunning();
        final SaleAdviceVO[] last = new SaleAdviceVO[1];
        running = marketRepository.pollAdvice(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(a -> {
                    last[0] = a;
                    if (!FinanceConst.GEN_GENERATING.equals(a.getStatus())) {
                        show(a);
                    }
                }, e -> {
                    generating.setValue(false);
                    failed.setValue(true);
                }, () -> {
                    if (last[0] == null || FinanceConst.GEN_GENERATING.equals(last[0].getStatus())) {
                        generating.setValue(false);
                        failed.setValue(true);
                    }
                });
        disposables.add(running);
    }

    private void show(@NonNull SaleAdviceVO a) {
        generating.setValue(false);
        boolean fail = FinanceConst.GEN_FAILED.equals(a.getStatus());
        failed.setValue(fail);
        advice.setValue(a);
        if (regenerating && !fail) {
            showMessage("已重新生成出栏建议");
        }
        regenerating = false;
    }

    /** 测试直连：取本省同品类近 30 天出栏价（失败时不带行情），再由直连仓库生成 */
    private void generateDirect() {
        SaleBatchOptionVO b = selectedBatch();
        if (b == null) {
            return;
        }
        generating.setValue(true);
        Single<Optional<MarketTrendVO>> trend = marketRepository.getFilters()
                .flatMap(f -> marketRepository.getTrend(trendQuery(f, b)))
                .map(Optional::of)
                .onErrorReturnItem(Optional.empty());
        running = launch(trend.flatMap(t -> direct.generate(b, t.orElse(null))), a -> {
            if (Objects.equals(selectedBatchId.getValue(), b.getBatchId())) {
                show(a);
            }
        }, this::onAdviceError);
    }

    @NonNull
    static MarketPriceQuery trendQuery(@NonNull MarketFilterVO f, @NonNull SaleBatchOptionVO b) {
        return new MarketPriceQuery(f.getDefaults().getProvinceCode(), b.getCategoryId(), 0,
                FinanceConst.PRICE_SLAUGHTER, 30);
    }

    private void cancelRunning() {
        if (running != null) {
            running.dispose();
            running = null;
        }
    }

    // ===== 历史与登记 =====

    public void openHistory(long id) {
        launch(marketRepository.getAdvice(id), showHistorySheet::setValue);
    }

    /** “登记出栏单”：进入 E06 并带入该批次 */
    public void openRegister() {
        Long id = selectedBatchId.getValue();
        if (id != null) {
            open(NavTarget.of(RouterPath.E06).put(RouterPath.EXTRA_BATCH_ID, id));
        }
    }

    @Override
    protected void onCleared() {
        cancelRunning();
        super.onCleared();
    }
}
