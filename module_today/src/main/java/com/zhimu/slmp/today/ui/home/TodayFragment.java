package com.zhimu.slmp.today.ui.home;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayFragmentTodayBinding;
import com.zhimu.slmp.today.databinding.TodayItemHomeHeaderBinding;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.vo.HomeVO;
import com.zhimu.slmp.today.model.vo.MyPenVO;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.ui.common.ListStateAdapter;
import com.zhimu.slmp.today.ui.common.SingleViewAdapter;
import com.zhimu.slmp.today.ui.common.TodoPagingAdapter;
import com.zhimu.slmp.today.ui.detail.TodoDetailActivity;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.Texts;
import com.zhimu.slmp.today.util.TodayNavigator;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

/**
 * T01 今日首页（底部导航“今日”，TabRegistry.TODAY）：一级页顶部栏、问候区、按角色的指标卡与 AI 横幅 / 我负责的栏舍、
 * “今天和已逾期”分页列表（ConcatAdapter：头部 + 待办 + 列表状态）、扩展 FAB“登记”打开 T02。
 * 长按待办以 Activity Result 打开 T04；回到前台刷新未读数与首页；首次进入后先说明再申请通知权限（Android 13 及以上）。
 */
public class TodayFragment extends BaseFragment implements TodoPagingAdapter.Listener {

    private static final String REQ_NOTIFY = "today_notify_permission";

    private TodayViewModel vm;
    @Nullable
    private TodayFragmentTodayBinding binding;
    @Nullable
    private TodayItemHomeHeaderBinding header;
    private TodoPagingAdapter todoAdapter;
    private ListStateAdapter footer;

    private final ActivityResultLauncher<Intent> detailLauncher = Router.registerForResult(this, result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null
                && result.getData().getBooleanExtra(TodoDetailActivity.RESULT_IGNORED, false)) {
            showMessage(getString(R.string.today_ignored_toast));
            vm.refresh();
        }
    });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> vm.onNotifyPermissionResult(granted));

    private final DefaultLifecycleObserver foregroundObserver = new DefaultLifecycleObserver() {
        private boolean first = true;

        @Override
        public void onStart(@NonNull LifecycleOwner owner) {
            if (first) {
                first = false;
                return;
            }
            if (vm != null) {
                vm.onAppForeground();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        vm = obtainViewModel(TodayInjector.factory(), TodayViewModel.class);
        binding = TodayFragmentTodayBinding.inflate(inflater, container, false);
        binding.setVm(vm);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TodayFragmentTodayBinding b = binding;
        if (b == null) {
            return;
        }
        observeBase(vm);
        LifecycleOwner owner = getViewLifecycleOwner();
        b.setLifecycleOwner(owner);

        b.topBar.bind(requireActivity(), owner);
        b.topBar.bindLiftOnScroll(b.list);

        header = TodayItemHomeHeaderBinding.inflate(getLayoutInflater(), b.list, false);
        header.setLifecycleOwner(owner);
        header.setVm(vm);
        bindHeaderClicks(header);

        todoAdapter = new TodoPagingAdapter(this, false);
        footer = new ListStateAdapter();
        footer.setOnActionClickListener(v -> onFooterAction());
        ConcatAdapter concat = new ConcatAdapter(new SingleViewAdapter(header.getRoot()), todoAdapter, footer);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setItemAnimator(null);
        b.list.setAdapter(concat);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 4 && b.fab.isExtended()) {
                    b.fab.shrink();
                } else if (dy < -4 && !b.fab.isExtended()) {
                    b.fab.extend();
                }
            }
        });
        b.refresh.setOnRefreshListener(vm::refresh);
        b.stateLayout.setOnRetryListener(v -> vm.retry());

        todoAdapter.addLoadStateListener(states -> {
            updateFooter(states);
            return Unit.INSTANCE;
        });
        vm.getTodoPaging().observe(owner, data -> todoAdapter.submitData(owner.getLifecycle(), data));
        vm.getManagerView().observe(owner, manager -> updateFooter(null));
        vm.getHasPen().observe(owner, has -> updateFooter(null));
        vm.getFarmBlocked().observe(owner, blocked -> updateFooter(null));
        vm.getMyPens().observe(owner, this::renderPens);
        vm.getHome().observe(owner, this::renderFarmNotice);
        vm.getNavEvent().observe(owner, this::open);
        vm.getLeavingTodoId().observe(owner, id -> {
            if (id != null && binding != null) {
                todoAdapter.fadeOut(binding.list, id, 1, () -> vm.removeTodoLocally(id));
            }
        });
        vm.getOpenQuickRegister().observe(owner, v -> QuickRegisterSheet.show(getChildFragmentManager()));
        vm.getSwitchTab().observe(owner, tab -> {
            if (tab != null && requireActivity() instanceof MainTabHost) {
                ((MainTabHost) requireActivity()).selectTab(tab);
            }
        });
        vm.getAskNotifyPermission().observe(owner, v -> showNotifyGuide());
        vm.getUiState().observe(owner, state -> {
            if (state != null && state.is(UiState.Status.CONTENT)) {
                vm.checkNotifyGuide(needNotifyPermission());
            }
        });
        getChildFragmentManager().setFragmentResultListener(REQ_NOTIFY, owner, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
        CoreInjector.get(requireContext()).farmContext().getContext().observe(owner, this::onFarmContext);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(foregroundObserver);
    }

    private void bindHeaderClicks(@NonNull TodayItemHomeHeaderBinding h) {
        h.cardStock.setOnClickListener(v -> vm.onMetricClick(TodayViewModel.CARD_STOCK));
        h.cardPens.setOnClickListener(v -> vm.onMetricClick(TodayViewModel.CARD_PENS));
        h.cardToday.setOnClickListener(v -> vm.onMetricClick(TodayViewModel.CARD_TODAY));
        h.cardWarning.setOnClickListener(v -> vm.onMetricClick(TodayViewModel.CARD_WARNING));
        h.cardOverdue.setOnClickListener(v -> vm.onMetricClick(TodayViewModel.CARD_OVERDUE));
        h.todoHeader.setOnActionClickListener(v -> vm.onCalendarClick());
    }

    private void onFarmContext(@Nullable FarmContextVO ctx) {
        vm.onFarmSwitched(ctx);
    }

    // ===================== 渲染 =====================

    private void renderFarmNotice(@Nullable HomeVO home) {
        if (header == null || home == null) {
            return;
        }
        String name = home.getFarm() == null || home.getFarm().getName() == null ? "" : home.getFarm().getName();
        header.farmNotice.setText(getString(R.string.today_home_blocked, name));
        header.farmNotice.setAction(getString(R.string.today_home_view_audit), v -> vm.onViewAuditClick());
    }

    private void renderPens(@Nullable List<MyPenVO> pens) {
        if (header == null) {
            return;
        }
        header.pensContainer.removeAllViews();
        List<MyPenVO> list = pens == null ? new ArrayList<>() : pens;
        String unit = countUnitText();
        for (MyPenVO pen : list) {
            ListRowView row = new ListRowView(requireContext());
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_fence, ListRowView.TONE_SECONDARY);
            row.setTitle(pen.getPenName());
            row.setSubtitle(penSubtitle(pen, unit));
            row.setSubtitleMaxLines(2);
            if (pen.isInWithdrawal()) {
                row.setStatusTag(getString(R.string.today_pen_withdrawal), StatusTagView.WARNING, 0);
                row.setShowChevron(false);
            } else {
                row.setStatusTag(null, StatusTagView.NEUTRAL, 0);
                row.setShowChevron(true);
            }
            ClickThrottle.setOnClickListener(row, v -> vm.onPenClick(pen));
            header.pensContainer.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        header.pensContainer.updatePositions();
        boolean staffNoPen = list.isEmpty() && !Boolean.TRUE.equals(vm.getManagerView().getValue())
                && vm.getHome().getValue() != null;
        header.noPen.setVisibility(staffNoPen ? View.VISIBLE : View.GONE);
        // 员工还没有负责栏舍时不显示待办列表
        header.todoHeader.setVisibility(staffNoPen || Boolean.TRUE.equals(vm.getFarmBlocked().getValue())
                || !Boolean.TRUE.equals(vm.getHasPen().getValue()) ? View.GONE : View.VISIBLE);
        updateFooter(null);
    }

    /** “ZM2610-031 · 存栏 200 头 · 待办 5 项”；没有在栏批次时“待办 2 项 · 消毒、巡检” */
    @NonNull
    private String penSubtitle(@NonNull MyPenVO pen, @NonNull String unit) {
        List<String> parts = new ArrayList<>();
        if (!pen.getBatches().isEmpty()) {
            List<String> nos = new ArrayList<>();
            int head = 0;
            for (MyPenVO.Batch batch : pen.getBatches()) {
                nos.add(batch.getBatchNo());
                head += batch.getHeadCount() == null ? 0 : batch.getHeadCount();
            }
            parts.add(Texts.join("、", nos));
            parts.add(getString(R.string.today_home_metric_stock) + " " + NumberFormatter.formatQuantity(head, unit));
            parts.add(getString(R.string.today_pen_todo_count, pen.getTodoCount()));
        } else {
            parts.add(getString(R.string.today_pen_todo_count, pen.getTodoCount()));
            if (!pen.getTodoNames().isEmpty()) {
                parts.add(Texts.join("、", pen.getTodoNames()));
            }
        }
        return Texts.join(Texts.DOT, parts);
    }

    @NonNull
    private String countUnitText() {
        FarmContextVO ctx = CoreInjector.get(requireContext()).farmContext().current();
        return ctx != null && "BIRD".equals(ctx.getCountUnit()) ? "只" : "头";
    }

    /** 列表区：加载更多圈、今天没有待办、还没有栏舍、加载失败 */
    private void updateFooter(@Nullable CombinedLoadStates states) {
        if (footer == null || todoAdapter == null) {
            return;
        }
        CombinedLoadStates s = states;
        boolean blocked = Boolean.TRUE.equals(vm.getFarmBlocked().getValue());
        boolean manager = Boolean.TRUE.equals(vm.getManagerView().getValue());
        boolean staffNoPen = !manager && vm.getHome().getValue() != null && vm.getMyPens().getValue() != null
                && vm.getMyPens().getValue().isEmpty();
        if (vm.getHome().getValue() == null || blocked || staffNoPen) {
            footer.setState(ListStateAdapter.State.none());
            return;
        }
        if (!Boolean.TRUE.equals(vm.getHasPen().getValue())) {
            footer.setState(ListStateAdapter.State.empty(com.zhimu.slmp.base.R.drawable.ic_fence,
                    getString(R.string.today_home_no_pen_title), null,
                    manager ? getString(R.string.today_home_add_pen) : null));
            return;
        }
        if (s == null) {
            return;
        }
        LoadState refresh = s.getRefresh();
        LoadState append = s.getAppend();
        if (refresh instanceof LoadState.Error) {
            footer.setState(ListStateAdapter.State.error(getString(R.string.today_home_error_title),
                    ((LoadState.Error) refresh).getError().getMessage(), getString(com.zhimu.slmp.base.R.string.base_action_retry)));
        } else if (append instanceof LoadState.Loading) {
            footer.setState(ListStateAdapter.State.loading());
        } else if (refresh instanceof LoadState.NotLoading && todoAdapter.getItemCount() == 0) {
            footer.setState(ListStateAdapter.State.empty(com.zhimu.slmp.base.R.drawable.ic_task_alt,
                    getString(R.string.today_home_empty_title), getString(R.string.today_home_empty_desc), null));
        } else {
            footer.setState(ListStateAdapter.State.none());
        }
    }

    private void onFooterAction() {
        if (footer.getState().getType() == ListStateAdapter.State.ERROR) {
            todoAdapter.retry();
        } else {
            vm.onAddPenClick();
        }
    }

    // ===================== 跳转与弹窗 =====================

    private void open(@Nullable NavTarget target) {
        if (target == null) {
            return;
        }
        TodayNavigator.open(requireContext(), target, "T04".equals(target.getRoute()) ? detailLauncher : null);
    }

    private boolean needNotifyPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void showNotifyGuide() {
        ConfirmDialogFragment.builder(REQ_NOTIFY)
                .icon(com.zhimu.slmp.base.R.drawable.ic_notifications)
                .title(getString(R.string.today_notify_title))
                .message(getString(R.string.today_notify_message))
                .positive(getString(R.string.today_notify_positive))
                .negative(getString(R.string.today_notify_negative))
                .build()
                .showSafely(getChildFragmentManager());
    }

    @Override
    public void onTodoClick(@NonNull TodoItemVO item) {
        vm.onTodoClick(item);
    }

    @Override
    public void onTodoLongClick(@NonNull TodoItemVO item) {
        vm.onTodoLongClick(item);
    }

    @Override
    public void onDestroyView() {
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(foregroundObserver);
        if (binding != null) {
            binding.list.setAdapter(null);
        }
        binding = null;
        header = null;
        super.onDestroyView();
    }
}
