package com.zhimu.slmp.stock.ui.drugstock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.databinding.StockActivityDrugStockBinding;
import com.zhimu.slmp.stock.databinding.StockItemEmptyBinding;
import com.zhimu.slmp.stock.databinding.StockViewDrugHeaderBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.stock.ui.common.PickerData;
import com.zhimu.slmp.stock.ui.common.PickerHost;
import com.zhimu.slmp.stock.ui.common.PickerSheet;
import com.zhimu.slmp.stock.ui.common.RowPagingAdapter;
import com.zhimu.slmp.stock.ui.common.SimpleTextWatcher;
import com.zhimu.slmp.stock.ui.common.SingleViewAdapter;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.flow.StockFlowListActivity;
import com.zhimu.slmp.stock.util.StockSettingDelegate;

import kotlin.Unit;

/**
 * M05 兽药与疫苗库存。M06、M08、M09、M11 以 RESULT_OK 返回时刷新；弹窗不叠弹窗：菜单收起后再弹面板，面板关闭后再推入页面。
 */
public class DrugStockActivity extends BaseActivity implements PickerHost, NearDaysSheet.Host {

    private static final int MENU_OUTBOUND = 1;
    private static final int MENU_ARCHIVE = 2;
    private static final int MENU_NEAR = 3;
    private static final int MENU_FLOWS = 4;
    private static final String[][] CATEGORIES = {{"", "全部"}, {StockEnums.CAT_VET_DRUG, "兽药"},
            {StockEnums.CAT_VACCINE, "疫苗"}, {StockEnums.CAT_DISINFECTANT, "消毒剂"}};

    private StockActivityDrugStockBinding binding;
    private StockViewDrugHeaderBinding header;
    private StockItemEmptyBinding emptyRow;
    private SingleViewAdapter emptyAdapter;
    private DrugStockViewModel vm;
    private LotPagingAdapter lots;
    private ActivityResultLauncher<Intent> childLauncher;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingSearch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = StockActivityDrugStockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(StockInjector.factory(), DrugStockViewModel.class);
        observeBase(vm);
        childLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                vm.refresh();
            }
        });

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_drug_title));
        binding.stockTopBar.baseToolbar.inflateMenu(R.menu.stock_menu_drug_stock);
        TopAppBarHelper.tintMenu(binding.stockTopBar.baseToolbar);
        binding.stockTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.stock_menu_search) {
                setSearchMode(true);
            } else {
                showMenu();
            }
            return true;
        });
        binding.stockSearchBack.setOnClickListener(v -> setSearchMode(false));
        binding.stockSearchInput.addTextChangedListener(new SimpleTextWatcher(this::scheduleSearch));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.stockSearchBar.getVisibility() == View.VISIBLE) {
                    setSearchMode(false);
                } else {
                    finish();
                }
            }
        });

        RecyclerView list = binding.stockList;
        list.setLayoutManager(new LinearLayoutManager(this));
        header = StockViewDrugHeaderBinding.inflate(getLayoutInflater(), list, false);
        emptyRow = StockItemEmptyBinding.inflate(getLayoutInflater(), list, false);
        emptyAdapter = new SingleViewAdapter(emptyRow.getRoot(), false);
        buildChips();
        header.stockLowBanner.setAction(getString(R.string.stock_action_stock_in), v -> vm.onBannerAction());
        lots = new LotPagingAdapter(vm::onLotClick);
        ConcatAdapter concat = new ConcatAdapter(new SingleViewAdapter(header.getRoot()),
                lots.withLoadStateFooter(new FooterLoadStateAdapter(lots::retry)), emptyAdapter);
        list.setAdapter(concat);
        list.addItemDecoration(new RowPagingAdapter.Decoration(this, lots, 1));
        lots.addLoadStateListener(this::onLoadStates);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 8) {
                    binding.stockFab.shrink();
                } else if (dy < -8) {
                    binding.stockFab.extend();
                }
            }
        });

        binding.stockRefresh.setOnChildScrollUpCallback((p, c) -> list.canScrollVertically(-1));
        binding.stockRefresh.setOnRefreshListener(vm::refresh);
        vm.getRefreshing().observe(this, r -> binding.stockRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.stockState.setEmpty(com.zhimu.slmp.base.R.drawable.ic_medical_services,
                getString(R.string.stock_drug_empty), null, getString(R.string.stock_action_inbound),
                com.zhimu.slmp.base.R.drawable.ic_add, StateLayout.EMPTY_ACTION_TONAL, v -> vm.onFabClick());
        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, s -> {
            binding.stockState.render(s);
            renderFab();
        });
        vm.getPagingData().observe(this, d -> lots.submitData(getLifecycle(), d));
        vm.getLowBannerText().observe(this, t -> {
            header.stockLowBanner.setVisibility(t == null ? View.GONE : View.VISIBLE);
            header.stockLowBanner.setText(t);
        });
        vm.getNearRuleText().observe(this, header.stockNearRule::setText);
        vm.getReadonlyText().observe(this, t -> {
            header.stockReadonlyNotice.setVisibility(t == null ? View.GONE : View.VISIBLE);
            header.stockReadonlyNotice.setText(t);
        });
        vm.getReadonly().observe(this, r -> renderFab());
        vm.getCurrentFarmId().observe(this, id -> {
        });
        binding.stockFab.setOnClickListener(v -> vm.onFabClick());

        vm.getOpenLot().observe(this, id -> StockNav.open(this, childLauncher, RouterPath.M06,
                StockNav.idArgs(RouterPath.EXTRA_LOT_ID, id)));
        vm.getOpenInbound().observe(this, id -> StockNav.open(this, childLauncher, RouterPath.M08,
                StockNav.idArgs(RouterPath.EXTRA_DRUG_ID, id)));
        vm.getOpenArchive().observe(this, this::openArchive);
        vm.getShowArchivePicker().observe(this, v -> PickerSheet.builder(DrugStockViewModel.PICKER_ARCHIVE)
                .title(getString(R.string.stock_drug_archive_title))
                .description(vm.archiveSubtitleText())
                .footer(Boolean.TRUE.equals(vm.getReadonly().getValue()) ? null : getString(R.string.stock_drug_archive_new))
                .build().showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(DrugStockViewModel.PICKER_ARCHIVE, this, (k, r) -> {
            if (PickerSheet.EVENT_FOOTER.equals(PickerSheet.eventOf(r))) {
                vm.onArchiveNew();
            } else if (PickerSheet.valueOf(r) != null) {
                vm.onArchivePicked(Long.parseLong(PickerSheet.valueOf(r)));
            }
        });
        vm.getShowNearDays().observe(this, v -> NearDaysSheet.newInstance(getString(R.string.stock_near_days_title),
                NearDaysSheet.description(this), null).showSafely(getSupportFragmentManager(), NearDaysSheet.TAG));
        vm.getNearDaysSaved().observe(this, msg -> {
            Fragment f = getSupportFragmentManager().findFragmentByTag(NearDaysSheet.TAG);
            if (f instanceof DialogFragment) {
                ((DialogFragment) f).dismissAllowingStateLoss();
            }
            showMessage(msg);
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void openArchive(@Nullable Long drugId) {
        Bundle b = StockNav.idArgs(RouterPath.EXTRA_DRUG_ID, drugId);
        if (drugId == null && vm.currentCategory() != null) {
            b.putString(RouterPath.EXTRA_CATEGORY, vm.currentCategory());
        }
        StockNav.open(this, childLauncher, RouterPath.M09, b);
    }

    private void buildChips() {
        header.stockCategoryChips.removeAllViews();
        String current = vm.currentCategory();
        for (String[] c : CATEGORIES) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.stock_item_filter_chip, header.stockCategoryChips,
                    false);
            chip.setId(View.generateViewId());
            chip.setText(c[1]);
            chip.setTag(c[0]);
            chip.setCheckable(true);
            chip.setChecked(c[0].equals(current == null ? "" : current));
            header.stockCategoryChips.addView(chip);
        }
        header.stockCategoryChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            String tag = (String) group.findViewById(ids.get(0)).getTag();
            vm.setCategory(tag == null || tag.isEmpty() ? null : tag);
            binding.stockList.scrollToPosition(0);
        });
    }

    private void renderFab() {
        binding.stockFab.setVisibility(vm.isContent() && !Boolean.TRUE.equals(vm.getReadonly().getValue())
                ? View.VISIBLE : View.GONE);
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        boolean done = states.getRefresh() instanceof LoadState.NotLoading;
        boolean empty = done && lots.getItemCount() == 0;
        emptyRow.stockEmptyText.setText(vm.emptyText());
        emptyAdapter.setVisible(empty && vm.isContent());
        return Unit.INSTANCE;
    }

    private void setSearchMode(boolean on) {
        binding.stockSearchBar.setVisibility(on ? View.VISIBLE : View.GONE);
        binding.stockTopBar.getRoot().setVisibility(on ? View.GONE : View.VISIBLE);
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (on) {
            binding.stockSearchInput.requestFocus();
            if (imm != null) {
                imm.showSoftInput(binding.stockSearchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            binding.stockSearchInput.setText("");
            if (imm != null) {
                imm.hideSoftInputFromWindow(binding.stockSearchInput.getWindowToken(), 0);
            }
            vm.search(null);
        }
    }

    private void scheduleSearch(@NonNull String keyword) {
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> {
            vm.search(keyword);
            binding.stockList.scrollToPosition(0);
        };
        handler.postDelayed(pendingSearch, 300);
    }

    private void showMenu() {
        View anchor = binding.stockTopBar.baseToolbar.findViewById(R.id.stock_menu_more);
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor != null ? anchor : binding.stockTopBar.baseToolbar);
        boolean readonly = Boolean.TRUE.equals(vm.getReadonly().getValue());
        if (!readonly) {
            b.add(MENU_OUTBOUND, getString(R.string.stock_drug_menu_outbound), R.drawable.stock_ic_output);
        }
        b.add(MENU_ARCHIVE, getString(R.string.stock_drug_archive_title), R.drawable.stock_ic_medical_information);
        if (!readonly) {
            b.add(MENU_NEAR, getString(R.string.stock_near_days_title), com.zhimu.slmp.base.R.drawable.ic_schedule);
        }
        b.add(MENU_FLOWS, getString(R.string.stock_flow_menu), com.zhimu.slmp.base.R.drawable.ic_receipt_long);
        b.setOnItemClickListener(id -> {
            if (id == MENU_OUTBOUND) {
                StockNav.open(this, childLauncher, RouterPath.M11, null);
            } else if (id == MENU_ARCHIVE) {
                vm.openArchivePicker();
            } else if (id == MENU_NEAR) {
                vm.openNearDays();
            } else if (id == MENU_FLOWS) {
                childLauncher.launch(StockFlowListActivity.intent(this, StockEnums.MATERIAL_DRUG, null, null));
            }
        }).show();
    }

    // ===================== PickerHost / NearDaysSheet.Host =====================

    @Nullable
    @Override
    public LiveData<PickerData> pickerData(@NonNull String pickerKey) {
        return vm.getArchivePicker();
    }

    @Override
    public void onPickerSearch(@NonNull String pickerKey, @NonNull String keyword) {
    }

    @NonNull
    @Override
    public StockSettingDelegate nearDaysDelegate() {
        return vm.getSettings();
    }

    @NonNull
    @Override
    public LiveData<Boolean> nearDaysSaving() {
        return vm.getSettingSaving();
    }

    @Override
    public void onSaveNearDays() {
        vm.saveNearDays();
    }
}
