package com.zhimu.slmp.herd.ui.mypens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdFragmentMyPensBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.MyPenBatchVO;
import com.zhimu.slmp.herd.model.vo.MyPenItemVO;
import com.zhimu.slmp.herd.model.vo.TodoBriefVO;
import com.zhimu.slmp.herd.ui.common.HerdNav;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * L02 我的栏舍：员工底部导航“我的栏舍”（TabRegistry.MY_PENS）。登记完成（ITodoService.todoChanges）后自动刷新。
 */
public class MyPensFragment extends BaseFragment {

    private HerdFragmentMyPensBinding binding;
    private MyPensViewModel vm;
    private final CompositeDisposable viewDisposables = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = HerdFragmentMyPensBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /** 等列表数据到达后要滚动到的栏舍，0 表示没有 */
    private long pendingPenId;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = obtainViewModel(HerdInjector.factory(), MyPensViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);

        binding.herdTopBar.bind(requireActivity(), getViewLifecycleOwner());
        binding.herdTopBar.bindLiftOnScroll(binding.herdScroll);
        binding.herdState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(getViewLifecycleOwner(), binding.herdState::render);
        vm.getRefreshing().observe(getViewLifecycleOwner(),
                r -> binding.herdRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.herdRefresh.setOnRefreshListener(vm::refresh);
        vm.getCurrentFarmId().observe(getViewLifecycleOwner(), vm::onFarmChanged);

        MyPenAdapter adapter = new MyPenAdapter(new MyPenAdapter.Listener() {
            @Override
            public void onPenHeaderClick(@NonNull MyPenItemVO pen) {
                vm.onPenHeaderClick(pen);
            }

            @Override
            public void onBatchClick(@NonNull MyPenBatchVO batch) {
                vm.onBatchClick(batch);
            }

            @Override
            public void onTaskClick(@NonNull TodoBriefVO task, @NonNull MyPenItemVO pen) {
                vm.onTaskClick(task, pen);
            }
        });
        binding.herdPens.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.herdPens.setAdapter(adapter);
        vm.getPens().observe(getViewLifecycleOwner(), list -> adapter.submitList(list, this::scrollToPendingPen));
        // 今日首页点栏舍切到本标签时定位到该栏舍
        getParentFragmentManager().setFragmentResultListener(MainTabHost.argsResultKey(TabRegistry.MY_PENS),
                getViewLifecycleOwner(), (key, result) -> {
                    pendingPenId = result.getLong(RouterPath.EXTRA_PEN_ID, 0L);
                    scrollToPendingPen();
                });

        ClickThrottle.setOnClickListener(binding.herdScanBar, v -> vm.onScanClick());
        ClickThrottle.setOnClickListener(binding.herdBreedingEntry, v -> vm.onBreedingEntryClick());
        vm.getNavEvent().observe(getViewLifecycleOwner(), t -> HerdNav.open(requireActivity(), null, t));
        vm.getPickBatchEvent().observe(getViewLifecycleOwner(), pen ->
                PenBatchPickBottomSheet.newInstance(pen, getString(R.string.herd_my_pens_pick_batch))
                        .showSafely(getChildFragmentManager()));
        getChildFragmentManager().setFragmentResultListener(PenBatchPickBottomSheet.REQUEST_KEY,
                getViewLifecycleOwner(), (key, result) -> vm.onBatchPicked(PenBatchPickBottomSheet.getBatchId(result)));

        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            viewDisposables.add(todo.todoChanges()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(change -> vm.refresh(), t -> {
                    }));
        }
    }

    @Override
    public void onDestroyView() {
        viewDisposables.clear();
        binding = null;
        super.onDestroyView();
    }

    private void scrollToPendingPen() {
        scrollToPendingPen(0);
    }

    /** 列表提交后 RecyclerView 可能还没布局，取不到目标项时下一帧重试（最多 10 次） */
    private void scrollToPendingPen(int attempt) {
        if (binding == null || pendingPenId == 0L) {
            return;
        }
        MyPenAdapter adapter = (MyPenAdapter) binding.herdPens.getAdapter();
        if (adapter == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < adapter.getCurrentList().size(); i++) {
            if (adapter.getCurrentList().get(i).getPenId() == pendingPenId) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        final int position = index;
        binding.herdPens.post(() -> {
            if (binding == null || pendingPenId == 0L) {
                return;
            }
            View child = binding.herdPens.getLayoutManager() == null ? null
                    : binding.herdPens.getLayoutManager().findViewByPosition(position);
            if (child == null || binding.herdPens.isLayoutRequested()) {
                if (attempt < 10) {
                    binding.herdPens.postDelayed(() -> scrollToPendingPen(attempt + 1), 50);
                }
                return;
            }
            pendingPenId = 0L;
            binding.herdScroll.smoothScrollTo(0, binding.herdPens.getTop() + child.getTop());
        });
    }
}
