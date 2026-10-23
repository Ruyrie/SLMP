package com.zhimu.slmp.finance.ui.market;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivityMarketBinding;
import com.zhimu.slmp.finance.databinding.FinanceViewMarketHeaderBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.MarketPriceQuery;
import com.zhimu.slmp.finance.model.vo.MarketFilterVO;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.finance.ui.common.SingleViewAdapter;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * E11 市场行情（图 10-15）。
 */
public class MarketPriceActivity extends FinancePageActivity {

    static final String REQ_PICK = "finance_e11_pick";
    static final String EXTRA_WHICH = "which";

    private FinanceActivityMarketBinding binding;
    private FinanceViewMarketHeaderBinding header;
    private MarketPriceViewModel vm;
    private DailyPriceAdapter daily;
    private boolean renderingGroups;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivityMarketBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), MarketPriceViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e11_title), binding.financeList);
        bindState(binding.financeState, vm);
        observeNav(vm.getOpenPage());

        binding.financeList.setLayoutManager(new LinearLayoutManager(this));
        header = FinanceViewMarketHeaderBinding.inflate(getLayoutInflater(), binding.financeList, false);
        daily = new DailyPriceAdapter();
        binding.financeList.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()),
                daily.withLoadStateFooter(new FooterLoadStateAdapter(daily::retry, true))));
        vm.getPagingData().observe(this, d -> daily.submitData(getLifecycle(), d));
        daily.addLoadStateListener(s -> {
            if (!(s.getRefresh() instanceof androidx.paging.LoadState.Loading)) {
                binding.financeRefresh.setRefreshing(false);
            }
            return kotlin.Unit.INSTANCE;
        });
        binding.financeRefresh.setOnRefreshListener(vm::retry);
        binding.financeList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 4) {
                    binding.financeFab.shrink();
                } else if (dy < -4) {
                    binding.financeFab.extend();
                }
            }
        });
        ClickThrottle.setOnClickListener(binding.financeFab, v -> vm.openAdvice());

        header.financeChipProvince.setOnClickListener(v -> vm.openPicker(MarketPriceViewModel.PICK_PROVINCE));
        header.financeChipCategory.setOnClickListener(v -> vm.openPicker(MarketPriceViewModel.PICK_CATEGORY));
        header.financeChipBreed.setOnClickListener(v -> vm.openPicker(MarketPriceViewModel.PICK_BREED));
        header.financeReset.setOnClickListener(v -> vm.resetDefault());
        header.financeTypeGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked && !renderingGroups) {
                vm.setPriceType(id == R.id.finance_type_seedling ? FinanceConst.PRICE_SEEDLING
                        : FinanceConst.PRICE_SLAUGHTER);
            }
        });
        header.financeRangeGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked && !renderingGroups) {
                vm.setRange(id == R.id.finance_range_7 ? 7 : id == R.id.finance_range_90 ? 90 : 30);
            }
        });
        header.financeStatAvg.financeColLabel.setText(R.string.finance_e11_avg);

        vm.getShowPicker().observe(this, this::showPicker);
        getSupportFragmentManager().setFragmentResultListener(REQ_PICK, this, (k, r) -> {
            Bundle extras = OptionSheetDialogFragment.getExtras(r);
            String which = extras == null ? null : extras.getString(EXTRA_WHICH);
            String value = OptionSheetDialogFragment.getValue(r);
            if (MarketPriceViewModel.PICK_PROVINCE.equals(which)) {
                vm.selectProvince(value);
            } else if (MarketPriceViewModel.PICK_CATEGORY.equals(which)) {
                vm.selectCategory(Long.parseLong(value));
            } else if (MarketPriceViewModel.PICK_BREED.equals(which)) {
                vm.selectBreed(Long.parseLong(value));
            }
        });
        vm.getQuery().observe(this, this::renderQuery);
        vm.getIsDefault().observe(this, d -> renderDefault());
        vm.getFilters().observe(this, f -> renderDefault());
        vm.getTrend().observe(this, this::renderTrend);
        vm.getRiseIsGood().observe(this, good -> {
            daily.setRiseIsGood(Boolean.TRUE.equals(good));
            renderTrend(vm.getTrend().getValue());
        });
    }

    private void showPicker(@NonNull String which) {
        MarketFilterVO f = vm.getFilters().getValue();
        MarketPriceQuery q = vm.getQuery().getValue();
        if (f == null || q == null) {
            return;
        }
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        String selected;
        String title;
        if (MarketPriceViewModel.PICK_PROVINCE.equals(which)) {
            for (MarketFilterVO.Province p : f.getProvinces()) {
                options.add(new OptionSheetDialogFragment.Option(p.getCode(), p.getName()));
            }
            selected = q.getProvinceCode();
            title = getString(R.string.finance_e11_pick_province);
        } else if (MarketPriceViewModel.PICK_CATEGORY.equals(which)) {
            for (MarketFilterVO.CategoryOption c : f.getCategories()) {
                options.add(new OptionSheetDialogFragment.Option(String.valueOf(c.getId()), c.getName()));
            }
            selected = String.valueOf(q.getCategoryId());
            title = getString(R.string.finance_e11_pick_category);
        } else {
            options.add(new OptionSheetDialogFragment.Option("0", "全部品种"));
            for (MarketFilterVO.BreedOption b : f.getBreeds()) {
                if (b.getCategoryId() == q.getCategoryId() && b.getBreedId() != 0) {
                    options.add(new OptionSheetDialogFragment.Option(String.valueOf(b.getBreedId()), b.getName()));
                }
            }
            selected = String.valueOf(q.getBreedId());
            title = getString(R.string.finance_e11_pick_breed);
        }
        Bundle extras = new Bundle();
        extras.putString(EXTRA_WHICH, which);
        OptionSheetDialogFragment.builder(REQ_PICK).title(title).options(options).selected(selected).extras(extras)
                .build().showSafely(getSupportFragmentManager());
    }

    private void renderQuery(@Nullable MarketPriceQuery q) {
        if (q == null) {
            return;
        }
        renderingGroups = true;
        header.financeChipProvince.setText(vm.provinceName());
        header.financeChipCategory.setText(vm.categoryName());
        header.financeChipBreed.setText(vm.breedName());
        header.financeTypeGroup.check(FinanceConst.PRICE_SEEDLING.equals(q.getPriceType()) ? R.id.finance_type_seedling
                : R.id.finance_type_slaughter);
        header.financeRangeGroup.check(q.getRangeDays() == 7 ? R.id.finance_range_7 : q.getRangeDays() == 90
                ? R.id.finance_range_90 : R.id.finance_range_30);
        renderingGroups = false;
        header.financeCardTitle.setText(vm.cardTitle());
        renderDefault();
    }

    private void renderDefault() {
        boolean def = Boolean.TRUE.equals(vm.getIsDefault().getValue());
        MarketFilterVO f = vm.getFilters().getValue();
        header.financeDefaultDesc.setVisibility(def ? View.VISIBLE : View.GONE);
        header.financeReset.setVisibility(def ? View.GONE : View.VISIBLE);
        header.financeDefaultDesc.setText(f == null ? null : f.getDefaultDesc());
    }

    private void renderTrend(@Nullable MarketTrendVO t) {
        boolean empty = t == null || t.isEmpty() || t.getLatest() == null;
        header.financePriceBox.setVisibility(empty ? View.GONE : View.VISIBLE);
        header.financePriceEmpty.setVisibility(empty && t != null ? View.VISIBLE : View.GONE);
        header.financePriceEmpty.setText(vm.emptyText());
        MarketPriceQuery q = vm.getQuery().getValue();
        header.financeDailyTitle.setTitle(getString(R.string.finance_e11_daily,
                t == null || t.getLatest() == null ? "元/千克" : t.getLatest().getPriceUnitText()));
        if (empty) {
            header.financeUpdated.setText(null);
            header.financeChart.clear();
            return;
        }
        MarketTrendVO.LatestPrice latest = t.getLatest();
        header.financeUpdated.setText(getString(R.string.finance_e11_updated, FinanceFormat.monthDay(latest.getPriceDate())));
        header.financeLatest.setText(FinanceViews.amountWithUnit(header.financeLatest, FinanceFormat.price(latest.getPrice()),
                latest.getPriceUnitText(), .36f));
        MarketTrendVO.PriceChange c = t.getChange();
        boolean riseGood = Boolean.TRUE.equals(vm.getRiseIsGood().getValue());
        if (c == null) {
            header.financeChange.setText(null);
            header.financeCompare.setText(null);
        } else {
            int dir = FinanceConst.UP.equals(c.getDirection()) ? 1 : FinanceConst.DOWN.equals(c.getDirection()) ? -1 : 0;
            FinanceViews.setTrend(header.financeChange, FinanceFormat.priceChange(c.getChangeRate(), c.getDirection()),
                    FinanceFormat.priceTone(c.getChangeRate(), c.getDirection(), riseGood), dir);
            header.financeCompare.setText(c.getCompareDate() == null ? null : getString(R.string.finance_e11_compare,
                    FinanceFormat.monthDay(c.getCompareDate()), FinanceFormat.price(c.getComparePrice())));
        }
        MarketTrendVO.PriceStats s = t.getStats();
        header.financeChart.setSeries(t.getSeries(), s == null ? null : s.getAvgPrice());
        if (s != null) {
            header.financeStatMax.financeColLabel.setText(getString(R.string.finance_e11_max,
                    FinanceFormat.monthDay(s.getMaxDate())));
            header.financeStatMax.financeColValue.setText(FinanceFormat.price(s.getMaxPrice()) + " 元");
            header.financeStatMin.financeColLabel.setText(getString(R.string.finance_e11_min,
                    FinanceFormat.monthDay(s.getMinDate())));
            header.financeStatMin.financeColValue.setText(FinanceFormat.price(s.getMinPrice()) + " 元");
            header.financeStatAvg.financeColValue.setText(FinanceFormat.price(s.getAvgPrice()) + " 元");
        }
        if (q != null && DateFormatter.todayString().equals(latest.getPriceDate())) {
            header.financeUpdated.setText(getString(R.string.finance_e11_updated,
                    FinanceFormat.monthDay(latest.getPriceDate())));
        }
    }
}
