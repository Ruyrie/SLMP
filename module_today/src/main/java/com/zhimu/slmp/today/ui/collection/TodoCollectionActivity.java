package com.zhimu.slmp.today.ui.collection;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayActivityCollectionBinding;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.ui.common.ListStateAdapter;
import com.zhimu.slmp.today.ui.common.SectionAdapter;
import com.zhimu.slmp.today.ui.common.TodoListAdapter;
import com.zhimu.slmp.today.ui.common.TodoPagingAdapter;
import com.zhimu.slmp.today.util.TodayNavigator;

/** 只包含对应类型的事项，没有日历，也不混入普通待办。 */
public class TodoCollectionActivity extends BaseActivity implements TodoPagingAdapter.Listener {
    private TodoCollectionViewModel vm;
    private TodayActivityCollectionBinding binding;
    private final ListStateAdapter footer = new ListStateAdapter();
    private final SectionAdapter more = new SectionAdapter();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(TodayInjector.factory(), TodoCollectionViewModel.class);
        vm.init(getIntent().getStringExtra("filter"), getIntent().getStringExtra("scope"));
        binding = TodayActivityCollectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.topBar.baseToolbar,
                getString(vm.isOverdue() ? R.string.today_overdue_title : R.string.today_warning_title));
        TopAppBarHelper.bindLiftOnScroll(binding.topBar.baseAppBar, binding.list);
        TodoListAdapter adapter = new TodoListAdapter(this, vm.showsFarm());
        SectionAdapter count = new SectionAdapter();
        more.set(getString(R.string.today_load_more), "↓");
        more.setVisible(false);
        more.setOnClickListener(v -> vm.loadNext());
        footer.setOnActionClickListener(v -> vm.retry());
        LinearLayoutManager manager = new LinearLayoutManager(this);
        binding.list.setLayoutManager(manager);
        binding.list.setAdapter(new ConcatAdapter(count, adapter, footer, more));
        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy > 0 && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) vm.loadNext();
            }
        });
        binding.refresh.setOnRefreshListener(vm::refresh);
        vm.getItems().observe(this, adapter::submitList);
        vm.getTotal().observe(this, total -> count.set(vm.isOverdue() ? "已逾期" : "未解除预警", total + " 项"));
        vm.getNavigation().observe(this, target -> TodayNavigator.open(this, target));
        vm.getListState().observe(this, state -> {
            boolean loading = state.is(UiState.Status.LOADING);
            binding.refresh.setRefreshing(loading);
            more.setVisible(!loading && !state.is(UiState.Status.ERROR) && vm.hasMore());
            if (loading) footer.setState(ListStateAdapter.State.loading());
            else if (state.is(UiState.Status.ERROR)) footer.setState(ListStateAdapter.State.error(
                    "加载失败", state.getTitle(), getString(com.zhimu.slmp.base.R.string.base_action_retry)));
            else if (state.is(UiState.Status.EMPTY)) footer.setState(ListStateAdapter.State.empty(
                    com.zhimu.slmp.base.R.drawable.ic_task_alt,
                    getString(vm.isOverdue() ? R.string.today_overdue_empty : R.string.today_warning_empty), null, null));
            else footer.setState(ListStateAdapter.State.none());
        });
    }

    @Override protected void onResume() { super.onResume(); if (vm != null) vm.refreshForFarm(
            com.zhimu.slmp.common.di.CoreInjector.get(this).farmContext().farmIdOrZero()); }
    @Override public void onTodoClick(@NonNull TodoItemVO item) { vm.open(item); }
    @Override public void onTodoLongClick(@NonNull TodoItemVO item) { vm.openDetail(item); }
}
