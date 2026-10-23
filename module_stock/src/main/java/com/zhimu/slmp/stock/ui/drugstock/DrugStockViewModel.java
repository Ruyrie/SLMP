package com.zhimu.slmp.stock.ui.drugstock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.StockSettingRequest;
import com.zhimu.slmp.stock.model.vo.DrugArchiveItemVO;
import com.zhimu.slmp.stock.model.vo.DrugArchiveListResult;
import com.zhimu.slmp.stock.model.vo.DrugLotItemVO;
import com.zhimu.slmp.stock.model.vo.DrugStockOverviewVO;
import com.zhimu.slmp.stock.ui.common.ListRow;
import com.zhimu.slmp.stock.ui.common.PickerData;
import com.zhimu.slmp.stock.ui.common.PickerItem;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockSettingDelegate;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

/**
 * M05 兽药与疫苗库存（技术准备文档 2.4.7，含 NearDaysSheet、药品档案面板）：
 * <ul>
 *     <li>API-M24 概览（横幅、临期天数说明、有无批号）；API-M25 批号分页（{@link LotPagingSource}）</li>
 *     <li>Chip 按类别筛选、搜索（300 ms，由页面防抖）改为 ALL 不分组；筛选变化重建分页</li>
 *     <li>“药品档案”面板 API-M34 按类别分组；“临期天数设置”API-M40 / M41，保存后刷新</li>
 * </ul>
 */
public class DrugStockViewModel extends BaseListViewModel<DrugStockViewModel.Filter, ListRow<DrugLotItemVO>> {

    public static final String PICKER_ARCHIVE = "stock_m05_archive";

    private final StockRepository repository;
    private final FarmContext farmContext;
    private final StockSettingDelegate settings = new StockSettingDelegate();

    private final MutableLiveData<DrugStockOverviewVO> overview = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> groupCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<String> lowBannerText = new MutableLiveData<>();
    private final MutableLiveData<String> nearRuleText = new MutableLiveData<>(StockTexts.nearRule(null));
    private final MutableLiveData<Boolean> readonly = new MutableLiveData<>(false);
    private final MutableLiveData<String> readonlyText = new MutableLiveData<>();
    private final MutableLiveData<List<DrugArchiveItemVO>> archives = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PickerData> archivePicker = new MutableLiveData<>();
    private final MutableLiveData<Boolean> settingSaving = new MutableLiveData<>(false);
    private final MediatorLiveData<Long> currentFarmId = new MediatorLiveData<>();

    private final SingleLiveEvent<Long> openLot = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openInbound = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openArchive = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showArchivePicker = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showNearDays = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> nearDaysSaved = new SingleLiveEvent<>();

    /** 批号筛选：类别（null 为全部）与搜索词 */
    public static final class Filter {
        @Nullable
        public final String category;
        @Nullable
        public final String keyword;

        public Filter(@Nullable String category, @Nullable String keyword) {
            this.category = category;
            this.keyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Filter)) {
                return false;
            }
            Filter f = (Filter) o;
            return Objects.equals(category, f.category) && Objects.equals(keyword, f.keyword);
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, keyword);
        }
    }

    @Inject
    public DrugStockViewModel(@NonNull StockRepository repository, @NonNull FarmContext farmContext) {
        super(new Filter(null, null));
        this.repository = repository;
        this.farmContext = farmContext;
        currentFarmId.setValue(farmContext.currentFarmId());
        currentFarmId.addSource(farmContext.getContext(), this::onFarmChanged);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        load();
    }

    /** API-M24 概览（首次骨架屏，之后为刷新） */
    public void load() {
        load(repository.getDrugStockOverview(), this::applyOverview);
    }

    @Override
    public void retry() {
        load();
        super.refresh();
    }

    /** 下拉刷新：概览与批号一起刷新 */
    @Override
    public void refresh() {
        load();
        super.refresh();
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        if (data instanceof DrugStockOverviewVO) {
            return !((DrugStockOverviewVO) data).hasAnyLot;
        }
        return super.isEmptyData(data);
    }

    private void applyOverview(@NonNull DrugStockOverviewVO o) {
        overview.setValue(o);
        lowBannerText.setValue(StockTexts.drugBanner(o.lowStockBanner));
        nearRuleText.setValue(StockTexts.nearRule(o.nearDays));
        readonly.setValue(o.farmReadonly);
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
            refresh();
        }
    }

    @NonNull
    @Override
    protected PagingSource<Integer, ListRow<DrugLotItemVO>> createPagingSource(@Nullable Filter filter) {
        Filter f = filter == null ? new Filter(null, null) : filter;
        return new LotPagingSource(repository, f.category, f.keyword, groupCounts::postValue);
    }

    // ===================== 筛选与搜索 =====================

    public void setCategory(@Nullable String category) {
        Filter f = currentFilter();
        setFilter(new Filter(category, f == null ? null : f.keyword));
    }

    /** 搜索（页面已防抖 300 ms）；空串恢复分组列表 */
    public void search(@Nullable String keyword) {
        Filter f = currentFilter();
        setFilter(new Filter(f == null ? null : f.category, keyword));
    }

    public boolean isSearching() {
        Filter f = currentFilter();
        return f != null && f.keyword != null;
    }

    /** 列表为空时的文字：搜索无结果 / 当前类别无批号 */
    @NonNull
    public String emptyText() {
        Filter f = currentFilter();
        if (f != null && f.keyword != null) {
            return "没有找到“" + f.keyword + "”相关的批号";
        }
        String cat = f == null || f.category == null ? "兽药和疫苗" : StockEnums.categoryText(f.category);
        return "没有" + cat + "库存。登记入库后会显示在这里。";
    }

    // ===================== 操作 =====================

    public void onLotClick(@NonNull DrugLotItemVO lot) {
        openLot.setValue(lot.lotId);
    }

    public void onFabClick() {
        openInbound.setValue(null);
    }

    /** 横幅“去入库”：预选 preselect_drug_id，多种不足时取第一位 */
    public void onBannerAction() {
        DrugStockOverviewVO o = overview.getValue();
        DrugStockOverviewVO.LowStockBanner b = o == null ? null : o.lowStockBanner;
        Long id = null;
        if (b != null) {
            id = b.preselectDrugId != null ? b.preselectDrugId
                    : b.drugs != null && !b.drugs.isEmpty() ? Long.valueOf(b.drugs.get(0).drugId) : null;
        }
        openInbound.setValue(id);
    }

    /** 菜单“药品档案”：API-M34 后弹出面板 */
    public void openArchivePicker() {
        launch(repository.listDrugs(null, null), r -> {
            applyArchives(r);
            showArchivePicker.call();
        });
    }

    private void applyArchives(@NonNull DrugArchiveListResult r) {
        List<DrugArchiveItemVO> list = r.list == null ? new ArrayList<>() : r.list;
        archives.setValue(list);
        List<PickerItem> items = new ArrayList<>();
        for (String cat : new String[]{StockEnums.CAT_VET_DRUG, StockEnums.CAT_VACCINE, StockEnums.CAT_DISINFECTANT}) {
            boolean header = false;
            for (DrugArchiveItemVO d : list) {
                if (!cat.equals(d.category)) {
                    continue;
                }
                if (!header) {
                    items.add(PickerItem.header(StockEnums.categoryText(cat)));
                    header = true;
                }
                items.add(PickerItem.item(String.valueOf(d.drugId), StockTexts.nz(d.name))
                        .subtitle(archiveSubtitle(d))
                        .icon(StockTexts.categoryIcon(d.category), d.lowStock ? ListRowView.TONE_WARNING
                                : ListRowView.TONE_SECONDARY)
                        .tag(d.lowStock ? "库存不足" : null, StatusTagView.WARNING));
            }
        }
        archivePicker.setValue(PickerData.of(items, "还没有药品档案。新增后才能登记入库。"));
    }

    /** “可用结存 18 袋 · 下限 10 袋”或“暂无库存” */
    @NonNull
    static String archiveSubtitle(@NonNull DrugArchiveItemVO d) {
        if (!d.hasStock || d.usable == null || d.usable.value == null || d.usable.value.signum() == 0) {
            return "暂无库存";
        }
        String usable = "可用结存 " + StockQtyFormatter.packText(d.usable);
        if (d.minStock == null) {
            return usable;
        }
        String unit = StockQtyFormatter.hasPack(d.usable) ? d.usable.packUnit : StockQtyFormatter.unitText(d.usable);
        return usable + StockTexts.DOT + "下限 " + StockQtyFormatter.qty(d.minStock, unit);
    }

    /** “共 5 种 · 点按查看或修改” */
    @NonNull
    public String archiveSubtitleText() {
        List<DrugArchiveItemVO> l = archives.getValue();
        return "共 " + (l == null ? 0 : l.size()) + " 种" + StockTexts.DOT + "点按查看或修改";
    }

    public void onArchivePicked(long drugId) {
        openArchive.setValue(drugId);
    }

    /** 面板“新增药品档案” */
    public void onArchiveNew() {
        openArchive.setValue(null);
    }

    /** 当前筛选的类别（新增药品档案时作为默认类别） */
    @Nullable
    public String currentCategory() {
        Filter f = currentFilter();
        return f == null ? null : f.category;
    }

    // ===================== 临期天数 =====================

    public void openNearDays() {
        launch(repository.getStockSetting(), s -> {
            settings.apply(s);
            showNearDays.call();
        });
    }

    public void saveNearDays() {
        if (Boolean.TRUE.equals(settingSaving.getValue())) {
            return;
        }
        StockSettingRequest req = settings.buildRequest();
        if (req == null) {
            return;
        }
        settingSaving.setValue(true);
        launch(repository.saveStockSetting(req), () -> {
            settingSaving.setValue(false);
            settings.onSaved(req);
            nearDaysSaved.setValue(StockSettingDelegate.MSG_SAVED);
            refresh();
        }, this::onSettingError);
    }

    private void onSettingError(@NonNull ApiException e) {
        settingSaving.setValue(false);
        if (e.getCode() == ErrorCodes.VERSION_CONFLICT) {
            showMessage(e.getMessage());
            launch(repository.getStockSetting(), settings::apply);
            return;
        }
        if (ErrorCodes.isNoPermission(e.getCode())) {
            showMessage(e.getMessage());
            return;
        }
        handleError(e);
    }

    // ===================== 对外 =====================

    public StockSettingDelegate getSettings() {
        return settings;
    }

    public LiveData<Boolean> getSettingSaving() {
        return settingSaving;
    }

    public LiveData<DrugStockOverviewVO> getOverview() {
        return overview;
    }

    public LiveData<Map<String, Integer>> getGroupCounts() {
        return groupCounts;
    }

    public LiveData<String> getLowBannerText() {
        return lowBannerText;
    }

    public LiveData<String> getNearRuleText() {
        return nearRuleText;
    }

    public LiveData<Boolean> getReadonly() {
        return readonly;
    }

    public LiveData<String> getReadonlyText() {
        return readonlyText;
    }

    public LiveData<List<DrugArchiveItemVO>> getArchives() {
        return archives;
    }

    public LiveData<PickerData> getArchivePicker() {
        return archivePicker;
    }

    public LiveData<Long> getCurrentFarmId() {
        return currentFarmId;
    }

    public SingleLiveEvent<Long> getOpenLot() {
        return openLot;
    }

    public SingleLiveEvent<Long> getOpenInbound() {
        return openInbound;
    }

    public SingleLiveEvent<Long> getOpenArchive() {
        return openArchive;
    }

    public SingleLiveEvent<Void> getShowArchivePicker() {
        return showArchivePicker;
    }

    public SingleLiveEvent<Void> getShowNearDays() {
        return showNearDays;
    }

    public SingleLiveEvent<String> getNearDaysSaved() {
        return nearDaysSaved;
    }

    /** 页面状态是否为内容（FAB 显隐） */
    public boolean isContent() {
        UiState s = getUiState().getValue();
        return s != null && s.is(UiState.Status.CONTENT);
    }
}
