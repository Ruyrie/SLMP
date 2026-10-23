package com.zhimu.slmp.breed.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedActivityHomeBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.ListScrollHost;
import com.zhimu.slmp.breed.ui.common.SearchBarController;
import com.zhimu.slmp.breed.ui.due.BreedDueViewModel;
import com.zhimu.slmp.breed.ui.due.DueListFragment;
import com.zhimu.slmp.breed.ui.due.FarrowDueFragment;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * B01 种畜与待产（RouterPath.B01，L01“繁育”进入）：禽类养殖场转到 B02 待出雏；员工显示无权查看。
 * EXTRA_MODE = EXITED 为“已淘汰和死亡的母猪”只读列表（B01 更多菜单进入）。
 */
public class BreedHomeActivity extends BaseActivity implements ListScrollHost {

    private static final String TAG_ANIMALS = "breed_home_animals";
    private static final String TAG_DUE = "breed_home_due";

    private BreedActivityHomeBinding binding;
    private BreedHomeViewModel vm;
    private BreedDueViewModel dueVm;
    private SearchBarController search;
    @Nullable
    private Long lastFarmId;
    private final ActivityResultLauncher<Intent> launcher = Router.registerForResult(this, result -> {
        UiMessage msg = BreedNav.messageOf(this, result);
        if (result.getResultCode() == RESULT_OK) {
            vm.onChildResult(msg);
            dueVm.refresh();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = BreedActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(BreedInjector.factory(), BreedHomeViewModel.class);
        dueVm = obtainViewModel(BreedInjector.factory(), BreedDueViewModel.class);
        observeBase(vm);
        observeBase(dueVm);
        boolean exited = Boolean.TRUE.equals(vm.getExitedMode().getValue());

        TopAppBarHelper.setupBack(binding.breedTopBar.baseToolbar,
                getString(exited ? R.string.breed_home_exited_title : R.string.breed_home_title));
        TopAppBarHelper.inflateMenu(binding.breedTopBar.baseToolbar,
                exited ? R.menu.breed_menu_search : R.menu.breed_menu_search_more);
        search = new SearchBarController(binding.breedTopBar.getRoot(), binding.breedSearch,
                getString(R.string.breed_search_hint_animal), text -> {
            if (BreedHomeViewModel.SEGMENT_DUE.equals(vm.getSegment().getValue())) {
                dueVm.search(text);
            } else {
                vm.search(text);
            }
        });
        binding.breedTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.breed_menu_search) {
                search.open();
                return true;
            }
            if (item.getItemId() == R.id.breed_menu_more) {
                showMoreMenu();
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
        vm.getUiState().observe(this, state -> {
            binding.breedState.render(state);
            boolean hideFab = Boolean.TRUE.equals(vm.getExitedMode().getValue())
                    || (state != null && state.is(com.zhimu.slmp.base.mvvm.UiState.Status.NO_PERMISSION));
            binding.breedFab.setVisibility(hideFab ? View.GONE : View.VISIBLE);
            // 员工无权查看时不渲染搜索与更多菜单
            boolean noPermission = state != null && state.is(com.zhimu.slmp.base.mvvm.UiState.Status.NO_PERMISSION);
            binding.breedTopBar.baseToolbar.getMenu().setGroupVisible(0, !noPermission);
        });

        ensureFragments(exited);
        binding.breedSegments.setVisibility(exited ? View.GONE : View.VISIBLE);
        binding.breedSegments.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                if (search.isActive()) {
                    search.close();
                }
                vm.switchSegment(id == R.id.breed_segment_due ? BreedHomeViewModel.SEGMENT_DUE
                        : BreedHomeViewModel.SEGMENT_ARCHIVE);
            }
        });
        vm.getSegment().observe(this, this::showSegment);
        vm.getOverview().observe(this, this::renderSegments);
        renderSegments(null);
        vm.getScrollToToday().observe(this, v -> {
            Fragment due = getSupportFragmentManager().findFragmentByTag(TAG_DUE);
            if (due instanceof DueListFragment) {
                ((DueListFragment) due).scrollToToday();
            }
        });
        vm.getRedirectToHatch().observe(this, v -> {
            Bundle b = new Bundle();
            b.putString(RouterPath.EXTRA_TAB, BreedExtras.TAB_HATCH);
            Router.open(this, RouterPath.B02, b);
            finish();
        });
        vm.getCurrentFarmId().observe(this, id -> {
            if (lastFarmId != null && id != null && !lastFarmId.equals(id)) {
                vm.onFarmChanged();
                dueVm.onFarmChanged();
            }
            lastFarmId = id;
        });

        binding.breedFab.setOnClickListener(v -> vm.onFabClick());
        vm.getOpenRegisterSheet().observe(this, v -> BreedRegisterSheet.open(getSupportFragmentManager(),
                getString(R.string.breed_register_sheet_title),
                getString(R.string.breed_register_sheet_desc, vm.farmName() == null ? "" : vm.farmName())));
        getSupportFragmentManager().setFragmentResultListener(BreedRegisterSheet.REQUEST_KEY, this,
                (key, result) -> vm.onRegisterPicked(result.getString(BreedRegisterSheet.RESULT_TYPE)));
        vm.getOpenPage().observe(this, this::openPage);
        vm.getOpenArchive().observe(this, id -> Router.openForResult(this, launcher, RouterPath.B06,
                BreedNav.forResult(BreedNav.id(RouterPath.EXTRA_ANIMAL_ID, id))));
        dueVm.getOpenFarrowing().observe(this, id -> Router.openForResult(this, launcher, RouterPath.B04,
                BreedNav.forResult(BreedNav.id(RouterPath.EXTRA_ANIMAL_ID, id))));
        dueVm.getOpenArchive().observe(this, id -> Router.openForResult(this, launcher, RouterPath.B06,
                BreedNav.forResult(BreedNav.id(RouterPath.EXTRA_ANIMAL_ID, id))));
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        BreedNav.handleAction(this, actionId);
    }

    /** Snackbar 显示在 FAB 上方 */
    @Nullable
    @Override
    public View getSnackbarAnchor() {
        return binding != null && binding.breedFab.getVisibility() == View.VISIBLE ? binding.breedFab
                : super.getSnackbarAnchor();
    }

    @Override
    public void onListScrolled(int dy) {
        if (dy > 8 && binding.breedFab.isExtended()) {
            binding.breedFab.shrink();
        } else if (dy < -8 && !binding.breedFab.isExtended()) {
            binding.breedFab.extend();
        }
    }

    private void ensureFragments(boolean exited) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_ANIMALS) == null) {
            FragmentTransaction tx = fm.beginTransaction()
                    .add(R.id.breed_home_container, new AnimalListFragment(), TAG_ANIMALS);
            if (!exited) {
                FarrowDueFragment due = FarrowDueFragment.newInstance(true);
                tx.add(R.id.breed_home_container, due, TAG_DUE).hide(due);
            }
            tx.commitNow();
        }
    }

    private void showSegment(@Nullable String segment) {
        boolean due = BreedHomeViewModel.SEGMENT_DUE.equals(segment);
        int checked = due ? R.id.breed_segment_due : R.id.breed_segment_archive;
        if (binding.breedSegments.getCheckedButtonId() != checked) {
            binding.breedSegments.check(checked);
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment animals = fm.findFragmentByTag(TAG_ANIMALS);
        Fragment dueF = fm.findFragmentByTag(TAG_DUE);
        if (animals == null || dueF == null) {
            return;
        }
        FragmentTransaction tx = fm.beginTransaction();
        if (due) {
            tx.hide(animals).show(dueF);
        } else {
            tx.hide(dueF).show(animals);
        }
        tx.commitAllowingStateLoss();
    }

    private void renderSegments(@Nullable BreedingOverviewVO o) {
        String label = o == null || o.getAnimalLabel() == null ? "母猪" : o.getAnimalLabel();
        int all = o == null || o.getCounts() == null ? 0 : o.getCounts().getAll();
        binding.breedSegmentArchive.setText(getString(R.string.breed_segment_archive, label, all));
        binding.breedSegmentDue.setText(getString(R.string.breed_segment_due, o == null ? 0 : o.getFarrowDueCount()));
    }

    private void showMoreMenu() {
        View anchor = binding.breedTopBar.baseToolbar.findViewById(R.id.breed_menu_more);
        if (anchor == null) {
            return;
        }
        MoreMenuHelper.with(anchor)
                .add(R.id.breed_menu_exited, getString(R.string.breed_menu_exited),
                        com.zhimu.slmp.base.R.drawable.ic_history)
                .add(R.id.breed_menu_reminder, getString(R.string.breed_menu_reminder),
                        com.zhimu.slmp.base.R.drawable.ic_notifications)
                .setOnItemClickListener(id -> {
                    if (id == R.id.breed_menu_exited) {
                        Bundle b = new Bundle();
                        b.putString(RouterPath.EXTRA_MODE, BreedExtras.MODE_EXITED);
                        Router.openForResult(this, launcher, RouterPath.B01, BreedNav.forResult(b));
                    } else if (id == R.id.breed_menu_reminder) {
                        Router.open(this, RouterPath.T05);
                    }
                })
                .show();
    }

    private void openPage(@Nullable String type) {
        if (type == null) {
            return;
        }
        String action;
        switch (type) {
            case BreedCodes.TYPE_MATING:
                action = RouterPath.B03;
                break;
            case BreedCodes.TYPE_FARROWING:
                action = RouterPath.B04;
                break;
            case BreedCodes.TYPE_WEANING:
                action = RouterPath.B05;
                break;
            case BreedHomeViewModel.PAGE_NEW_ANIMAL:
                action = BreedExtras.ACTION_ANIMAL_FORM;
                break;
            default:
                return;
        }
        Router.openForResult(this, launcher, action, BreedNav.forResult(null));
    }
}
