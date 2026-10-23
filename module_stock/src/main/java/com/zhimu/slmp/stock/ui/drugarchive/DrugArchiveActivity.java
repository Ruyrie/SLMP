package com.zhimu.slmp.stock.ui.drugarchive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.databinding.StockActivityDrugArchiveBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.DrugDetailVO;
import com.zhimu.slmp.stock.ui.common.ChangeLogActivity;
import com.zhimu.slmp.stock.ui.common.PickerData;
import com.zhimu.slmp.stock.ui.common.PickerHost;
import com.zhimu.slmp.stock.ui.common.PickerSheet;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.ui.drugstock.NearDaysSheet;
import com.zhimu.slmp.stock.util.StockSettingDelegate;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.ArrayList;
import java.util.List;

/**
 * M09 药品档案（新增 / 编辑）。保存成功带 drug_id（{@link StockExtras#RESULT_DRUG_ID}）RESULT_OK 返回上一页并全局提示。
 */
public class DrugArchiveActivity extends BaseActivity implements PickerHost, NearDaysSheet.Host {

    private static final String REQ_UNIT = "stock_m09_unit";
    private static final String REQ_DISCARD = "stock_m09_discard";
    private static final String REQ_CONFLICT = "stock_m09_conflict";

    private StockActivityDrugArchiveBinding binding;
    private DrugArchiveViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.stock_activity_drug_archive);
        vm = obtainViewModel(StockInjector.factory(), DrugArchiveViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        boolean edit = vm.isEdit();
        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar,
                getString(edit ? R.string.stock_drug_archive_title : R.string.stock_drug_archive_new));
        binding.stockTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        if (edit) {
            binding.stockTopBar.baseToolbar.inflateMenu(R.menu.stock_menu_history);
            TopAppBarHelper.tintMenu(binding.stockTopBar.baseToolbar);
            binding.stockTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
                vm.onChangeLog();
                return true;
            });
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });

        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, s -> renderBar());
        vm.getBanned().observe(this, b -> renderBar());
        vm.getCategory().observe(this, this::renderCategory);
        vm.getDetail().observe(this, this::renderCard);
        binding.stockCategoryGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) {
                return;
            }
            vm.setCategory(id == R.id.stock_cat_vaccine_btn ? StockEnums.CAT_VACCINE
                    : id == R.id.stock_cat_dis_btn ? StockEnums.CAT_DISINFECTANT : StockEnums.CAT_VET_DRUG);
        });
        ClickThrottle.setOnClickListener(binding.stockNameInput, v -> showCatalog());
        binding.stockNameLayout.setEndIconOnClickListener(v -> showCatalog());
        ClickThrottle.setOnClickListener(binding.stockUnitInput, v -> showUnits());
        binding.stockUnitLayout.setEndIconOnClickListener(v -> showUnits());
        binding.stockNearEdit.setOnClickListener(v -> vm.openNearDays());
        binding.stockChangeLog.setOnClickListener(v -> vm.onChangeLog());
        binding.stockActionBar.setOnPrimaryClickListener(v -> vm.save());
        vm.getSubmitting().observe(this, s -> binding.stockActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFocusField().observe(this, f -> StockUi.scrollTo(binding.stockScroll,
                DrugArchiveViewModel.F_CATALOG.equals(f) ? binding.stockNameLayout
                        : DrugArchiveViewModel.F_SPEC.equals(f) ? binding.stockSpecLayout
                        : DrugArchiveViewModel.F_UNIT.equals(f) ? binding.stockUnitLayout : binding.stockMinLayout));

        getSupportFragmentManager().setFragmentResultListener(DrugArchiveViewModel.PICKER_CATALOG, this, (k, r) -> {
            String v = PickerSheet.valueOf(r);
            if (v != null) {
                vm.onCatalogPicked(Long.parseLong(v));
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_UNIT, this,
                (k, r) -> vm.onPackUnitPicked(OptionSheetDialogFragment.getValue(r)));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_CONFLICT, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onConflictRefresh();
            }
        });
        vm.getShowDiscard().observe(this, v -> StockUi.showDiscard(this, REQ_DISCARD));
        vm.getConflict().observe(this, msg -> ConfirmDialogFragment.builder(REQ_CONFLICT)
                .title(getString(R.string.stock_conflict_title))
                .positive(getString(R.string.stock_action_refresh))
                .negative(getString(R.string.stock_action_cancel))
                .build().showSafely(getSupportFragmentManager()));
        vm.getSavedMessage().observe(this, msg -> GlobalEvents.postMessage(UiMessage.of(msg)));
        vm.getSaved().observe(this, d -> {
            setResult(Activity.RESULT_OK, new Intent().putExtra(StockExtras.RESULT_DRUG_ID, d == null ? 0L : d.drugId));
            finish();
        });
        vm.getOpenChangeLog().observe(this, id -> startActivity(ChangeLogActivity.intent(this,
                StockEnums.BIZ_DRUG, id)));
        vm.getShowNearDays().observe(this, v -> NearDaysSheet.newInstance(getString(R.string.stock_near_days_title),
                NearDaysSheet.description(this), vm.getCategory().getValue())
                .showSafely(getSupportFragmentManager(), NearDaysSheet.TAG));
        vm.getNearDaysSaved().observe(this, msg -> {
            Fragment f = getSupportFragmentManager().findFragmentByTag(NearDaysSheet.TAG);
            if (f instanceof DialogFragment) {
                ((DialogFragment) f).dismissAllowingStateLoss();
            }
            showMessage(msg);
        });
    }

    private void renderBar() {
        UiState s = vm.getUiState().getValue();
        binding.stockState.render(s);
        boolean content = s != null && s.is(UiState.Status.CONTENT);
        binding.stockActionBar.setVisibility(content && !Boolean.TRUE.equals(vm.getBanned().getValue())
                ? View.VISIBLE : View.GONE);
    }

    private void renderCategory(@Nullable String c) {
        int id = StockEnums.CAT_VACCINE.equals(c) ? R.id.stock_cat_vaccine_btn
                : StockEnums.CAT_DISINFECTANT.equals(c) ? R.id.stock_cat_dis_btn : R.id.stock_cat_vet_btn;
        if (binding.stockCategoryGroup.getCheckedButtonId() != id) {
            binding.stockCategoryGroup.check(id);
        }
        binding.stockNameInput.setHint(StockEnums.CAT_DISINFECTANT.equals(c) ? R.string.stock_name_placeholder_dis
                : R.string.stock_name_placeholder);
    }

    private void renderCard(@Nullable DrugDetailVO d) {
        if (d == null) {
            return;
        }
        binding.stockDrugIcon.setImageResource(StockTexts.categoryIcon(d.category));
        StockUi.tone(binding.stockDrugLeading, binding.stockDrugIcon, ListRowView.TONE_SECONDARY);
        binding.stockDrugSource.setText(StockTexts.join(StockTexts.DOT, StockEnums.categoryText(d.category),
                d.catalogSourceText));
    }

    private void showCatalog() {
        if (Boolean.TRUE.equals(vm.getLocked().getValue()) || Boolean.TRUE.equals(vm.getBanned().getValue())) {
            return;
        }
        String kw = vm.openCatalogPicker();
        boolean dis = StockEnums.CAT_DISINFECTANT.equals(vm.getCategory().getValue());
        PickerSheet.builder(DrugArchiveViewModel.PICKER_CATALOG)
                .title(getString(dis ? R.string.stock_pick_disinfectant_title : R.string.stock_pick_name_title))
                .search(getString(R.string.stock_search_name_hint), kw)
                .build().showSafely(getSupportFragmentManager());
    }

    private void showUnits() {
        if (Boolean.TRUE.equals(vm.getLocked().getValue()) || Boolean.TRUE.equals(vm.getBanned().getValue())) {
            return;
        }
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        List<String> list = vm.getPackUnitOptions().getValue();
        if (list == null || list.isEmpty()) {
            showMessage(getString(R.string.stock_pick_name_first));
            return;
        }
        for (String u : list) {
            options.add(new OptionSheetDialogFragment.Option(u, u, null));
        }
        OptionSheetDialogFragment.builder(REQ_UNIT)
                .title(getString(R.string.stock_field_stock_unit))
                .options(options)
                .selected(vm.getPackUnit().getValue())
                .build().showSafely(getSupportFragmentManager());
    }

    // ===================== PickerHost / NearDaysSheet.Host =====================

    @Nullable
    @Override
    public LiveData<PickerData> pickerData(@NonNull String pickerKey) {
        return vm.getCatalogPicker();
    }

    @Override
    public void onPickerSearch(@NonNull String pickerKey, @NonNull String keyword) {
        vm.searchCatalog(keyword);
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
