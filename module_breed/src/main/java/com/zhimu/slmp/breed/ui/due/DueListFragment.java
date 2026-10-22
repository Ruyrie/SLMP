package com.zhimu.slmp.breed.ui.due;

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
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedFragmentListBinding;
import com.zhimu.slmp.breed.databinding.BreedViewDueHeaderBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;
import com.zhimu.slmp.breed.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.breed.ui.common.ListScrollHost;
import com.zhimu.slmp.breed.ui.common.SingleViewAdapter;

/**
 * 待产 / 待出雏分段列表（Activity 范围的 BreedDueViewModel）：头部为提醒规则说明、跨养殖场横幅（B02）、
 * “登记入孵”按钮与空状态，下方为分组分页列表；员工只读版不渲染按钮，列表项不可点。
 */
public abstract class DueListFragment extends BaseFragment implements DuePagingAdapter.Listener {

    /** 嵌在 B01“待产”分段中（boolean）：不显示跨养殖场横幅 */
    public static final String ARG_EMBEDDED = "embedded";

    protected BreedDueViewModel vm;
    private BreedFragmentListBinding binding;
    private BreedViewDueHeaderBinding header;
    private DuePagingAdapter adapter;
    private FooterLoadStateAdapter footer;
    private boolean pendingScrollTop;

    /** true 为待出雏 */
    protected abstract boolean isHatch();

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
        vm = obtainActivityViewModel(BreedInjector.factory(), BreedDueViewModel.class);
        boolean embedded = getArguments() != null && getArguments().getBoolean(ARG_EMBEDDED);
        String tab = isHatch() ? BreedExtras.TAB_HATCH : BreedExtras.TAB_FARROW;

        RecyclerView list = binding.breedList;
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        header = BreedViewDueHeaderBinding.inflate(getLayoutInflater(), list, false);
        adapter = new DuePagingAdapter(isHatch(), this);
        footer = new FooterLoadStateAdapter(adapter::retry);
        list.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()), adapter.withLoadStateFooter(footer)));
        list.addItemDecoration(new GroupedListDecoration(requireContext()) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View child, int adapterPosition) {
                // 位置 0 为头部；分组标题不算列表项（查询上一项时 child 为 null，按位置判断）
                int pos = adapterPosition - 1;
                if (pos < 0 || pos >= adapter.getItemCount()) {
                    return false;
                }
                DueRow row = adapter.peek(pos);
                return row != null && !row.isHeader();
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
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                // 分组标题在首屏数据之后插入：原本在顶部时保持停在顶部
                if (positionStart == 0 && !list.canScrollVertically(-1)) {
                    list.scrollToPosition(0);
                }
            }
        });
        adapter.addLoadStateListener(this::onLoadStates);

        binding.breedListRefresh.setOnRefreshListener(() -> vm.refresh());
        binding.breedListState.setOnRetryListener(v -> adapter.retry());
        binding.breedListState.showLoading();

        vm.getRole().observe(getViewLifecycleOwner(), role -> header.breedDueRule.setText(
                com.zhimu.slmp.breed.util.BreedTextFormatter.ruleText(role, tab)));
        vm.getIsStaff().observe(getViewLifecycleOwner(), staff -> {
            adapter.setReadOnly(Boolean.TRUE.equals(staff));
            renderHeader();
        });
        if (!embedded) {
            vm.getSwitchBanner().observe(getViewLifecycleOwner(), text -> {
                header.breedDueSwitch.setVisibility(text == null ? View.GONE : View.VISIBLE);
                if (text != null) {
                    header.breedDueSwitch.setNotice(null, text);
                    header.breedDueSwitch.setAction(getString(R.string.breed_switch_action), v -> vm.onSwitchFarm());
                }
            });
        }
        vm.getOverview().observe(getViewLifecycleOwner(), o -> renderHeader());
        ClickThrottle.setOnClickListener(header.breedDueRegisterSet, v -> vm.onRegisterIncubation());
        if (isHatch()) {
            vm.getHatchDue().observe(getViewLifecycleOwner(), data -> adapter.submitData(getLifecycle(), data));
            vm.getHatchCounts().observe(getViewLifecycleOwner(), adapter::setCounts);
        } else {
            vm.getFarrowDue().observe(getViewLifecycleOwner(), data -> adapter.submitData(getLifecycle(), data));
            vm.getFarrowCounts().observe(getViewLifecycleOwner(), adapter::setCounts);
        }
    }

    private void renderHeader() {
        if (header == null) {
            return;
        }
        header.breedDueRegisterSet.setVisibility(isHatch() && vm.canRegisterIncubation() ? View.VISIBLE : View.GONE);
        boolean empty = loaded && !loading && adapter.getItemCount() == 0;
        header.breedDueEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        header.breedDueEmpty.setText(vm.emptyText(isHatch() ? BreedExtras.TAB_HATCH : BreedExtras.TAB_FARROW));
    }

    private boolean loading = true;
    private boolean loaded;

    private kotlin.Unit onLoadStates(@NonNull CombinedLoadStates states) {
        LoadState refresh = states.getRefresh();
        loading = refresh instanceof LoadState.Loading;
        if (refresh instanceof LoadState.NotLoading) {
            loaded = true;
            binding.breedListRefresh.setRefreshing(false);
            binding.breedListState.showContent();
            if (pendingScrollTop) {
                pendingScrollTop = false;
                binding.breedList.scrollToPosition(0);
            }
        } else if (refresh instanceof LoadState.Error) {
            binding.breedListRefresh.setRefreshing(false);
            if (adapter.getItemCount() == 0) {
                Throwable t = ((LoadState.Error) refresh).getError();
                binding.breedListState.render(UiState.error(t.getMessage(), null, 0));
            }
        }
        footer.setHasItems(adapter.getItemCount() > 0);
        renderHeader();
        return kotlin.Unit.INSTANCE;
    }

    /** B01 横幅“查看待产”：滚动到“今天”分组（列表顶部） */
    public void scrollToToday() {
        if (binding == null) {
            pendingScrollTop = true;
            return;
        }
        binding.breedList.scrollToPosition(0);
    }

    @Override
    public void onItemClick(@NonNull DueRow row) {
        vm.onItemClick(row);
    }

    @Override
    public void onRegisterFarrow(@NonNull FarrowDueItemVO item) {
        vm.onRegisterFarrow(item);
    }

    @Override
    public void onRegisterHatch(@NonNull HatchDueItemVO item) {
        vm.onRegisterHatch(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        header = null;
    }
}
