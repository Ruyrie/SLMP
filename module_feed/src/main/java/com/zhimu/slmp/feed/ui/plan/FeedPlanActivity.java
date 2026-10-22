package com.zhimu.slmp.feed.ui.plan;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedActivityPlanBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.model.vo.FeedPlanBatchOptionVO;
import com.zhimu.slmp.feed.ui.common.FeedErrors;
import com.zhimu.slmp.feed.ui.common.FeedViews;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.List;

/**
 * G03 饲喂方案（所有者、场长）。保存后留在本页；返回时若保存过方案 setResult(RESULT_OK) 让 G01 刷新。
 */
public class FeedPlanActivity extends BaseActivity {

    private static final String REQ_TIME = "feed_plan_time";
    private static final String REQ_DISCARD = "feed_plan_discard";
    private static final String REQ_BATCH = "feed_plan_batch";
    private static final String ARG_STAGE = "stage";
    private static final String ARG_MEAL = "meal";

    private FeedActivityPlanBinding binding;
    private FeedPlanViewModel vm;
    private FeedPlanStageAdapter stageAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.feed_activity_plan);
        vm = obtainViewModel(FeedInjector.factory(), FeedPlanViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        TopAppBarHelper.setupBack(binding.feedTopBar.baseToolbar, getString(R.string.feed_plan_title));
        binding.feedTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.feedTopBar.baseToolbar.inflateMenu(R.menu.feed_menu_plan);
        TopAppBarHelper.tintMenu(binding.feedTopBar.baseToolbar);
        binding.feedTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.feed_menu_history) {
                vm.onHistoryClick();
                return true;
            }
            return false;
        });
        TopAppBarHelper.bindLiftOnScroll(binding.feedTopBar.baseAppBar, binding.feedPlanScroll);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finishWithResult();
                }
            }
        });

        binding.feedState.setOnRetryListener(v -> vm.retry());
        binding.feedState.setNoPermissionTitle(getString(R.string.feed_plan_no_permission_title));
        vm.getUiState().observe(this, this::renderState);

        stageAdapter = new FeedPlanStageAdapter(binding.feedPlanStages, new FeedPlanStageAdapter.Listener() {
            @Override
            public void onExpand(int index) {
                vm.expandStage(index);
            }

            @Override
            public void onFeedSelected(int index, long feedId) {
                vm.setFeed(index, feedId);
            }

            @Override
            public void onDailyQtyChanged(int index, @NonNull String text) {
                vm.setDailyQty(index, text);
            }

            @Override
            public void onMealsSelected(int index, int meals) {
                vm.setMeals(index, meals);
            }

            @Override
            public void onTimeClick(int index, int mealIndex) {
                vm.onTimeChipClick(index, mealIndex);
            }
        });
        vm.getStages().observe(this, s -> renderStages());
        vm.getExpandedIndex().observe(this, i -> renderStages());
        vm.getStageErrors().observe(this, e -> renderStages());
        vm.getFeedOptions().observe(this, f -> renderStages());
        vm.getThresholdText().observe(this, t -> binding.feedPlanThreshold.setSubtitle(t));
        binding.feedPlanThreshold.setOnClickListener(v -> vm.onThresholdEntryClick());
        binding.feedPlanApply.setOnClickListener(v -> vm.applyStandard());

        binding.feedActionBar.setOnPrimaryClickListener(v -> vm.save());
        vm.getSaving().observe(this, s -> binding.feedActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));

        vm.getScrollToError().observe(this, this::scrollToStage);
        vm.getShowTimePicker().observe(this, this::showTimePicker);
        vm.getShowThresholdSheet().observe(this, v -> FeedThresholdSheet.newInstance(
                getString(R.string.feed_plan_threshold_title)).showSafely(getSupportFragmentManager()));
        vm.getShowHistorySheet().observe(this, v -> FeedPlanHistorySheet.newInstance(
                getString(R.string.feed_plan_history_title)).showSafely(getSupportFragmentManager()));
        vm.getShowDiscardDialog().observe(this, v -> FeedViews.showDiscard(this, REQ_DISCARD));
        vm.getShowBatchSheet().observe(this, v -> showBatchSheet());

        getSupportFragmentManager().setFragmentResultListener(REQ_TIME, this, (key, result) -> {
            Bundle extras = DateTimePicker.getExtras(result);
            String time = DateTimePicker.getTime(result);
            if (extras != null && time != null) {
                vm.setMealTime(extras.getInt(ARG_STAGE), extras.getInt(ARG_MEAL), time);
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                finishWithResult();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_BATCH, this, (key, result) -> {
            String value = OptionSheetDialogFragment.getValue(result);
            if (value != null) {
                vm.load(Long.parseLong(value));
            }
        });
    }

    private void finishWithResult() {
        if (vm.isSaved()) {
            setResult(Activity.RESULT_OK);
        }
        finish();
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (FeedPlanViewModel.ACTION_UNDO.equals(actionId)) {
            vm.undoApply();
        } else if (FeedErrors.ACTION_REFRESH.equals(actionId)) {
            vm.retry();
        }
    }

    private void renderState(@Nullable UiState state) {
        binding.feedState.render(state);
        boolean content = state == null || state.is(UiState.Status.CONTENT);
        binding.feedActionBar.setVisibility(content ? View.VISIBLE : View.GONE);
        binding.feedTopBar.baseToolbar.getMenu().findItem(R.id.feed_menu_history).setVisible(content);
        if (state != null && state.is(UiState.Status.NO_PERMISSION) && state.getErrorCode() == ErrorCodes.ROLE_FORBIDDEN) {
            binding.feedState.showNoPermission(getString(R.string.feed_plan_no_permission_title),
                    getString(R.string.feed_plan_no_permission_message));
        }
    }

    private void renderStages() {
        Integer expanded = vm.getExpandedIndex().getValue();
        stageAdapter.submit(vm.getStages().getValue(), expanded == null ? -1 : expanded,
                vm.getStageErrors().getValue(), vm.getFeedOptions().getValue(), vm.currentCount());
    }

    /** 展开出错阶段（300 ms）后滚动到该卡 */
    private void scrollToStage(int index) {
        binding.feedPlanScroll.postDelayed(() -> {
            View v = stageAdapter.viewAt(index);
            if (v != null) {
                int y = binding.feedPlanStages.getTop() + v.getTop();
                binding.feedPlanScroll.smoothScrollTo(0, Math.max(0, y - getResources().getDimensionPixelSize(
                        com.zhimu.slmp.base.R.dimen.base_space_4)));
            }
        }, 320);
    }

    /** MaterialTimePicker（24 小时制） */
    private void showTimePicker(@NonNull int[] pos) {
        List<FeedStageForm> stages = vm.getStages().getValue();
        String current = null;
        if (stages != null && pos[0] < stages.size() && pos[1] < stages.get(pos[0]).mealTimes.size()) {
            current = stages.get(pos[0]).mealTimes.get(pos[1]);
        }
        Bundle extras = new Bundle();
        extras.putInt(ARG_STAGE, pos[0]);
        extras.putInt(ARG_MEAL, pos[1]);
        DateTimePicker.time(REQ_TIME)
                .title(getString(R.string.feed_plan_meal_times))
                .time(current)
                .extras(extras)
                .show(getSupportFragmentManager());
    }

    private void showBatchSheet() {
        List<FeedPlanBatchOptionVO> list = vm.getBatchOptions().getValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQ_BATCH)
                .title(getString(R.string.feed_batch_sheet_title));
        for (FeedPlanBatchOptionVO o : list) {
            OptionSheetDialogFragment.Option option = new OptionSheetDialogFragment.Option(
                    String.valueOf(o.getBatchId()), FeedFormat.orEmpty(o.getBatchNo()),
                    FeedFormat.join(o.getPenNames(), o.getBreedName(),
                            o.getDayAge() == null ? null : o.getDayAge() + " 日龄"));
            if (!o.isHasPlan()) {
                option.tag(getString(R.string.feed_plan_not_set), StatusTagView.NEUTRAL);
            }
            b.option(option);
        }
        b.build().showSafely(getSupportFragmentManager());
    }
}
