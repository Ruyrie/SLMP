package com.zhimu.slmp.breed.ui.archive;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedActivityArchiveBinding;
import com.zhimu.slmp.breed.databinding.BreedViewArchiveHeaderBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalDetailVO;
import com.zhimu.slmp.breed.model.vo.BreedingRecordVO;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.breed.ui.common.NavRequest;
import com.zhimu.slmp.breed.ui.common.PregCheckHost;
import com.zhimu.slmp.breed.ui.common.SingleViewAdapter;
import com.zhimu.slmp.breed.ui.mating.PregCheckSheet;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;

import java.util.List;

/**
 * B06 种畜档案（RouterPath.B06）：参数 EXTRA_ANIMAL_ID。头部按状态显示登记按钮；更多菜单“编辑档案 / 查看修改记录 /
 * 登记死亡 / 淘汰”；点繁育记录在该项下方弹出记录菜单。登记页、档案表单返回后刷新并显示提示。
 */
public class AnimalArchiveActivity extends BaseActivity implements PregCheckHost.Provider {

    private BreedActivityArchiveBinding binding;
    private BreedViewArchiveHeaderBinding header;
    private AnimalArchiveViewModel vm;
    private BreedRecordAdapter adapter;
    private FooterLoadStateAdapter footer;
    private final ActivityResultLauncher<Intent> launcher = Router.registerForResult(this, result -> {
        UiMessage msg = BreedNav.messageOf(this, result);
        if (BreedNav.isToList(result)) {
            BreedNav.finishToList(this, msg == null ? null : msg.getText());
            return;
        }
        if (result.getResultCode() == RESULT_OK) {
            // 档案里登记过（断奶、产仔等）：返回列表时让列表刷新状态与计数
            setResult(RESULT_OK);
            vm.onChildResult(msg);
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.breed_activity_archive);
        vm = obtainViewModel(BreedInjector.factory(), AnimalArchiveViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        TopAppBarHelper.setupBack(binding.breedTopBar.baseToolbar, getString(R.string.breed_archive_title));
        TopAppBarHelper.inflateMenu(binding.breedTopBar.baseToolbar, R.menu.breed_menu_more);
        binding.breedTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.breed_menu_more) {
                vm.onMoreClick();
                return true;
            }
            return false;
        });
        vm.getMoreMenu().observe(this, menu -> binding.breedTopBar.baseToolbar.getMenu()
                .findItem(R.id.breed_menu_more).setVisible(menu != null && !menu.isEmpty()));

        binding.breedState.setOnRetryListener(v -> vm.retry());
        binding.breedState.setOnNoPermissionActionListener(v -> finish());
        vm.getUiState().observe(this, binding.breedState::render);
        binding.breedRefresh.setOnRefreshListener(vm::refresh);
        vm.getRefreshing().observe(this, r -> {
            if (!Boolean.TRUE.equals(r)) {
                binding.breedRefresh.setRefreshing(false);
            }
        });

        // 以 RecyclerView 为 parent inflate 头部前先设置 LayoutManager
        binding.breedList.setLayoutManager(new LinearLayoutManager(this));
        header = BreedViewArchiveHeaderBinding.inflate(LayoutInflater.from(this), binding.breedList, false);
        header.setLifecycleOwner(this);
        header.setVm(vm);
        header.breedHeaderAction.setOnClickListener(v -> vm.onHeaderAction());
        adapter = new BreedRecordAdapter((view, record) -> vm.onRecordClick(record));
        adapter.addLoadStateListener(this::onLoadStates);
        footer = new FooterLoadStateAdapter(adapter::retry);
        binding.breedList.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()),
                adapter.withLoadStateFooter(footer)));
        binding.breedList.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int adapterPosition) {
                return adapterPosition > 0 && adapterPosition <= adapter.getItemCount();
            }
        });
        vm.getRecords().observe(this, data -> adapter.submitData(getLifecycle(), data));
        vm.getDetail().observe(this, this::renderStats);

        vm.getShowMoreMenu().observe(this, this::showMoreMenu);
        vm.getShowRecordMenu().observe(this, this::showRecordMenu);
        vm.getShowExitSheet().observe(this, v -> AnimalExitSheet.open(getSupportFragmentManager(),
                vm.getExitSubtitle().getValue()));
        vm.getDismissExitSheet().observe(this, v -> AnimalExitSheet.dismissIn(getSupportFragmentManager()));
        vm.getShowCheckSheet().observe(this, v -> PregCheckSheet.show(this,
                Boolean.TRUE.equals(vm.getCheckEditMode().getValue())));
        vm.getDismissCheckSheet().observe(this, msg -> {
            PregCheckSheet.dismissIn(getSupportFragmentManager());
            if (msg != null) {
                setResult(RESULT_OK);
                showMessage(msg);
            }
        });
        vm.getNavigate().observe(this, this::navigate);
        vm.getOpenChangeLog().observe(this, args -> RecordLogSheet.show(getSupportFragmentManager(), args[0],
                Long.parseLong(args[1]), args[2] == null || args[2].isEmpty() ? null
                        : args[2] + " " + getString(com.zhimu.slmp.common.R.string.common_record_log_title)));
        vm.getFinishToList().observe(this, msg -> BreedNav.finishToList(this, msg));
        vm.getNoPermission().observe(this, v -> finish());
    }

    @NonNull
    @Override
    public PregCheckHost pregCheckHost() {
        if (vm == null) {
            vm = obtainViewModel(BreedInjector.factory(), AnimalArchiveViewModel.class);
        }
        return vm;
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (!BreedNav.handleAction(this, actionId)) {
            vm.onMessageAction(actionId);
        }
    }

    private kotlin.Unit onLoadStates(@NonNull CombinedLoadStates states) {
        LoadState refresh = states.getRefresh();
        boolean empty = refresh instanceof LoadState.NotLoading && adapter.getItemCount() == 0;
        header.setRecordsEmpty(empty);
        footer.setHasItems(adapter.getItemCount() > 0);
        if (!(refresh instanceof LoadState.Loading)) {
            binding.breedRefresh.setRefreshing(false);
        }
        return kotlin.Unit.INSTANCE;
    }

    private void renderStats(@Nullable BreedingAnimalDetailVO d) {
        if (d == null) {
            return;
        }
        String noData = getString(R.string.breed_no_data);
        header.breedStatParity.setValue(String.valueOf(d.getParity()), getString(R.string.breed_unit_parity));
        BreedingAnimalDetailVO.Stats s = d.getStats();
        if (s != null && s.getAvgLiveBorn() != null) {
            header.breedStatAvg.setValue(s.getAvgLiveBorn().stripTrailingZeros().scale() <= 0
                    ? s.getAvgLiveBorn().setScale(1).toPlainString() : s.getAvgLiveBorn().toPlainString(),
                    BreedTextFormatter.UNIT_HEAD);
        } else {
            header.breedStatAvg.setValue(noData, null);
        }
        if (s != null && s.getLastWeanDate() != null) {
            header.breedStatWean.setValue(BreedTextFormatter.md(s.getLastWeanDate()), null);
        } else {
            header.breedStatWean.setValue(noData, null);
        }
        // UI 2.2：指标卡数字 24 sp，日期与“无数据”20 sp
        valueSize(header.breedStatParity, 24);
        valueSize(header.breedStatAvg, s != null && s.getAvgLiveBorn() != null ? 24 : 20);
        valueSize(header.breedStatWean, 20);
    }

    private static void valueSize(@NonNull View card, int sp) {
        android.widget.TextView v = card.findViewById(com.zhimu.slmp.base.R.id.base_stat_value);
        if (v != null) {
            v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
            v.setMaxLines(1);
        }
    }

    private void showMoreMenu(@Nullable List<String> menu) {
        View anchor = binding.breedTopBar.baseToolbar.findViewById(R.id.breed_menu_more);
        if (menu == null || anchor == null) {
            return;
        }
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor);
        if (menu.contains(AnimalArchiveViewModel.MENU_EDIT)) {
            b.add(R.id.breed_menu_edit_archive, getString(R.string.breed_menu_edit), com.zhimu.slmp.base.R.drawable.ic_edit);
        }
        if (menu.contains(AnimalArchiveViewModel.MENU_LOG)) {
            b.add(R.id.breed_menu_archive_log, getString(R.string.breed_menu_log), com.zhimu.slmp.base.R.drawable.ic_history);
        }
        if (menu.contains(AnimalArchiveViewModel.MENU_EXIT)) {
            b.add(new MoreMenuHelper.Item(R.id.breed_menu_exit, getString(R.string.breed_menu_exit),
                    com.zhimu.slmp.base.R.drawable.ic_heart_minus, getString(R.string.breed_menu_exit_desc), null,
                    true, true));
        }
        b.setOnItemClickListener(id -> {
            if (id == R.id.breed_menu_edit_archive) {
                vm.onMenu(AnimalArchiveViewModel.MENU_EDIT);
            } else if (id == R.id.breed_menu_archive_log) {
                vm.onMenu(AnimalArchiveViewModel.MENU_LOG);
            } else if (id == R.id.breed_menu_exit) {
                // 先关菜单再弹面板（菜单与面板不叠加）
                binding.getRoot().post(() -> vm.onMenu(AnimalArchiveViewModel.MENU_EXIT));
            }
        }).show();
    }

    private void showRecordMenu(@Nullable BreedingRecordVO r) {
        if (r == null) {
            return;
        }
        View anchor = findItemView(r);
        if (anchor == null) {
            return;
        }
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor);
        boolean any = false;
        if (AnimalArchiveViewModel.canEdit(r)) {
            b.add(R.id.breed_menu_edit_record, getString(R.string.breed_record_edit), com.zhimu.slmp.base.R.drawable.ic_edit);
            any = true;
        }
        // 产仔记录生成了仔猪批次就能查看，与能否申请修改无关
        if (BreedCodes.BIZ_FARROWING.equals(r.getRecordType()) && r.getBatchId() != null) {
            b.add(R.id.breed_menu_view_batch, getString(R.string.breed_record_view_batch),
                    com.zhimu.slmp.base.R.drawable.ic_folder_open);
            any = true;
        }
        if (r.isCanRequestChange()) {
            if (r.getChangeState() != null && r.getChangeState().getPendingRequestId() != null) {
                b.add(new MoreMenuHelper.Item(R.id.breed_menu_pending, getString(R.string.breed_record_pending),
                        com.zhimu.slmp.base.R.drawable.ic_pending_actions, null, null, false, false));
            } else {
                b.addReview(R.id.breed_menu_request_change, getString(R.string.breed_record_request_change),
                        com.zhimu.slmp.base.R.drawable.ic_edit_note);
            }
            any = true;
        }
        if (r.isHasChangeLog() || AnimalArchiveViewModel.canEdit(r)) {
            b.add(R.id.breed_menu_record_log, getString(R.string.breed_menu_log), com.zhimu.slmp.base.R.drawable.ic_history);
            any = true;
        }
        if (!any) {
            return;
        }
        b.setOnItemClickListener(id -> {
            if (id == R.id.breed_menu_edit_record) {
                vm.editRecord(r);
            } else if (id == R.id.breed_menu_view_batch) {
                vm.viewBatch(r);
            } else if (id == R.id.breed_menu_request_change) {
                vm.requestChange(r);
            } else if (id == R.id.breed_menu_record_log) {
                vm.viewChangeLog(r);
            }
        }).show();
    }

    @Nullable
    private View findItemView(@NonNull BreedingRecordVO r) {
        RecyclerView list = binding.breedList;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            RecyclerView.ViewHolder h = list.getChildViewHolder(child);
            if (h instanceof BreedRecordAdapter.Holder) {
                int pos = h.getBindingAdapterPosition();
                if (pos >= 0 && pos < adapter.getItemCount()) {
                    BreedingRecordVO item = adapter.peek(pos);
                    if (item != null && item.getRecordId() == r.getRecordId()
                            && java.util.Objects.equals(item.getRecordType(), r.getRecordType())) {
                        View anchor = child.findViewById(R.id.breed_row_anchor);
                        return anchor != null ? anchor : child;
                    }
                }
            }
        }
        return null;
    }

    private void navigate(@Nullable NavRequest req) {
        if (req == null) {
            return;
        }
        if (req.isForResult()) {
            Router.openForResult(this, launcher, req.getAction(), req.getExtras());
        } else {
            Router.open(this, req.getAction(), req.getExtras());
        }
    }
}
