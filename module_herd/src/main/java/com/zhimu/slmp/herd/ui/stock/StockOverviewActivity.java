package com.zhimu.slmp.herd.ui.stock;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityStockOverviewBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.herd.ui.common.HerdNav;
import com.zhimu.slmp.herd.ui.common.MenuItemSpec;
import com.zhimu.slmp.herd.ui.penedit.PenEditViewModel;

import java.util.Arrays;
import java.util.List;

import kotlin.Unit;

/**
 * L03 存栏总览（所有者、场长）：栏舍 / 品类 / 批次三视图，搜索、排序、批次入栏。
 */
public class StockOverviewActivity extends BaseActivity {

    private static final int SORT_CODE = 1;
    private static final int SORT_STOCK = 2;
    private static final int SORT_OCCUPANCY = 3;
    private static final int SORT_ENTRY_DATE = 4;
    private static final int SORT_DAY_AGE = 5;

    private HerdActivityStockOverviewBinding binding;
    private StockOverviewViewModel vm;
    private StockHeaderAdapter header;
    private PenStockPagingAdapter penAdapter;
    private BatchPagingAdapter batchAdapter;
    private CategoryStockAdapter categoryAdapter;
    private FooterLoadStateAdapter penFooter;
    private FooterLoadStateAdapter batchFooter;
    private ConcatAdapter concat;
    @Nullable
    private RecyclerView.Adapter<?> currentList;
    @Nullable
    private FooterLoadStateAdapter currentFooter;
    private boolean penRefreshDone;
    private boolean batchRefreshDone;

    private final ActivityResultLauncher<Intent> childLauncher =
            Router.registerForResult(this, r -> vm.onChildResult(r.getResultCode(), null));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HerdActivityStockOverviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(HerdInjector.factory(), StockOverviewViewModel.class);
        observeBase(vm);

        binding.herdToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.herdToolbar.inflateMenu(R.menu.herd_menu_stock);
        TopAppBarHelper.tintMenu(binding.herdToolbar);
        binding.herdToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.herd_menu_search) {
                openSearch(true);
                return true;
            }
            if (item.getItemId() == R.id.herd_menu_add) {
                vm.onAddClick();
                return true;
            }
            return false;
        });
        setupSearch();
        setupList();

        binding.herdViewGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.herd_view_category) {
                vm.setViewType(HerdExtras.VIEW_CATEGORY);
            } else if (checkedId == R.id.herd_view_batch) {
                vm.setViewType(HerdExtras.VIEW_BATCH);
            } else {
                vm.setViewType(HerdExtras.VIEW_PEN);
            }
        });

        binding.herdState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.herdState::render);
        vm.getRefreshing().observe(this, r -> binding.herdRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.herdRefresh.setOnRefreshListener(() -> {
            vm.refresh();
            penAdapter.refresh();
            batchAdapter.refresh();
        });
        ClickThrottle.setOnClickListener(binding.herdFab, v -> vm.onFabClick());
        binding.herdList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                binding.herdAppBar.setLifted(rv.canScrollVertically(-1));
                if (dy > 4) {
                    binding.herdFab.shrink();
                } else if (dy < -4) {
                    binding.herdFab.extend();
                }
            }
        });

        vm.getViewType().observe(this, this::onViewTypeChanged);
        vm.getSummary().observe(this, header::setSummary);
        vm.getListTotal().observe(this, t -> renderTitle());
        vm.getPenSort().observe(this, s -> renderTitle());
        vm.getBatchSort().observe(this, s -> renderTitle());
        vm.getHasPen().observe(this, has -> {
            binding.herdFab.setVisibility(Boolean.FALSE.equals(has) ? View.GONE : View.VISIBLE);
            renderEmpty();
        });
        vm.getPenPaging().observe(this, data -> penAdapter.submitData(getLifecycle(), data));
        vm.getBatchPaging().observe(this, data -> batchAdapter.submitData(getLifecycle(), data));
        vm.getCategories().observe(this, list -> {
            categoryAdapter.setCategories(list);
            renderEmpty();
        });
        vm.getNavEvent().observe(this, t -> HerdNav.open(this, childLauncher, t));
    }

    private void setupList() {
        header = new StockHeaderAdapter(new StockHeaderAdapter.Listener() {
            @Override
            public void onSortClick(@NonNull View anchor) {
                showSortMenu(anchor);
            }

            @Override
            public void onEmptyActionClick() {
                vm.onAddClick();
            }
        });
        penAdapter = new PenStockPagingAdapter(vm::onPenClick);
        batchAdapter = new BatchPagingAdapter(vm::onBatchClick);
        categoryAdapter = new CategoryStockAdapter(vm::onBatchClick);
        penFooter = new FooterLoadStateAdapter(penAdapter::retry);
        batchFooter = new FooterLoadStateAdapter(batchAdapter::retry);
        penAdapter.addLoadStateListener(states -> {
            penRefreshDone = states.getRefresh() instanceof LoadState.NotLoading;
            return onLoadStates(states, penFooter, penAdapter.getItemCount());
        });
        batchAdapter.addLoadStateListener(states -> {
            batchRefreshDone = states.getRefresh() instanceof LoadState.NotLoading;
            return onLoadStates(states, batchFooter, batchAdapter.getItemCount());
        });
        concat = new ConcatAdapter(new ConcatAdapter.Config.Builder().setIsolateViewTypes(true).build(), header);
        binding.herdList.setLayoutManager(new LinearLayoutManager(this));
        binding.herdList.setAdapter(concat);
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states, @NonNull FooterLoadStateAdapter footer, int count) {
        footer.setHasItems(count > 0);
        footer.setLoadState(states.getAppend());
        renderEmpty();
        return Unit.INSTANCE;
    }

    private void onViewTypeChanged(@Nullable String type) {
        int checked = HerdExtras.VIEW_CATEGORY.equals(type) ? R.id.herd_view_category
                : HerdExtras.VIEW_BATCH.equals(type) ? R.id.herd_view_batch : R.id.herd_view_pen;
        if (binding.herdViewGroup.getCheckedButtonId() != checked) {
            binding.herdViewGroup.check(checked);
        }
        if (currentList != null) {
            concat.removeAdapter(currentList);
        }
        if (currentFooter != null) {
            concat.removeAdapter(currentFooter);
        }
        if (HerdExtras.VIEW_CATEGORY.equals(type)) {
            currentList = categoryAdapter;
            currentFooter = null;
        } else if (HerdExtras.VIEW_BATCH.equals(type)) {
            currentList = batchAdapter;
            currentFooter = batchFooter;
        } else {
            currentList = penAdapter;
            currentFooter = penFooter;
        }
        concat.addAdapter(currentList);
        if (currentFooter != null) {
            concat.addAdapter(currentFooter);
        }
        binding.herdList.setAlpha(0f);
        binding.herdList.animate().alpha(1f).setDuration(200).start();
        renderTitle();
        renderEmpty();
    }

    private void renderTitle() {
        String type = vm.currentViewType();
        Integer total = vm.getListTotal().getValue();
        int n = total == null ? 0 : total;
        if (HerdExtras.VIEW_CATEGORY.equals(type)) {
            header.setTitle(getString(R.string.herd_stock_title_category, n), null);
        } else if (HerdExtras.VIEW_BATCH.equals(type)) {
            String sort = StockOverviewViewModel.SORT_DAY_AGE.equals(vm.getBatchSort().getValue())
                    ? getString(R.string.herd_stock_sort_day_age) : getString(R.string.herd_stock_sort_entry_date);
            header.setTitle(getString(R.string.herd_stock_title_batch, n), sort);
        } else {
            String s = vm.getPenSort().getValue();
            String sort = StockOverviewViewModel.SORT_STOCK.equals(s) ? getString(R.string.herd_stock_sort_stock)
                    : StockOverviewViewModel.SORT_OCCUPANCY.equals(s) ? getString(R.string.herd_stock_sort_occupancy)
                    : getString(R.string.herd_stock_sort_code);
            header.setTitle(getString(R.string.herd_stock_title_pen, n), sort);
        }
    }

    /** 列表区空状态：还没有栏舍 / 还没有在养批次 / 没有找到匹配的栏舍或批次 */
    private void renderEmpty() {
        if (header == null) {
            return;
        }
        String type = vm.currentViewType();
        String keyword = vm.getKeyword().getValue();
        boolean searching = keyword != null && !keyword.trim().isEmpty();
        boolean empty;
        if (HerdExtras.VIEW_CATEGORY.equals(type)) {
            List<?> list = vm.getCategories().getValue();
            empty = list == null || list.isEmpty();
        } else if (HerdExtras.VIEW_BATCH.equals(type)) {
            empty = batchAdapter.getItemCount() == 0 && batchRefreshDone;
        } else {
            empty = penAdapter.getItemCount() == 0 && penRefreshDone;
        }
        if (!empty) {
            header.setEmpty(null, null, false);
        } else if (searching) {
            header.setEmpty(getString(R.string.herd_stock_search_empty), null, false);
        } else if (HerdExtras.VIEW_PEN.equals(type) && Boolean.FALSE.equals(vm.getHasPen().getValue())) {
            header.setEmpty(getString(R.string.herd_stock_no_pen_title), getString(R.string.herd_stock_no_pen_message),
                    true);
        } else if (HerdExtras.VIEW_PEN.equals(type)) {
            header.setEmpty(null, null, false);
        } else {
            header.setEmpty(getString(R.string.herd_stock_no_batch), null, false);
        }
    }

    private void showSortMenu(@NonNull View anchor) {
        boolean batch = HerdExtras.VIEW_BATCH.equals(vm.currentViewType());
        List<MenuItemSpec> items = batch
                ? Arrays.asList(MenuItemSpec.normal(SORT_ENTRY_DATE, getString(R.string.herd_stock_sort_entry_date), 0),
                MenuItemSpec.normal(SORT_DAY_AGE, getString(R.string.herd_stock_sort_day_age), 0))
                : Arrays.asList(MenuItemSpec.normal(SORT_CODE, getString(R.string.herd_stock_sort_code), 0),
                MenuItemSpec.normal(SORT_STOCK, getString(R.string.herd_stock_sort_stock), 0),
                MenuItemSpec.normal(SORT_OCCUPANCY, getString(R.string.herd_stock_sort_occupancy), 0));
        MenuItemSpec.show(anchor, items, id -> {
            switch (id) {
                case SORT_STOCK:
                    vm.setSort(StockOverviewViewModel.SORT_STOCK);
                    break;
                case SORT_OCCUPANCY:
                    vm.setSort(StockOverviewViewModel.SORT_OCCUPANCY);
                    break;
                case SORT_ENTRY_DATE:
                    vm.setSort(StockOverviewViewModel.SORT_ENTRY_DATE);
                    break;
                case SORT_DAY_AGE:
                    vm.setSort(StockOverviewViewModel.SORT_DAY_AGE);
                    break;
                default:
                    vm.setSort(StockOverviewViewModel.SORT_CODE);
                    break;
            }
        });
    }

    // ===== 搜索 =====

    private void setupSearch() {
        binding.herdSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.onKeywordChanged(s.toString());
            }
        });
        ClickThrottle.setOnClickListener(binding.herdSearchClose, v -> {
            binding.herdSearchInput.setText("");
            openSearch(false);
        });
        String keyword = vm.getKeyword().getValue();
        if (keyword != null && !keyword.isEmpty()) {
            openSearch(true);
        }
    }

    private void openSearch(boolean open) {
        binding.herdSearchBar.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.herdToolbar.setVisibility(open ? View.INVISIBLE : View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (open) {
            binding.herdSearchInput.requestFocus();
            if (imm != null) {
                imm.showSoftInput(binding.herdSearchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            hideKeyboard(binding.herdSearchInput);
        }
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (PenEditViewModel.ACTION_ASSIGN.equals(actionId)) {
            vm.onAssignClick();
        }
    }
}
