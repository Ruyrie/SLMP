package com.zhimu.slmp.today.ui.message;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayActivityMessageCenterBinding;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.ui.common.ListStateAdapter;
import com.zhimu.slmp.today.ui.notice.NoticeDetailActivity;
import com.zhimu.slmp.today.util.MessageRow;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.TodayNavigator;

import kotlin.Unit;

/**
 * T06 消息中心：类型、养殖场（只对养殖户）、未读筛选；按“今天 / 昨天 / 更早”分组；done_all 全部标为已读；
 * 点消息标为已读并跳转（平台通知、转让、冻结进 T07，以 Activity Result 打开以便删除后撤销）；邀请在列表内同意或拒绝；
 * 长按菜单“删除消息”或左滑删除，Snackbar“撤销”4 秒；列表末尾“只保留最近 180 天的消息”。
 */
public class MessageCenterActivity extends BaseActivity implements MessagePagingAdapter.Listener {

    private static final String REQ_REJECT = "today_reject_invite";

    private MessageCenterViewModel vm;
    private TodayActivityMessageCenterBinding binding;
    private MessagePagingAdapter adapter;
    private final ListStateAdapter footer = new ListStateAdapter();

    private final ActivityResultLauncher<Intent> noticeLauncher = Router.registerForResult(this, result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            long id = result.getData().getLongExtra(NoticeDetailActivity.RESULT_DELETED_ID, 0);
            if (id > 0) {
                vm.onNoticeDeleted(id);
            }
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(TodayInjector.factory(), MessageCenterViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.today_activity_message_center);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.topBar.baseToolbar, getString(R.string.today_message_title));
        TopAppBarHelper.inflateMenu(binding.topBar.baseToolbar, R.menu.today_menu_messages);
        binding.topBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.today_menu_read_all) {
                vm.markAllRead();
                return true;
            }
            if (item.getItemId() == R.id.today_menu_settings) {
                TodayNavigator.open(this, NavTarget.of("T05"));
                return true;
            }
            return false;
        });
        TopAppBarHelper.bindLiftOnScroll(binding.topBar.baseAppBar, binding.list);

        adapter = new MessagePagingAdapter(this);
        footer.setOnActionClickListener(v -> {
            if (footer.getState().getType() == ListStateAdapter.State.ERROR) {
                adapter.retry();
            } else {
                vm.clearFilters();
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(new ConcatAdapter(adapter, footer));
        binding.refresh.setOnRefreshListener(vm::refresh);
        adapter.addLoadStateListener(this::onLoadStates);
        attachSwipeToDelete();

        binding.chipType.setOnClickListener(v -> vm.onCategoryChipClick());
        binding.chipFarm.setOnClickListener(v -> vm.onFarmChipClick());
        binding.chipUnread.setOnClickListener(v -> vm.toggleUnread());
        getSupportFragmentManager().setFragmentResultListener(REQ_REJECT, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.confirmPendingReject();
            }
        });

        vm.getMessagePaging().observe(this, data -> adapter.submitData(getLifecycle(), data));
        vm.getCategory().observe(this, c -> renderDropdown(binding.chipType, c == null ? null
                : MessageCenterViewModel.categoryName(c), getString(R.string.today_message_type)));
        vm.getFarmFilter().observe(this, f -> renderDropdown(binding.chipFarm, f == null ? null : f.getName(),
                getString(R.string.today_message_all_farms)));
        vm.getUnreadOnly().observe(this, u -> binding.chipUnread.setChecked(Boolean.TRUE.equals(u)));
        vm.getActingActionId().observe(this, adapter::setActingActionId);
        vm.getOpenFilterSheet().observe(this, this::openFilterSheet);
        vm.getOpenRejectDialog().observe(this, m -> ConfirmDialogFragment.builder(REQ_REJECT)
                .title(getString(R.string.today_message_reject_title))
                .message(getString(R.string.today_message_reject_message))
                .positive(getString(R.string.today_message_reject))
                .negative(getString(R.string.today_detail_cancel))
                .danger(true)
                .build()
                .showSafely(getSupportFragmentManager()));
        vm.getNavEvent().observe(this, this::open);
    }

    /** 下拉型 Chip：已选值时显示所选值并变为选中样式 */
    private static void renderDropdown(@NonNull Chip chip, @Nullable String value, @NonNull String placeholder) {
        chip.setCheckable(true);
        chip.setCheckedIconVisible(true);
        chip.setChecked(value != null);
        chip.setText(value != null ? value : placeholder);
    }

    private void openFilterSheet(@Nullable String type) {
        if (MessageCenterViewModel.SHEET_FARM.equals(type)) {
            MessageFilterSheet.show(getSupportFragmentManager(), type, getString(R.string.today_message_farm_sheet),
                    getString(R.string.today_message_farm_sheet_desc));
        } else if (type != null) {
            MessageFilterSheet.show(getSupportFragmentManager(), type, getString(R.string.today_message_type_sheet), null);
        }
        // Chip 点击会切换选中态，按 ViewModel 的值恢复
        String c = vm.getCategory().getValue();
        renderDropdown(binding.chipType, c == null ? null : MessageCenterViewModel.categoryName(c),
                getString(R.string.today_message_type));
        FarmBriefVO f = vm.getFarmFilter().getValue();
        renderDropdown(binding.chipFarm, f == null ? null : f.getName(), getString(R.string.today_message_all_farms));
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        LoadState refresh = states.getRefresh();
        if (!(refresh instanceof LoadState.Loading)) {
            binding.refresh.setRefreshing(false);
        }
        int count = adapter.getItemCount();
        if (refresh instanceof LoadState.Loading && count == 0) {
            footer.setState(ListStateAdapter.State.skeleton());
        } else if (refresh instanceof LoadState.Error && count == 0) {
            footer.setState(ListStateAdapter.State.error(getString(R.string.today_message_error_title),
                    ((LoadState.Error) refresh).getError().getMessage(),
                    getString(com.zhimu.slmp.base.R.string.base_action_retry)));
        } else if (refresh instanceof LoadState.Error) {
            showMessage(getString(R.string.today_refresh_failed));
            footer.setState(ListStateAdapter.State.none());
        } else if (refresh instanceof LoadState.NotLoading && count == 0) {
            if (Boolean.TRUE.equals(vm.getUnreadOnly().getValue()) && vm.getCategory().getValue() == null
                    && vm.getFarmFilter().getValue() == null) {
                footer.setState(ListStateAdapter.State.empty(com.zhimu.slmp.base.R.drawable.ic_done_all,
                        getString(R.string.today_message_unread_empty), null, getString(R.string.today_message_clear_filter)));
            } else if (vm.hasFilter()) {
                footer.setState(ListStateAdapter.State.empty(com.zhimu.slmp.base.R.drawable.ic_filter_list,
                        getString(R.string.today_message_filter_empty), null, getString(R.string.today_message_clear_filter)));
            } else {
                footer.setState(ListStateAdapter.State.empty(com.zhimu.slmp.base.R.drawable.ic_notifications,
                        getString(R.string.today_message_empty_title), getString(R.string.today_message_empty_desc), null));
            }
        } else if (states.getAppend() instanceof LoadState.Loading) {
            footer.setState(ListStateAdapter.State.loading());
        } else if (states.getAppend().getEndOfPaginationReached()) {
            footer.setState(ListStateAdapter.State.note(getString(R.string.today_message_retention)));
        } else {
            footer.setState(ListStateAdapter.State.none());
        }
        return Unit.INSTANCE;
    }

    private void open(@Nullable NavTarget target) {
        if (target == null) {
            return;
        }
        TodayNavigator.open(this, target, "T07".equals(target.getRoute()) ? noticeLauncher : null);
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        vm.onMessageAction(actionId);
    }

    // ===================== 列表回调 =====================

    @Override
    public void onMessageClick(@NonNull MessageBriefVO m) {
        vm.openMessage(m);
    }

    @Override
    public void onMessageLongClick(@NonNull View anchor, @NonNull MessageBriefVO m) {
        MoreMenuHelper.with(anchor)
                .addDanger(R.id.today_action_delete, getString(R.string.today_message_delete),
                        com.zhimu.slmp.base.R.drawable.ic_delete)
                .setOnItemClickListener(id -> vm.deleteMessage(m))
                .show();
    }

    @Override
    public void onAccept(@NonNull MessageBriefVO m) {
        vm.acceptInvite(m);
    }

    @Override
    public void onReject(@NonNull MessageBriefVO m) {
        vm.rejectInvite(m);
    }

    /** 左滑删除：调用 API-T17，成功后从列表移除并提示“撤销” */
    private void attachSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                MessageRow row = rowAt(vh);
                return row == null || row.isHeader() ? 0 : super.getSwipeDirs(rv, vh);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                MessageRow row = rowAt(vh);
                int pos = vh.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    adapter.notifyItemChanged(pos);
                }
                if (row != null && row.getMessage() != null) {
                    vm.deleteMessage(row.getMessage());
                }
            }
        }).attachToRecyclerView(binding.list);
    }

    @Nullable
    private MessageRow rowAt(@NonNull RecyclerView.ViewHolder vh) {
        if (vh.getBindingAdapter() != adapter) {
            return null;
        }
        int pos = vh.getBindingAdapterPosition();
        return pos == RecyclerView.NO_POSITION || pos >= adapter.getItemCount() ? null : adapter.peek(pos);
    }
}
