package com.zhimu.slmp.breed.ui.due;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedActivityDueBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.SearchBarController;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;

/**
 * B02 待产与待出雏（RouterPath.B02）：参数 EXTRA_TAB（FARROW / HATCH），不带时禽类养殖场停在“待出雏”。
 * 员工从 L02“待产与待出雏 ›”进入只读版（只含负责栏舍，没有登记按钮，列表项不可点）。
 */
public class BreedDueActivity extends BaseActivity {

    private static final String TAG_FARROW = "breed_due_farrow";
    private static final String TAG_HATCH = "breed_due_hatch";

    private BreedActivityDueBinding binding;
    private BreedDueViewModel vm;
    private SearchBarController search;
    @Nullable
    private Long lastFarmId;
    private final ActivityResultLauncher<Intent> launcher = Router.registerForResult(this, result -> {
        UiMessage msg = BreedNav.messageOf(this, result);
        if (result.getResultCode() == RESULT_OK) {
            vm.refresh();
        }
        if (msg != null) {
            showMessage(msg);
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = BreedActivityDueBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(BreedInjector.factory(), BreedDueViewModel.class);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.breedTopBar.baseToolbar, getString(R.string.breed_due_title));
        TopAppBarHelper.inflateMenu(binding.breedTopBar.baseToolbar, R.menu.breed_menu_search);
        search = new SearchBarController(binding.breedTopBar.getRoot(), binding.breedSearch,
                getString(R.string.breed_search_hint_due), vm::search);
        binding.breedTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.breed_menu_search) {
                search.open();
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (search.isActive()) {
                    search.close();
                } else {
                    finish();
                }
            }
        });

        binding.breedState.setOnRetryListener(v -> vm.retry());
        binding.breedState.setOnNoPermissionActionListener(v -> finish());
        vm.getUiState().observe(this, binding.breedState::render);

        ensureFragments();
        binding.breedTabs.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                vm.switchTab(id == R.id.breed_tab_hatch ? BreedExtras.TAB_HATCH : BreedExtras.TAB_FARROW);
            }
        });
        vm.getTab().observe(this, this::showTab);
        vm.getOverview().observe(this, this::renderCounts);
        vm.getCurrentFarmId().observe(this, id -> {
            if (lastFarmId != null && id != null && !lastFarmId.equals(id)) {
                vm.onFarmChanged();
            }
            lastFarmId = id;
        });

        vm.getOpenFarrowing().observe(this, id -> Router.openForResult(this, launcher, RouterPath.B04,
                BreedNav.forResult(BreedNav.id(RouterPath.EXTRA_ANIMAL_ID, id))));
        vm.getOpenArchive().observe(this, id -> Router.openForResult(this, launcher, RouterPath.B06,
                BreedNav.forResult(BreedNav.id(RouterPath.EXTRA_ANIMAL_ID, id))));
        vm.getOpenIncubation().observe(this, id -> Router.openForResult(this, launcher, RouterPath.B07,
                BreedNav.forResult(id == null || id == 0 ? null : BreedNav.id(RouterPath.EXTRA_INCUBATION_ID, id))));
        vm.getOpenFarmPicker().observe(this, v -> {
            IFarmContextService service = ServiceHub.get(IFarmContextService.class);
            if (service != null) {
                service.showSwitcher(this, false, false);
            } else {
                showMessage("暂时无法切换养殖场，请从顶部养殖场切换器切换");
            }
        });
        vm.start();
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        BreedNav.handleAction(this, actionId);
    }

    private void ensureFragments() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_FARROW) == null) {
            HatchDueFragment hatch = HatchDueFragment.newInstance();
            fm.beginTransaction()
                    .add(R.id.breed_due_container, FarrowDueFragment.newInstance(false), TAG_FARROW)
                    .add(R.id.breed_due_container, hatch, TAG_HATCH)
                    .hide(hatch)
                    .commitNow();
        }
    }

    private void showTab(@Nullable String tab) {
        if (tab == null) {
            return;
        }
        boolean hatch = BreedExtras.TAB_HATCH.equals(tab);
        int checked = hatch ? R.id.breed_tab_hatch : R.id.breed_tab_farrow;
        if (binding.breedTabs.getCheckedButtonId() != checked) {
            binding.breedTabs.check(checked);
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment farrow = fm.findFragmentByTag(TAG_FARROW);
        Fragment hatchF = fm.findFragmentByTag(TAG_HATCH);
        if (farrow == null || hatchF == null) {
            return;
        }
        FragmentTransaction tx = fm.beginTransaction();
        if (hatch) {
            tx.hide(farrow).show(hatchF);
        } else {
            tx.hide(hatchF).show(farrow);
        }
        tx.commitAllowingStateLoss();
    }

    private void renderCounts(@Nullable BreedingOverviewVO o) {
        if (o == null) {
            return;
        }
        binding.breedTabFarrow.setText(getString(R.string.breed_tab_farrow_count, o.getFarrowDueCount()));
        binding.breedTabHatch.setText(o.getHatchDueCount() > 0
                ? getString(R.string.breed_tab_hatch_count, o.getHatchDueCount()) : getString(R.string.breed_tab_hatch));
    }
}
