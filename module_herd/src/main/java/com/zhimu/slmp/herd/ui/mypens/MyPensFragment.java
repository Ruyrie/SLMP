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
        vm.getPens().observe(getViewLifecycleOwner(), adapter::submitList);

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
}
