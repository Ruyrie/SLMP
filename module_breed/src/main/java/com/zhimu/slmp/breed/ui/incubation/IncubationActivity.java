package com.zhimu.slmp.breed.ui.incubation;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedActivityIncubationBinding;
import com.zhimu.slmp.breed.databinding.BreedItemStepBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.ui.archive.ChangeLogActivity;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.NavRequest;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.List;

/**
 * B07 禽类孵化（RouterPath.B07）：参数 EXTRA_INCUBATION_ID；不带为新建入孵（B02“登记入孵”）。按 currentStep 替换步骤
 * Fragment（共享 Activity 范围的 IncubationViewModel），出雏后显示只读结果并隐藏底部操作栏。
 */
public class IncubationActivity extends BaseActivity {

    private static final String TAG_STEP = "breed_inc_step";
    private static final String REQ_DISCARD = "breed_inc_discard";

    private BreedActivityIncubationBinding binding;
    private IncubationViewModel vm;
    private final BreedItemStepBinding[] steps = new BreedItemStepBinding[3];
    private int shownStep = -1;
    private boolean changed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(BreedInjector.factory(), IncubationViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.breed_activity_incubation);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        TopAppBarHelper.setupBack(binding.breedTopBar.baseToolbar, getString(R.string.breed_incubation_title));
        TopAppBarHelper.inflateMenu(binding.breedTopBar.baseToolbar, R.menu.breed_menu_more);
        binding.breedTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.breed_menu_more) {
                vm.onMoreClick();
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.cancelEdit()) {
                    return;
                }
                if (!vm.onBack()) {
                    finishWithResult();
                }
            }
        });
        binding.breedState.setOnRetryListener(v -> vm.retry());
        binding.breedState.setOnNoPermissionActionListener(v -> finish());
        vm.getUiState().observe(this, binding.breedState::render);

        for (int i = 0; i < 3; i++) {
            steps[i] = BreedItemStepBinding.inflate(getLayoutInflater(), binding.breedSteps, false);
            binding.breedSteps.addView(steps[i].getRoot());
            final int step = i + 1;
            ClickThrottle.setOnClickListener(steps[i].getRoot(), v -> vm.openStep(step));
        }
        steps[0].breedStepLineLeft.setVisibility(View.INVISIBLE);
        steps[2].breedStepLineRight.setVisibility(View.INVISIBLE);
        vm.getStepItems().observe(this, this::renderSteps);
        renderSource(null);
        vm.getDetail().observe(this, this::renderSource);
        vm.getCurrentStep().observe(this, this::showStep);

        binding.breedActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.breedActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCanSubmit().observe(this, c -> binding.breedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getScrollToError().observe(this, this::scrollTo);
        vm.getShowStepDetail().observe(this, step -> StepDetailSheet.open(getSupportFragmentManager(), step,
                getString(R.string.breed_step_detail_title, step == 1 ? getString(R.string.breed_step_set)
                        : step == 2 ? getString(R.string.breed_step_candle) : getString(R.string.breed_step_hatch))));
        vm.getShowMoreMenu().observe(this, this::showMoreMenu);
        vm.getNavigate().observe(this, this::navigate);
        vm.getRegistered().observe(this, v -> changed = true);
        vm.getNoPermission().observe(this, v -> finish());
        vm.getConfirmDiscard().observe(this, v -> BreedNav.showDiscard(this, REQ_DISCARD));
        BreedNav.listenDiscard(this, REQ_DISCARD);
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (!BreedNav.handleAction(this, actionId)) {
            vm.onMessageAction(actionId);
        }
    }

    /** 有登记成功时 setResult，上一页（B02）刷新 */
    private void finishWithResult() {
        if (changed) {
            setResult(RESULT_OK);
        }
        finish();
    }

    /** 养殖场标签（提醒用户当前已在西岭鸡场）；新建入孵时没有更多菜单 */
    private void renderSource(@Nullable com.zhimu.slmp.breed.model.vo.IncubationDetailVO d) {
        String farm = vm.farmName();
        binding.breedFarmTag.setText(farm);
        binding.breedFarmTag.setVisibility(farm == null ? View.GONE : View.VISIBLE);
        binding.breedTopBar.baseToolbar.getMenu().findItem(R.id.breed_menu_more).setVisible(d != null);
    }

    private void showStep(@Nullable Integer step) {
        if (step == null || step == shownStep) {
            return;
        }
        shownStep = step;
        Fragment f;
        int primary;
        switch (step) {
            case 1:
                f = new SetStepFragment();
                primary = R.string.breed_action_register_set;
                break;
            case 2:
                f = new CandleStepFragment();
                primary = R.string.breed_action_register_candle;
                break;
            case 3:
                f = new HatchStepFragment();
                primary = R.string.breed_action_register_hatch;
                break;
            case IncubationViewModel.STEP_EDIT:
                f = new EditStepFragment();
                primary = R.string.breed_action_save;
                break;
            default:
                f = new HatchResultFragment();
                primary = 0;
                break;
        }
        Fragment current = getSupportFragmentManager().findFragmentByTag(TAG_STEP);
        if (current == null || current.getClass() != f.getClass()) {
            getSupportFragmentManager().beginTransaction().replace(R.id.breed_step_container, f, TAG_STEP)
                    .commitAllowingStateLoss();
        }
        binding.breedActionBar.setVisibility(primary == 0 ? View.GONE : View.VISIBLE);
        if (primary != 0) {
            binding.breedActionBar.setPrimaryText(getString(primary));
        }
    }

    private void renderSteps(@Nullable List<StepItem> items) {
        if (items == null) {
            return;
        }
        int primary = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        int onPrimary = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnPrimary);
        int surface = MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorSurfaceContainerLowest);
        int outline = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOutline);
        int onSurface = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurface);
        int variant = MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorOnSurfaceVariant);
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 3 && i < items.size(); i++) {
            StepItem s = items.get(i);
            BreedItemStepBinding b = steps[i];
            GradientDrawable node = new GradientDrawable();
            node.setShape(GradientDrawable.OVAL);
            boolean done = s.getState() == StepItem.DONE;
            boolean current = s.getState() == StepItem.CURRENT;
            node.setColor(done ? primary : surface);
            if (!done) {
                node.setStroke((int) (2 * density), current ? primary : outline);
            }
            b.breedStepNode.setBackground(node);
            b.breedStepCheck.setVisibility(done ? View.VISIBLE : View.GONE);
            b.breedStepCheck.setColorFilter(onPrimary);
            b.breedStepNumber.setVisibility(done ? View.GONE : View.VISIBLE);
            b.breedStepNumber.setText(String.valueOf(s.getStep()));
            b.breedStepNumber.setTextColor(current ? primary : variant);
            b.breedStepName.setText(s.getName());
            b.breedStepName.setTextColor(current ? primary : onSurface);
            b.breedStepLine1.setText(s.getLine1());
            b.breedStepLine1.setVisibility(s.getLine1() == null ? View.INVISIBLE : View.VISIBLE);
            b.breedStepLine2.setText(s.getLine2());
            b.breedStepLine2.setVisibility(s.getLine2() == null ? View.INVISIBLE : View.VISIBLE);
            b.getRoot().setClickable(done);
        }
    }

    private void showMoreMenu(@Nullable List<String> menu) {
        View anchor = binding.breedTopBar.baseToolbar.findViewById(R.id.breed_menu_more);
        if (menu == null || anchor == null) {
            return;
        }
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor);
        if (menu.contains(IncubationViewModel.MENU_EDIT)) {
            b.add(R.id.breed_menu_inc_edit, getString(R.string.breed_menu_edit_incubation),
                    com.zhimu.slmp.base.R.drawable.ic_edit);
        }
        if (menu.contains(IncubationViewModel.MENU_LOG)) {
            b.add(R.id.breed_menu_inc_log, getString(R.string.breed_menu_log), com.zhimu.slmp.base.R.drawable.ic_history);
        }
        if (menu.contains(IncubationViewModel.MENU_CHANGE)) {
            b.addReview(R.id.breed_menu_inc_change, getString(R.string.breed_record_request_change),
                    com.zhimu.slmp.base.R.drawable.ic_edit_note);
        }
        b.setOnItemClickListener(id -> {
            if (id == R.id.breed_menu_inc_edit) {
                vm.onMenu(IncubationViewModel.MENU_EDIT);
            } else if (id == R.id.breed_menu_inc_log) {
                vm.onMenu(IncubationViewModel.MENU_LOG);
            } else if (id == R.id.breed_menu_inc_change) {
                vm.onMenu(IncubationViewModel.MENU_CHANGE);
            }
        }).show();
    }

    private void navigate(@Nullable NavRequest req) {
        if (req == null) {
            return;
        }
        if (BreedCodes.BIZ_INCUBATION.equals(req.getAction())) {
            long id = req.getExtras().getLong(RouterPath.EXTRA_BIZ_ID);
            startActivity(ChangeLogActivity.intent(this, BreedCodes.BIZ_INCUBATION, id, null));
            return;
        }
        Router.open(this, req.getAction(), req.getExtras());
    }

    private void scrollTo(@Nullable String field) {
        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_STEP);
        View target = null;
        if (f instanceof HatchStepFragment) {
            target = ((HatchStepFragment) f).viewFor(field);
        }
        if (target == null && BreedFormRules.F_CAPACITY.equals(field)) {
            return;
        }
        int y = target == null ? binding.breedStepContainer.getTop()
                : binding.breedStepContainer.getTop() + target.getTop();
        binding.breedScroll.post(() -> binding.breedScroll.smoothScrollTo(0, Math.max(0, y - 24)));
    }
}
