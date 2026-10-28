package com.zhimu.slmp.herd.ui.pendetail;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.UserBriefVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityPenDetailBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.PenDetailVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.HerdNav;
import com.zhimu.slmp.herd.ui.common.MenuItemSpec;

import java.util.List;

/**
 * L04 栏舍详情（所有者、场长）。员工从外部链接打开时 40303 显示无权查看。
 */
public class PenDetailActivity extends BaseActivity {

    private static final String REQ_DISABLE = "herd_disable_pen";

    private HerdActivityPenDetailBinding binding;
    private PenDetailViewModel vm;

    private final ActivityResultLauncher<Intent> childLauncher =
            Router.registerForResult(this, r -> vm.onChildResult(r.getResultCode()));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.herd_activity_pen_detail);
        vm = obtainViewModel(HerdInjector.factory(), PenDetailViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        com.google.android.material.appbar.MaterialToolbar toolbar = binding.herdTopBar.baseToolbar;
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        toolbar.inflateMenu(R.menu.herd_menu_more);
        TopAppBarHelper.tintMenu(toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.herd_menu_more) {
                vm.onMoreClick();
                return true;
            }
            return false;
        });
        TopAppBarHelper.bindLiftOnScroll(binding.herdTopBar.baseAppBar, binding.herdScroll);

        binding.herdState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.herdState::render);
        vm.getRefreshing().observe(this, r -> binding.herdRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.herdRefresh.setOnRefreshListener(vm::retry);
        vm.getStatusSubmitting().observe(this, s -> binding.herdRefresh.setRefreshing(Boolean.TRUE.equals(s)));
        vm.getPen().observe(this, this::render);
        vm.getMenuEvent().observe(this, this::showMenu);
        vm.getDisableConfirmEvent().observe(this, name -> ConfirmDialogFragment.builder(REQ_DISABLE)
                .title(getString(R.string.herd_pen_disable_title, name))
                .message(getString(R.string.herd_pen_disable_message))
                .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                .positive(getString(R.string.herd_pen_disable_action))
                .danger(true)
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISABLE, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.confirmDisable();
            }
        });
        vm.getChangeLogEvent().observe(this, penId -> RecordLogSheet.show(getSupportFragmentManager(),
                HerdExtras.BIZ_PEN, penId, null));
        vm.getNavEvent().observe(this, t -> HerdNav.open(this, childLauncher, t));
    }

    private void showMenu(List<MenuItemSpec> items) {
        View anchor = binding.herdTopBar.baseToolbar.findViewById(R.id.herd_menu_more);
        if (anchor != null) {
            MenuItemSpec.show(anchor, items, vm::onMenuSelected);
        }
    }

    private void render(@Nullable PenDetailVO p) {
        if (p == null) {
            return;
        }
        String unit = p.getUnitText();
        binding.herdOccupancy.setText(HerdFormat.percent(p.getOccupancyRate()));
        binding.herdOverTag.setVisibility(p.getOverCapacityCount() > 0 ? View.VISIBLE : View.GONE);
        binding.herdStock.setText(HerdFormat.number(p.getStockCount()));
        binding.herdCapacity.setText("/ " + HerdFormat.qty(p.getCapacity(), unit));
        int progress = p.getOverCapacityCount() > 0 ? 100 : HerdFormat.progress(p.getStockCount(), p.getCapacity());
        binding.herdCapacityBar.setProgressCompat(progress, false);
        binding.herdKvCode.setValue(p.getPenCode());
        binding.herdKvType.setValue(p.getPenTypeText());
        binding.herdKvCategory.setValue(p.getCategoryName());
        binding.herdKvCapacity.setValue(HerdFormat.qty(p.getCapacity(), unit));

        // 负责员工
        binding.herdStaffList.removeAllViews();
        if (p.getStaff().isEmpty()) {
            ListRowView row = newRow();
            row.setTitle(getString(R.string.herd_pen_no_staff));
            row.setTrailingText(getString(R.string.herd_pen_assign));
            ClickThrottle.setOnClickListener(row, v -> vm.onAssignClick());
            binding.herdStaffList.addView(row);
        } else {
            for (UserBriefVO u : p.getStaff()) {
                ListRowView row = newRow();
                row.setAvatarName(u.getName());
                row.setTitle(u.getName());
                row.setSubtitle(getString(R.string.herd_pen_staff_sub));
                // 有 member_id 才能打开 F09 成员详情
                boolean openable = PenDetailViewModel.canOpenStaff(u);
                row.setShowChevron(openable);
                if (openable) {
                    ClickThrottle.setOnClickListener(row, v -> vm.onStaffClick(u));
                }
                binding.herdStaffList.addView(row);
            }
        }

        // 在栏批次
        binding.herdBatchesHeader.setTitle(getString(R.string.herd_pen_batches, p.getBatches().size()));
        binding.herdBatchList.removeAllViews();
        if (p.getBatches().isEmpty()) {
            ListRowView row = newRow();
            row.setSubtitle(getString(R.string.herd_pen_empty_batches));
            binding.herdBatchList.addView(row);
        } else {
            for (PenDetailVO.PenBatchItem b : p.getBatches()) {
                ListRowView row = newRow();
                row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_folder_open, ListRowView.TONE_SECONDARY);
                row.setTitle(b.getBatchNo());
                row.setSubtitle(b.getBreedTitle() + HerdFormat.DOT + b.getDayAge() + " 日龄" + HerdFormat.DOT
                        + HerdFormat.qty(b.getQtyInPen(), unit));
                if (b.isInWithdrawal()) {
                    row.setStatusTag(getString(R.string.herd_tag_withdrawal), StatusTagView.WARNING, 0);
                }
                ClickThrottle.setOnClickListener(row, v -> vm.onBatchClick(b.getBatchId()));
                binding.herdBatchList.addView(row);
            }
        }
    }

    private ListRowView newRow() {
        ListRowView row = new ListRowView(this);
        row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }
}
