package com.zhimu.slmp.breed.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedFragmentListBinding;
import com.zhimu.slmp.breed.databinding.BreedViewAnimalHeaderBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.breed.ui.common.ListScrollHost;
import com.zhimu.slmp.breed.ui.common.SingleViewAdapter;

/**
 * B01“母猪档案”分段：临产横幅、状态 Chip、母猪分页列表（每页 20 条，底部线性进度条 / “已显示全部”）与三种空状态。
 * 已淘汰和死亡的母猪（EXITED 模式）只显示列表。
 */
public class AnimalListFragment extends BaseFragment {

    private BreedHomeViewModel vm;
    private BreedFragmentListBinding binding;
    private BreedViewAnimalHeaderBinding header;
    private AnimalPagingAdapter adapter;
    private FooterLoadStateAdapter footer;
    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BreedFragmentListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = obtainActivityViewModel(BreedInjector.factory(), BreedHomeViewModel.class);
        RecyclerView list = binding.breedList;
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        header = BreedViewAnimalHeaderBinding.inflate(getLayoutInflater(), list, false);
        adapter = new AnimalPagingAdapter(vm::onAnimalClick);
        footer = new FooterLoadStateAdapter(adapter::retry);
        list.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()), adapter.withLoadStateFooter(footer)));
        list.addItemDecoration(new GroupedListDecoration(requireContext()) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View child, int adapterPosition) {
                // 位置 0 为头部；查询上一项时 child 为 null，只能按位置判断
                return adapterPosition >= 1 && adapterPosition <= adapter.getItemCount();
            }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (getActivity() instanceof ListScrollHost) {
                    ((ListScrollHost) getActivity()).onListScrolled(dy);
                }
            }
        });
        adapter.addLoadStateListener(this::onLoadStates);

        binding.breedListRefresh.setOnRefreshListener(() -> vm.refreshAll());
        binding.breedListState.setOnRetryListener(v -> adapter.retry());
        binding.breedListState.showLoading();

        header.breedDueBanner.setAction(getString(R.string.breed_banner_view_due), v -> vm.onDueBannerClick());
        ClickThrottle.setOnClickListener(header.breedAnimalEmptyAction, v -> vm.onRegisterPicked(
                BreedHomeViewModel.PAGE_NEW_ANIMAL));
        bindChip(header.breedChipAll.getId(), BreedCodes.FILTER_ALL);
        bindChip(header.breedChipEmpty.getId(), BreedCodes.EMPTY);
        bindChip(header.breedChipMated.getId(), BreedCodes.MATED);
        bindChip(header.breedChipPregnant.getId(), BreedCodes.PREGNANT);
        bindChip(header.breedChipLactating.getId(), BreedCodes.LACTATING);

        vm.getExitedMode().observe(getViewLifecycleOwner(), exited -> {
            header.breedChipScroll.setVisibility(Boolean.TRUE.equals(exited) ? View.GONE : View.VISIBLE);
            renderEmpty();
        });
        vm.getDueBanner().observe(getViewLifecycleOwner(), text -> {
            boolean show = text != null && !Boolean.TRUE.equals(vm.getExitedMode().getValue());
            header.breedDueBanner.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                header.breedDueBanner.setNotice(null, text);
            }
        });
        vm.getOverview().observe(getViewLifecycleOwner(), this::renderChips);
        vm.getFilterStatus().observe(getViewLifecycleOwner(), status -> {
            int id = chipIdOf(status);
            if (header.breedChips.getCheckedChipId() != id) {
                header.breedChips.check(id);
            }
            binding.breedList.scrollToPosition(0);
        });
        vm.getPagingData().observe(getViewLifecycleOwner(), data -> adapter.submitData(getLifecycle(), data));
    }

    private void bindChip(int chipId, @NonNull String status) {
        header.breedChips.findViewById(chipId).setOnClickListener(v -> vm.setStatusFilter(status));
    }

    private int chipIdOf(@Nullable String status) {
        if (BreedCodes.EMPTY.equals(status)) {
            return header.breedChipEmpty.getId();
        }
        if (BreedCodes.MATED.equals(status)) {
            return header.breedChipMated.getId();
        }
        if (BreedCodes.PREGNANT.equals(status)) {
            return header.breedChipPregnant.getId();
        }
        if (BreedCodes.LACTATING.equals(status)) {
            return header.breedChipLactating.getId();
        }
        return header.breedChipAll.getId();
    }

    private void renderChips(@Nullable BreedingOverviewVO o) {
        BreedingOverviewVO.Counts c = o == null ? null : o.getCounts();
        header.breedChipEmpty.setText(label(R.string.breed_chip_empty, c == null ? null : c.getEmpty()));
        header.breedChipMated.setText(label(R.string.breed_chip_mated, c == null ? null : c.getMated()));
        header.breedChipPregnant.setText(label(R.string.breed_chip_pregnant, c == null ? null : c.getPregnant()));
        header.breedChipLactating.setText(label(R.string.breed_chip_lactating, c == null ? null : c.getLactating()));
        renderEmpty();
    }

    @NonNull
    private String label(int res, @Nullable Integer count) {
        return count == null ? getString(res) : getString(res) + " " + count;
    }

    private kotlin.Unit onLoadStates(@NonNull CombinedLoadStates states) {
        LoadState refresh = states.getRefresh();
        if (refresh instanceof LoadState.NotLoading) {
            loaded = true;
            binding.breedListRefresh.setRefreshing(false);
            binding.breedListState.showContent();
        } else if (refresh instanceof LoadState.Error) {
            binding.breedListRefresh.setRefreshing(false);
            if (adapter.getItemCount() == 0) {
                Throwable t = ((LoadState.Error) refresh).getError();
                binding.breedListState.render(UiState.error(t.getMessage(), null, 0));
            }
        } else if (!binding.breedListRefresh.isRefreshing() && adapter.getItemCount() > 0) {
            // 切换筛选：列表回到顶部
            binding.breedList.scrollToPosition(0);
        }
        footer.setHasItems(adapter.getItemCount() > 0);
        renderEmpty();
        return kotlin.Unit.INSTANCE;
    }

    private void renderEmpty() {
        if (header == null) {
            return;
        }
        boolean empty = loaded && adapter.getItemCount() == 0
                && adapter.getItemCount() == 0 && !(binding.breedListRefresh.isRefreshing());
        header.breedAnimalEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            header.breedAnimalEmptyText.setText(vm.emptyText());
            header.breedAnimalEmptyAction.setText(getString(R.string.breed_action_new_animal).replace("母猪",
                    vm.animalLabel()));
            header.breedAnimalEmptyAction.setVisibility(vm.showNewAnimalOnEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        header = null;
    }
}
