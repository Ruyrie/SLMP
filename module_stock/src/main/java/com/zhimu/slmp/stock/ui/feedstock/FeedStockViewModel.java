package com.zhimu.slmp.stock.ui.feedstock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.vo.FeedStockItemVO;
import com.zhimu.slmp.stock.model.vo.FeedStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * M01 饲料库存（技术准备文档 2.4.1）：Single.zip 并行取 API-M01 概览与 API-M02 饲料结存（page 1，size 100）；
 * has_feed 为 false 时空状态；横幅按 banner.kind 拼文案；停用养殖场只读；当前养殖场变化时重新加载。
 */
public class FeedStockViewModel extends BaseViewModel {

    /** 最近出入库最多显示 5 条 */
    static final int RECENT_MAX = 5;

    private final StockRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<FeedStockOverviewVO> overview = new MutableLiveData<>();
    private final MutableLiveData<List<FeedStockItemVO>> feeds = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<StockFlowItemVO>> recentFlows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> bannerText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> readonly = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showOtherOut = new MutableLiveData<>(false);
    private final MutableLiveData<String> readonlyText = new MutableLiveData<>();
    private final MediatorLiveData<Long> currentFarmId = new MediatorLiveData<>();

    private final SingleLiveEvent<Long> openInbound = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openArchive = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openFlows = new SingleLiveEvent<>();
    private final SingleLiveEvent<StockFlowItemVO> openRecord = new SingleLiveEvent<>();

    @Inject
    public FeedStockViewModel(@NonNull StockRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.farmContext = farmContext;
        currentFarmId.setValue(farmContext.currentFarmId());
        currentFarmId.addSource(farmContext.getContext(), this::onFarmChanged);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        load();
    }

    /** 首次加载显示骨架屏；已有内容时为下拉刷新（失败只提示） */
    public void load() {
        load(Single.zip(repository.getFeedStockOverview(), repository.pageFeeds(1, StockRepository.FEED_PAGE_SIZE),
                Result::new), this::apply);
    }

    public void refresh() {
        load();
    }

    @Override
    public void retry() {
        load();
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        if (data instanceof Result) {
            FeedStockOverviewVO o = ((Result) data).overview;
            return o == null || !o.hasFeed;
        }
        return super.isEmptyData(data);
    }

    private void apply(@NonNull Result r) {
        FeedStockOverviewVO o = r.overview;
        overview.setValue(o);
        List<FeedStockItemVO> list = r.feeds == null || r.feeds.getList() == null ? new ArrayList<>()
                : r.feeds.getList();
        feeds.setValue(list);
        List<StockFlowItemVO> flows = o.recentFlows == null ? new ArrayList<>() : o.recentFlows;
        recentFlows.setValue(flows.size() > RECENT_MAX ? new ArrayList<>(flows.subList(0, RECENT_MAX)) : flows);
        bannerText.setValue(StockTexts.feedBanner(o.banner));
        readonly.setValue(o.farmReadonly);
        showOtherOut.setValue(o.canOtherOut && !o.farmReadonly);
        FarmContextVO ctx = farmContext.current();
        String farm = ctx == null || ctx.getName() == null ? "养殖场" : ctx.getName();
        readonlyText.setValue(o.farmReadonly ? farm + "已停用，库存数据只能查看。" : null);
    }

    private void onFarmChanged(@Nullable FarmContextVO vo) {
        Long id = vo == null ? null : vo.getFarmId();
        Long old = currentFarmId.getValue();
        currentFarmId.setValue(id);
        if (old != null && id != null && !Objects.equals(old, id)) {
            resetContentLoaded();
            load();
        }
    }

    // ===================== 操作 =====================

    /** 横幅“去入库”：预选 preselect_feed_id，空时取 feed_id */
    public void onBannerAction() {
        FeedStockOverviewVO o = overview.getValue();
        FeedStockOverviewVO.Banner b = o == null ? null : o.banner;
        Long id = b == null ? null : b.preselectFeedId != null ? b.preselectFeedId : b.feedId;
        openInbound.setValue(id);
    }

    /** FAB“入库”：不预选 */
    public void onFabClick() {
        openInbound.setValue(null);
    }

    public void onFeedClick(@NonNull FeedStockItemVO f) {
        openArchive.setValue(f.feedId);
    }

    public void onAddFeed() {
        openArchive.setValue(null);
    }

    public void onFeedFlows(@NonNull FeedStockItemVO f) {
        openFlows.setValue(f.feedId);
    }

    public void onAllFlows() {
        openFlows.setValue(null);
    }

    public void onFlowClick(@NonNull StockFlowItemVO f) {
        openRecord.setValue(f);
    }

    // ===================== 对外 =====================

    public LiveData<FeedStockOverviewVO> getOverview() {
        return overview;
    }

    public LiveData<List<FeedStockItemVO>> getFeeds() {
        return feeds;
    }

    public LiveData<List<StockFlowItemVO>> getRecentFlows() {
        return recentFlows;
    }

    public LiveData<String> getBannerText() {
        return bannerText;
    }

    public LiveData<Boolean> getReadonly() {
        return readonly;
    }

    public LiveData<Boolean> getShowOtherOut() {
        return showOtherOut;
    }

    public LiveData<String> getReadonlyText() {
        return readonlyText;
    }

    public LiveData<Long> getCurrentFarmId() {
        return currentFarmId;
    }

    public SingleLiveEvent<Long> getOpenInbound() {
        return openInbound;
    }

    public SingleLiveEvent<Long> getOpenArchive() {
        return openArchive;
    }

    public SingleLiveEvent<Long> getOpenFlows() {
        return openFlows;
    }

    public SingleLiveEvent<StockFlowItemVO> getOpenRecord() {
        return openRecord;
    }

    /** 并行加载结果 */
    static final class Result {
        final FeedStockOverviewVO overview;
        final PageVO<FeedStockItemVO> feeds;

        Result(FeedStockOverviewVO overview, PageVO<FeedStockItemVO> feeds) {
            this.overview = overview;
            this.feeds = feeds;
        }
    }
}
