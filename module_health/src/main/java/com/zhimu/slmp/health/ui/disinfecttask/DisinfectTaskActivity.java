package com.zhimu.slmp.health.ui.disinfecttask;

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
import com.zhimu.slmp.health.databinding.HealthActivityDisinfectTaskBinding;
import com.zhimu.slmp.health.databinding.HealthViewListNoteBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.DisinfectTaskVO;
import com.zhimu.slmp.health.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.SingleViewAdapter;

import java.util.Objects;

import kotlin.Unit;

/**
 * D09 消毒任务（DisinfectTaskActivity）：入口 L01 生产页、D06 顶部栏 task_alt、T04 消毒类待办、推送 DISINFECT_OVERDUE。
 */
public class DisinfectTaskActivity extends BaseActivity {

    private HealthActivityDisinfectTaskBinding binding;
    private HealthViewListNoteBinding note;
    private DisinfectTaskViewModel vm;
    private DisinfectTaskAdapter adapter;
    private boolean listEmpty;

    private final ActivityResultLauncher<Intent> childLauncher = Router.registerForResult(this, r -> {
        if (r.getResultCode() == RESULT_OK) {
            vm.refresh();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HealthActivityDisinfectTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.healthList.setLayoutManager(new LinearLayoutManager(this));
        note = HealthViewListNoteBinding.inflate(getLayoutInflater(), binding.healthList, false);
        vm = obtainViewModel(HealthInjector.factory(), DisinfectTaskViewModel.class);
        observeBase(vm);
        HealthNav.finishOnFarmSwitch(this);

        TopAppBarHelper.setupBack(binding.healthToolbar, getString(R.string.health_d09_title));
        if (!vm.isStaff()) {
            binding.healthToolbar.inflateMenu(R.menu.health_menu_plans);
            TopAppBarHelper.tintMenu(binding.healthToolbar);
        }
        binding.healthToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.health_menu_plans) {
                vm.onPlansClick();
                return true;
            }
            return false;
        });

        adapter = new DisinfectTaskAdapter(new DisinfectTaskAdapter.Listener() {
            @Override
            public void onClick(@NonNull DisinfectTaskVO task) {
                vm.onTaskClick(task);
            }

            @Override
            public void onBackfill(@NonNull DisinfectTaskVO task) {
                vm.onTaskClick(task);
            }
        });
        adapter.setStaff(vm.isStaff());
        FooterLoadStateAdapter footer = new FooterLoadStateAdapter(adapter::retry);
        note.healthEmptyAction.setText(R.string.health_new_plan);
        ClickThrottle.setOnClickListener(note.healthEmptyAction, v -> vm.onPlansClick());
        adapter.addLoadStateListener(states -> {
            footer.setLoadState(states.getAppend());
            listEmpty = states.getRefresh() instanceof LoadState.NotLoading && adapter.getItemCount() == 0;
            renderEmpty();
            binding.healthRefresh.setRefreshing(states.getRefresh() instanceof LoadState.Loading
                    && adapter.getItemCount() > 0);
            if (states.getRefresh() instanceof LoadState.Error && adapter.getItemCount() > 0) {
                showMessage(getString(R.string.health_refresh_failed));
            }
            return Unit.INSTANCE;
        });
        // 分组标题在首屏数据之后插入到顶部；原本在顶部时保持停在顶部
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart == 0 && !binding.healthList.canScrollVertically(-1)) {
                    binding.healthList.scrollToPosition(0);
                }
            }
        });
        binding.healthList.setAdapter(new ConcatAdapter(adapter, footer, new SingleViewAdapter(note.getRoot())));
        binding.healthList.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int position) {
                return adapter.isTask(position);
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

        binding.healthState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.healthState::render);
        binding.healthRefresh.setOnRefreshListener(vm::refresh);
        vm.getTasks().observe(this, data -> adapter.submitData(getLifecycle(), data));
        vm.getStatusFilter().observe(this, s -> renderChips());
        vm.getTabCounts().observe(this, counts -> {
            adapter.setCounts(counts);
            renderChips();
        });
        vm.getEmptyText().observe(this, t -> renderEmpty());

        ClickThrottle.setOnClickListener(binding.healthFab, v -> vm.onFabClick());
        vm.getOpenRegister().observe(this, t -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_TASK_ID, t.getId());
            extras.putLong(RouterPath.EXTRA_PEN_ID, t.getPenId());
            extras.putBoolean(HealthExtras.EXTRA_BACKFILL, DisinfectTaskViewModel.OVERDUE.equals(t.getStatus()));
            HealthNav.open(this, childLauncher, RouterPath.D08, extras);
        });
        vm.getOpenRecord().observe(this, id -> {
            Bundle extras = new Bundle();
            extras.putLong(HealthExtras.EXTRA_RECORD_ID, id);
            HealthNav.open(this, childLauncher, RouterPath.D08, extras);
        });
        vm.getOpenTemp().observe(this, v -> {
            Bundle extras = new Bundle();
            extras.putString(HealthExtras.EXTRA_RECORD_TYPE, HealthExtras.RECORD_EMPTY_PEN);
            HealthNav.open(this, childLauncher, RouterPath.D08, extras);
        });
        vm.getOpenPlans().observe(this, v -> Router.open(this, RouterPath.D06));
    }

    /** “全部”不带数字，其余“待执行 4”“已执行 2”“已逾期 1” */
    private void renderChips() {
        String current = vm.getStatusFilter().getValue();
        if (binding.healthFilterChips.getChildCount() == 0) {
            LayoutInflater inflater = getLayoutInflater();
            for (String status : DisinfectTaskViewModel.STATUSES) {
                Chip chip = (Chip) inflater.inflate(R.layout.health_item_filter_chip, binding.healthFilterChips, false);
                chip.setTag(status);
                ClickThrottle.setOnClickListener(chip, v -> vm.selectStatus(status));
                binding.healthFilterChips.addView(chip);
            }
        }
        for (int i = 0; i < binding.healthFilterChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.healthFilterChips.getChildAt(i);
            String status = (String) chip.getTag();
            chip.setText(DisinfectTaskViewModel.ALL.equals(status) ? DisinfectTaskViewModel.statusLabel(status)
                    : DisinfectTaskViewModel.statusLabel(status) + " " + vm.countOf(status));
            chip.setChecked(Objects.equals(status, current));
        }
    }

    private void renderEmpty() {
        String status = vm.getStatusFilter().getValue();
        boolean all = DisinfectTaskViewModel.ALL.equals(status);
        note.healthEmpty.setVisibility(listEmpty ? View.VISIBLE : View.GONE);
        note.healthEmptyText.setText(vm.getEmptyText().getValue());
        note.healthEmptyAction.setVisibility(listEmpty && all && !vm.isStaff() ? View.VISIBLE : View.GONE);
    }
}
