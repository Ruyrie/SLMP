package com.zhimu.slmp.health.ui.disinfectplan;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthActivityDisinfectPlanBinding;
import com.zhimu.slmp.health.databinding.HealthViewListNoteBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.DisinfectPlanVO;
import com.zhimu.slmp.health.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.SingleViewAdapter;

import java.util.Objects;

import kotlin.Unit;

/**
 * D06 消毒计划（DisinfectPlanActivity）：入口 D09 顶部栏 event_note、L01 生产页消毒入口。员工不开放（U03 无权查看）。
 */
public class DisinfectPlanActivity extends BaseActivity {

    private HealthActivityDisinfectPlanBinding binding;
    private HealthViewListNoteBinding note;
    private DisinfectPlanListViewModel vm;
    private DisinfectPlanAdapter adapter;
    private boolean listEmpty;

    private final ActivityResultLauncher<Intent> childLauncher = Router.registerForResult(this, r -> {
        if (r.getResultCode() == RESULT_OK) {
            vm.refresh();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HealthActivityDisinfectPlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.healthList.setLayoutManager(new LinearLayoutManager(this));
        note = HealthViewListNoteBinding.inflate(getLayoutInflater(), binding.healthList, false);
        vm = obtainViewModel(HealthInjector.factory(), DisinfectPlanListViewModel.class);
        observeBase(vm);
        HealthNav.finishOnFarmSwitch(this);

        TopAppBarHelper.setupBack(binding.healthToolbar, getString(R.string.health_d06_title));
        binding.healthToolbar.inflateMenu(R.menu.health_menu_tasks);
        TopAppBarHelper.tintMenu(binding.healthToolbar);
        binding.healthToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.health_menu_tasks) {
                vm.onTasksClick();
                return true;
            }
            return false;
        });

        adapter = new DisinfectPlanAdapter(new DisinfectPlanAdapter.Listener() {
            @Override
            public void onPause(@NonNull DisinfectPlanVO p) {
                vm.onPauseClick(p);
            }

            @Override
            public void onResume(@NonNull DisinfectPlanVO p) {
                vm.resume(p);
            }

            @Override
            public void onEdit(@NonNull DisinfectPlanVO p) {
                vm.onEdit(p);
            }
        });
        FooterLoadStateAdapter footer = new FooterLoadStateAdapter(adapter::retry);
        note.healthNote.setText(R.string.health_d06_note);
        note.healthEmptyAction.setText(R.string.health_new_plan);
        ClickThrottle.setOnClickListener(note.healthEmptyAction, v -> vm.onFabClick());
        adapter.addLoadStateListener(states -> {
            footer.setLoadState(states.getAppend());
            if (states.getRefresh() instanceof LoadState.Error) {
                vm.onListError(((LoadState.Error) states.getRefresh()).getError());
            }
            listEmpty = states.getRefresh() instanceof LoadState.NotLoading && adapter.getItemCount() == 0;
            renderEmpty();
            binding.healthRefresh.setRefreshing(states.getRefresh() instanceof LoadState.Loading
                    && adapter.getItemCount() > 0);
            return Unit.INSTANCE;
        });
        binding.healthList.setAdapter(new ConcatAdapter(adapter, footer, new SingleViewAdapter(note.getRoot())));
        binding.healthList.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int position) {
                return position < adapter.getItemCount();
            }
        });
        binding.healthList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                binding.healthAppBar.setLifted(rv.canScrollVertically(-1));
                if (dy > 4) {
                    binding.healthFab.shrink();
                } else if (dy < -4) {
                    binding.healthFab.extend();
                }
            }
        });

        vm.getUiState().observe(this, state -> {
            binding.healthState.render(state);
            boolean blocked = state != null && !state.is(com.zhimu.slmp.base.mvvm.UiState.Status.CONTENT);
            binding.healthFab.setVisibility(blocked ? View.GONE : View.VISIBLE);
            binding.healthChipScroll.setVisibility(blocked ? View.GONE : View.VISIBLE);
        });
        binding.healthState.setOnRetryListener(v -> vm.retry());
        binding.healthRefresh.setOnRefreshListener(vm::refresh);
        if (!vm.isStaff()) {
            vm.getPlans().observe(this, data -> adapter.submitData(getLifecycle(), data));
        }
        vm.getStatusFilter().observe(this, s -> renderChips());
        vm.getTabCounts().observe(this, c -> renderChips());
        vm.getBusyPlanId().observe(this, adapter::setBusyId);

        ClickThrottle.setOnClickListener(binding.healthFab, v -> vm.onFabClick());
        vm.getShowPauseSheet().observe(this, p -> PausePlanBottomSheet.newInstance(p)
                .showSafely(getSupportFragmentManager()));
        vm.getClosePauseSheet().observe(this, v -> {
            androidx.fragment.app.Fragment f = getSupportFragmentManager()
                    .findFragmentByTag(PausePlanBottomSheet.class.getName());
            if (f instanceof PausePlanBottomSheet) {
                ((PausePlanBottomSheet) f).dismissAllowingStateLoss();
            }
        });
        vm.getOpenEdit().observe(this, id -> {
            Bundle extras = new Bundle();
            if (id != null) {
                extras.putLong(RouterPath.EXTRA_PLAN_ID, id);
            }
            HealthNav.open(this, childLauncher, RouterPath.D07, extras);
        });
        vm.getOpenTasks().observe(this, v -> Router.open(this, RouterPath.D09));
    }

    private void renderChips() {
        String current = vm.getStatusFilter().getValue();
        if (binding.healthFilterChips.getChildCount() == 0) {
            LayoutInflater inflater = getLayoutInflater();
            for (String status : DisinfectPlanListViewModel.STATUSES) {
                Chip chip = (Chip) inflater.inflate(R.layout.health_item_filter_chip, binding.healthFilterChips, false);
                chip.setTag(status);
                ClickThrottle.setOnClickListener(chip, v -> vm.selectStatus(status));
                binding.healthFilterChips.addView(chip);
            }
        }
        for (int i = 0; i < binding.healthFilterChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.healthFilterChips.getChildAt(i);
            String status = (String) chip.getTag();
            chip.setText(DisinfectPlanListViewModel.statusLabel(status) + " " + vm.countOf(status));
            chip.setChecked(Objects.equals(status, current));
        }
        renderEmpty();
    }

    /** 没有计划：“还没有消毒计划……”+“新建计划”；筛选后为空：“没有启用中的计划。” */
    private void renderEmpty() {
        String status = vm.getStatusFilter().getValue();
        boolean all = DisinfectPlanListViewModel.ALL.equals(status);
        note.healthEmpty.setVisibility(listEmpty ? View.VISIBLE : View.GONE);
        note.healthEmptyText.setText(all ? getString(R.string.health_d06_empty)
                : getString(R.string.health_d06_empty_filter, DisinfectPlanListViewModel.statusLabel(
                status == null ? DisinfectPlanListViewModel.ALL : status)));
        note.healthEmptyAction.setVisibility(listEmpty && all ? View.VISIBLE : View.GONE);
        note.healthNote.setVisibility(listEmpty ? View.GONE : View.VISIBLE);
    }
}
