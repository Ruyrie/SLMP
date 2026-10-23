package com.zhimu.slmp.health.ui.immuneplan;

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
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthActivityImmunePlanBinding;
import com.zhimu.slmp.health.databinding.HealthItemPreEntryBinding;
import com.zhimu.slmp.health.databinding.HealthViewImmunePlanHeaderBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.ImmunePlanOverviewVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanVO;
import com.zhimu.slmp.health.model.vo.MedicationTargetVO;
import com.zhimu.slmp.health.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.SingleViewAdapter;
import com.zhimu.slmp.health.ui.immuneitem.ImmuneItemEditViewModel;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.ImmuneRules;

import java.util.List;
import java.util.Objects;

import kotlin.Unit;

/**
 * D01 免疫计划（ImmunePlanActivity）：入口 L06、T04、T06 漏免消息（参数 batch_id）。
 * 顶部栏 history 打开修改记录；批次概况行；状态筛选 Chip；入栏前项目卡（只在“全部”下显示，员工不显示）；
 * 计划列表（分页）；扩展型 FAB“添加项目”（所有者、场长，批次未结束）。
 */
public class ImmunePlanActivity extends BaseActivity {

    private static final String REQ_DELETE = "health_plan_delete";
    private static final String REQ_BATCH = "health_plan_batch";
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;

    private HealthActivityImmunePlanBinding binding;
    private HealthViewImmunePlanHeaderBinding header;
    private ImmunePlanViewModel vm;
    private ImmunePlanAdapter adapter;
    @Nullable
    private ImmunePlanVO pendingDelete;
    private boolean listEmpty;

    private final ActivityResultLauncher<Intent> childLauncher = Router.registerForResult(this, r -> {
        if (r.getResultCode() == RESULT_OK) {
            Intent data = r.getData();
            long id = data == null ? 0 : data.getLongExtra(HealthExtras.RESULT_PLAN_ID, 0);
            vm.onChildResult(id > 0 ? id : null);
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HealthActivityImmunePlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.healthList.setLayoutManager(new LinearLayoutManager(this));
        header = HealthViewImmunePlanHeaderBinding.inflate(getLayoutInflater(), binding.healthList, false);
        vm = obtainViewModel(HealthInjector.factory(), ImmunePlanViewModel.class);
        observeBase(vm);
        HealthNav.finishOnFarmSwitch(this);

        TopAppBarHelper.setupBack(binding.healthToolbar, getString(R.string.health_d01_title));
        binding.healthToolbar.inflateMenu(R.menu.health_menu_history);
        TopAppBarHelper.tintMenu(binding.healthToolbar);
        binding.healthToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.health_menu_history) {
                RecordLogSheet.show(getSupportFragmentManager(), HealthExtras.BIZ_IMMUNE_PLAN,
                        vm.getBatchId() == null ? 0L : vm.getBatchId(), null);
                return true;
            }
            return false;
        });

        adapter = new ImmunePlanAdapter(new ImmunePlanAdapter.Listener() {
            @Override
            public void onAction(@NonNull ImmunePlanVO plan, @NonNull String action) {
                vm.onPlanAction(plan, action);
            }

            @Override
            public void onMore(@NonNull View anchor, @NonNull ImmunePlanVO plan) {
                showPlanMenu(anchor, plan);
            }

            @Override
            public void onClick(@NonNull ImmunePlanVO plan) {
                vm.onPlanClick(plan);
            }
        });
        adapter.setStaff(vm.isStaff());
        adapter.setUnit(HealthFormat.countUnit(com.zhimu.slmp.common.di.CoreInjector.get(this).farmContext()));
        FooterLoadStateAdapter footer = new FooterLoadStateAdapter(adapter::retry);
        adapter.addLoadStateListener(states -> {
            footer.setLoadState(states.getAppend());
            boolean done = states.getRefresh() instanceof LoadState.NotLoading;
            listEmpty = done && adapter.getItemCount() == 0;
            renderEmpty();
            if (states.getRefresh() instanceof LoadState.Error && adapter.getItemCount() > 0) {
                showMessage(getString(R.string.health_refresh_failed));
            }
            return Unit.INSTANCE;
        });
        binding.healthList.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()), adapter, footer));
        binding.healthList.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, @NonNull View view, int position) {
                return position > 0;
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
        binding.healthState.setOnEmptyActionListener(v -> vm.requestBatchPick());
        vm.getUiState().observe(this, binding.healthState::render);
        vm.getRefreshing().observe(this, r -> binding.healthRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.healthRefresh.setOnRefreshListener(vm::refresh);

        vm.getOverview().observe(this, this::renderOverview);
        vm.getStatusFilter().observe(this, s -> renderChips());
        vm.getPreEntryItems().observe(this, this::renderPreEntry);
        vm.getCanManage().observe(this, c -> binding.healthFab.setVisibility(Boolean.TRUE.equals(c)
                ? View.VISIBLE : View.GONE));
        vm.getEmptyText().observe(this, t -> renderEmpty());
        vm.getPlans().observe(this, data -> adapter.submitData(getLifecycle(), data));
        vm.getHighlightPlanId().observe(this, id -> {
            if (id != null) {
                adapter.highlight(id);
                vm.consumeHighlight();
            }
        });
        ClickThrottle.setOnClickListener(binding.healthFab, v -> vm.onAddItem());
        ClickThrottle.setOnClickListener(header.healthEmptyAction, v -> vm.onAddItem());

        vm.getOpenRegister().observe(this, this::openRegister);
        vm.getOpenEditItem().observe(this, this::openEditItem);
        vm.getOpenRecord().observe(this, id -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_RECORD_ID, id);
            HealthNav.open(this, childLauncher, RouterPath.D03, extras);
        });
        vm.getShowPostpone().observe(this, p -> PostponeDialogFragment.show(getSupportFragmentManager()));
        vm.getClosePostpone().observe(this, v -> PostponeDialogFragment.dismiss(getSupportFragmentManager()));
        vm.getShowDeleteConfirm().observe(this, p -> {
            pendingDelete = p;
            ConfirmDialogFragment.builder(REQ_DELETE)
                    .title(getString(R.string.health_plan_delete_title))
                    .message(getString(R.string.health_plan_delete_message))
                    .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                    .positive(getString(R.string.health_action_delete))
                    .danger(true)
                    .build()
                    .showSafely(getSupportFragmentManager());
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DELETE, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result) && pendingDelete != null) {
                vm.deletePlan(pendingDelete);
            }
            pendingDelete = null;
        });
        vm.getPickBatch().observe(this, this::showBatchPick);
        getSupportFragmentManager().setFragmentResultListener(REQ_BATCH, this, (key, result) -> {
            Long id = ImmunePlanViewModel.asLong(OptionSheetDialogFragment.getValue(result));
            if (id != null) {
                vm.start(id);
            }
        });
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        vm.onMessageAction(actionId);
    }

    // ===== 渲染 =====

    private void renderOverview(@Nullable ImmunePlanOverviewVO vo) {
        if (vo == null) {
            return;
        }
        String unit = HealthFormat.countUnit(com.zhimu.slmp.common.di.CoreInjector.get(this).farmContext());
        header.healthBatchNo.setText(vo.getBatchNo());
        header.healthBatchSummary.setText(HealthFormat.nonNull(vo.getPenName()) + HealthFormat.DOT + vo.getDayAge()
                + " 日龄" + HealthFormat.DOT + "存栏 " + HealthFormat.qty(vo.getCurrentCount(), unit));
        header.healthEndedNotice.setVisibility(vo.isBatchEnded() ? View.VISIBLE : View.GONE);
        renderChips();
        renderPreEntry(vm.getPreEntryItems().getValue());
        renderEmpty();
    }

    /** 筛选 Chip：“全部 5”“待接种 1”……数量为 0 也显示 */
    private void renderChips() {
        String current = vm.getStatusFilter().getValue();
        if (header.healthFilterChips.getChildCount() == 0) {
            LayoutInflater inflater = getLayoutInflater();
            for (String status : ImmunePlanViewModel.STATUSES) {
                Chip chip = (Chip) inflater.inflate(R.layout.health_item_filter_chip, header.healthFilterChips, false);
                chip.setTag(status);
                ClickThrottle.setOnClickListener(chip, v -> vm.selectStatus(status));
                header.healthFilterChips.addView(chip);
            }
        }
        for (int i = 0; i < header.healthFilterChips.getChildCount(); i++) {
            Chip chip = (Chip) header.healthFilterChips.getChildAt(i);
            String status = (String) chip.getTag();
            chip.setText(ImmunePlanViewModel.statusLabel(status) + " " + vm.countOf(status));
            chip.setChecked(Objects.equals(status, current));
        }
        boolean all = ImmunePlanViewModel.ALL.equals(current);
        header.healthPlanTitle.setTitle(getString(R.string.health_plan_section, vm.countOf(
                current == null ? ImmunePlanViewModel.ALL : current)));
        List<ImmunePlanOverviewVO.PreEntryItemVO> items = vm.getPreEntryItems().getValue();
        header.healthPreEntryGroup.setVisibility(all && items != null && !items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 入栏前项目卡：每行“加入计划”“确认已接种”；全部处理后整组不显示 */
    private void renderPreEntry(@Nullable List<ImmunePlanOverviewVO.PreEntryItemVO> items) {
        header.healthPreEntryRows.removeAllViews();
        boolean show = items != null && !items.isEmpty() && ImmunePlanViewModel.ALL.equals(vm.getStatusFilter().getValue());
        header.healthPreEntryGroup.setVisibility(show ? View.VISIBLE : View.GONE);
        if (items == null) {
            return;
        }
        ImmunePlanOverviewVO vo = vm.getOverview().getValue();
        int pending = vo != null && vo.getPreEntryPendingCount() != null ? vo.getPreEntryPendingCount() : items.size();
        header.healthPreEntryTitle.setTitle(getString(R.string.health_pre_entry_section, pending));
        for (ImmunePlanOverviewVO.PreEntryItemVO item : items) {
            HealthItemPreEntryBinding row = HealthItemPreEntryBinding.inflate(getLayoutInflater(),
                    header.healthPreEntryRows, false);
            row.healthPreEntryName.setText(item.getVaccineName());
            row.healthPreEntryHint.setText((item.getImmuneRound() == null ? "" : item.getImmuneRound() + HealthFormat.DOT)
                    + "模板 " + item.getTemplateAgeDay() + " 日龄");
            ClickThrottle.setOnClickListener(row.healthPreEntryJoin, v -> vm.joinPreEntry(item));
            ClickThrottle.setOnClickListener(row.healthPreEntryConfirm, v -> vm.confirmPreEntry(item));
            header.healthPreEntryRows.addView(row.getRoot());
        }
    }

    /** 列表为空：无模板（养殖户带“添加免疫项目”按钮）或筛选后为空 */
    private void renderEmpty() {
        String text = vm.getEmptyText().getValue();
        boolean show = listEmpty && text != null;
        header.healthEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        header.healthEmptyText.setText(text);
        header.healthEmptyAction.setVisibility(show && vm.showAddOnEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPlanMenu(@NonNull View anchor, @NonNull ImmunePlanVO plan) {
        MoreMenuHelper.Builder menu = MoreMenuHelper.with(anchor);
        if (plan.getActions().contains(HealthExtras.ACTION_EDIT)) {
            menu.add(MENU_EDIT, getString(R.string.health_plan_menu_edit), com.zhimu.slmp.base.R.drawable.ic_edit);
        }
        if (plan.getActions().contains(HealthExtras.ACTION_DELETE)) {
            menu.addDanger(MENU_DELETE, getString(R.string.health_plan_menu_delete),
                    com.zhimu.slmp.base.R.drawable.ic_delete);
        }
        menu.setOnItemClickListener(id -> vm.onPlanAction(plan, id == MENU_EDIT ? HealthExtras.ACTION_EDIT
                : HealthExtras.ACTION_DELETE)).show();
    }

    private void showBatchPick(@NonNull List<MedicationTargetVO> list) {
        String unit = HealthFormat.countUnit(com.zhimu.slmp.common.di.CoreInjector.get(this).farmContext());
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQ_BATCH)
                .title(getString(R.string.health_pick_batch));
        for (MedicationTargetVO t : list) {
            if (t.getBatchId() == null) {
                continue;
            }
            b.option(String.valueOf(t.getBatchId()), HealthFormat.nonNull(t.getBatchNo()),
                    HealthFormat.nonNull(t.getPenName()) + HealthFormat.DOT + "存栏 "
                            + HealthFormat.qty(t.getCurrentCount(), unit));
        }
        b.build().showSafely(getSupportFragmentManager());
    }

    // ===== 跳转 =====

    private void openRegister(@NonNull ImmunePlanVO plan) {
        Bundle extras = new Bundle();
        extras.putLong(RouterPath.EXTRA_PLAN_ID, plan.getId());
        extras.putBoolean(RouterPath.EXTRA_BACKFILL, ImmuneRules.STATUS_MISSED.equals(plan.getStatus()));
        HealthNav.open(this, childLauncher, RouterPath.D03, extras);
    }

    private void openEditItem(@Nullable ImmunePlanVO plan) {
        Bundle extras = new Bundle();
        Long batchId = vm.getBatchId();
        if (batchId != null) {
            extras.putLong(RouterPath.EXTRA_BATCH_ID, batchId);
        }
        if (plan != null) {
            extras.putLong(RouterPath.EXTRA_PLAN_ID, plan.getId());
            extras.putInt(RouterPath.EXTRA_VERSION, plan.getVersion());
            extras.putString(RouterPath.EXTRA_DOSE, HealthFormat.plain(plan.getDose()));
            extras.putString(RouterPath.EXTRA_METHOD_CODE, plan.getMethodCode());
            extras.putString(ImmuneItemEditViewModel.EXTRA_VACCINE_NAME, plan.getVaccineName()
                    + (plan.getImmuneRound() == null ? "" : HealthFormat.DOT + plan.getImmuneRound()));
            extras.putString(ImmuneItemEditViewModel.EXTRA_PLAN_MODE, plan.getPlanMode());
            extras.putString(ImmuneItemEditViewModel.EXTRA_PLAN_DATE, plan.getPlanDate());
            extras.putInt(ImmuneItemEditViewModel.EXTRA_AGE_AT_PLAN, plan.getAgeAtPlan());
            extras.putString(ImmuneItemEditViewModel.EXTRA_DOSE_UNIT, plan.getDoseUnit());
        }
        HealthNav.open(this, childLauncher, RouterPath.D02, extras);
    }
}
