package com.zhimu.slmp.health.ui.medicationarchive;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.dialog.ReasonDialogFragment;
import com.zhimu.slmp.base.dialog.TipPopup;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthActivityMedicationArchiveBinding;
import com.zhimu.slmp.health.databinding.HealthViewMedicationHeaderBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.DeleteImpactVO;
import com.zhimu.slmp.health.model.vo.MedicationRecordVO;
import com.zhimu.slmp.health.model.vo.MedicationTargetVO;
import com.zhimu.slmp.health.model.vo.WithdrawalVO;
import com.zhimu.slmp.health.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.SingleViewAdapter;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.WithdrawalCalculator;

import java.util.List;

import kotlin.Unit;

/**
 * D05 用药档案（MedicationArchiveActivity）：入口 L06 休药状态、L01 生产页、T04 休药期到期待办、E06 休药期拦截、D04 提交后、
 * 推送 WITHDRAWAL_DELAYED / STAFF_NOTIFY（参数 batch_id）。
 */
public class MedicationArchiveActivity extends BaseActivity {

    private static final String REQ_DELETE_CONFIRM = "health_med_delete_confirm";
    private static final String REQ_DELETE_REQUEST = "health_med_delete_request";
    private static final String REQ_STAFF = "health_med_delete_staff";
    private static final String REQ_BATCH = "health_med_batch";
    private static final int MENU_EDIT = 1;
    private static final int MENU_CHANGE = 2;
    private static final int MENU_VIEW_REQUEST = 3;
    private static final int MENU_LOG = 4;
    private static final int MENU_DELETE = 5;

    private HealthActivityMedicationArchiveBinding binding;
    private HealthViewMedicationHeaderBinding header;
    private MedicationArchiveViewModel vm;
    private MedicationRecordAdapter adapter;

    private final ActivityResultLauncher<Intent> childLauncher = Router.registerForResult(this, r -> {
        if (r.getResultCode() == RESULT_OK) {
            vm.refresh();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HealthActivityMedicationArchiveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.healthList.setLayoutManager(new LinearLayoutManager(this));
        header = HealthViewMedicationHeaderBinding.inflate(getLayoutInflater(), binding.healthList, false);
        vm = obtainViewModel(HealthInjector.factory(), MedicationArchiveViewModel.class);
        observeBase(vm);
        HealthNav.finishOnFarmSwitch(this);

        TopAppBarHelper.setupBack(binding.healthToolbar, getString(R.string.health_d05_title));
        binding.healthToolbar.inflateMenu(R.menu.health_menu_info);
        TopAppBarHelper.tintMenu(binding.healthToolbar);
        binding.healthToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.health_menu_info) {
                vm.onInfoClick();
                return true;
            }
            return false;
        });
        vm.getShowTip().observe(this, v -> {
            View anchor = binding.healthToolbar.findViewById(R.id.health_menu_info);
            TipPopup.showRich(anchor != null ? anchor : binding.healthToolbar,
                    getString(R.string.health_tip_withdrawal_title), getString(R.string.health_tip_withdrawal), null, null);
        });

        adapter = new MedicationRecordAdapter(this::showRecordMenu);
        FooterLoadStateAdapter footer = new FooterLoadStateAdapter(adapter::retry);
        adapter.addLoadStateListener(states -> {
            footer.setLoadState(states.getAppend());
            boolean empty = states.getRefresh() instanceof LoadState.NotLoading && adapter.getItemCount() == 0;
            header.healthRecordEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (states.getRefresh() instanceof LoadState.Error && adapter.getItemCount() > 0) {
                showMessage(getString(R.string.health_refresh_failed));
            }
            return Unit.INSTANCE;
        });
        binding.healthList.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()), adapter, footer));
        binding.healthList.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int position) {
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
        vm.getWithdrawal().observe(this, this::renderWithdrawal);
        vm.getRecords().observe(this, data -> adapter.submitData(getLifecycle(), data));
        vm.getRecordTotal().observe(this, total -> header.healthRecordTitle.setTitle(
                getString(R.string.health_record_section, total == null ? 0 : total)));
        header.healthRecordTitle.setTitle(getString(R.string.health_record_section, 0));

        ClickThrottle.setOnClickListener(binding.healthFab, v -> vm.onFabClick());
        vm.getOpenRegister().observe(this, batchId -> {
            Bundle extras = new Bundle();
            if (batchId != null) {
                extras.putLong(RouterPath.EXTRA_BATCH_ID, batchId);
            }
            HealthNav.open(this, childLauncher, RouterPath.D04, extras);
        });
        vm.getOpenEdit().observe(this, id -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_RECORD_ID, id);
            HealthNav.open(this, childLauncher, RouterPath.D04, extras);
        });
        vm.getOpenChangeRequest().observe(this, id -> {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_BIZ_TYPE, HealthExtras.BIZ_MEDICATION_RECORD);
            extras.putLong(RouterPath.EXTRA_BIZ_ID, id);
            HealthNav.open(this, childLauncher, RouterPath.U01, extras);
        });
        vm.getOpenRequest().observe(this, id -> {
            Bundle extras = new Bundle();
            if (id != null) {
                extras.putLong(RouterPath.EXTRA_REQUEST_ID, id);
            }
            Router.open(this, RouterPath.U02, extras);
        });
        vm.getShowLogs().observe(this, id -> RecordLogSheet.show(getSupportFragmentManager(),
                HealthExtras.BIZ_MEDICATION_RECORD, id, null));

        // 删除：普通确认（NO_CHANGE_TODAY）
        vm.getShowDeleteConfirm().observe(this, impact -> ConfirmDialogFragment.builder(REQ_DELETE_CONFIRM)
                .title(getString(R.string.health_delete_record_title))
                .message(vm.deleteConfirmMessage(impact))
                .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                .positive(getString(R.string.health_action_delete))
                .danger(true)
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DELETE_CONFIRM, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.deleteDirectly();
            } else {
                vm.cancelDelete();
            }
        });
        // 删除：提交修改申请（图 8-8，申请原因 200 字）
        vm.getShowDeleteRequest().observe(this, this::showDeleteRequest);
        getSupportFragmentManager().setFragmentResultListener(REQ_DELETE_REQUEST, this, (key, result) -> {
            if (ReasonDialogFragment.isConfirmed(result)) {
                vm.submitDeleteRequest(ReasonDialogFragment.getReason(result));
            } else {
                vm.cancelDelete();
            }
        });
        vm.getSubmitting().observe(this, s -> ReasonDialogFragment.setLoading(getSupportFragmentManager(),
                REQ_DELETE_REQUEST, Boolean.TRUE.equals(s)));
        vm.getCloseDeleteRequest().observe(this, v -> ReasonDialogFragment.dismiss(getSupportFragmentManager(),
                REQ_DELETE_REQUEST));
        // 员工：不能直接删除（图 8-9）
        vm.getShowStaffRestricted().observe(this, impact -> ConfirmDialogFragment.builder(REQ_STAFF)
                .icon(com.zhimu.slmp.base.R.drawable.ic_lock)
                .title(getString(R.string.health_staff_cannot_delete))
                .message(titleLine(impact) + MedicationArchiveViewModel.staffRestrictedMessage(impact))
                .negative(getString(R.string.health_got_it))
                .positive(getString(R.string.health_notify_who, MedicationArchiveViewModel.notifyWho(impact)))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_STAFF, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.notifyManager();
            } else {
                vm.cancelDelete();
            }
        });

        vm.getPickBatch().observe(this, this::showBatchPick);
        getSupportFragmentManager().setFragmentResultListener(REQ_BATCH, this, (key, result) -> {
            String v = OptionSheetDialogFragment.getValue(result);
            if (v != null) {
                vm.start(Long.parseLong(v));
            }
        });
    }

    /** 对话框正文首行：“氟苯尼考粉 · 10月5日–10月9日” */
    @NonNull
    private static String titleLine(@NonNull DeleteImpactVO impact) {
        return impact.getRecordTitle() == null ? "" : impact.getRecordTitle() + "\n";
    }

    private void showDeleteRequest(@NonNull DeleteImpactVO impact) {
        ReasonDialogFragment.builder(REQ_DELETE_REQUEST)
                .icon(com.zhimu.slmp.base.R.drawable.ic_edit_note)
                .title(getString(R.string.health_delete_record_title))
                .message(titleLine(impact) + MedicationArchiveViewModel.deleteRequestMessage(impact))
                .label(getString(R.string.health_change_reason))
                .helper(getString(R.string.health_change_reason_helper))
                .maxLength(HealthFormRules.CHANGE_REASON_MAX)
                .positive(getString(R.string.health_submit_change_request))
                .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                .keepOpenOnConfirm()
                .build()
                .showSafely(getSupportFragmentManager());
    }

    /** 记录菜单（按 actions）：待审核时只有“查看修改申请”“查看修改记录” */
    private void showRecordMenu(@NonNull View anchor, @NonNull MedicationRecordVO r) {
        List<String> actions = r.getActions();
        MoreMenuHelper.Builder menu = MoreMenuHelper.with(anchor);
        if (actions.contains(HealthExtras.ACTION_VIEW_REQUEST)) {
            menu.add(MENU_VIEW_REQUEST, getString(R.string.health_view_change_request),
                    com.zhimu.slmp.base.R.drawable.ic_pending_actions);
        }
        if (actions.contains(HealthExtras.ACTION_EDIT)) {
            menu.add(MENU_EDIT, getString(R.string.health_edit_medication), com.zhimu.slmp.base.R.drawable.ic_edit);
        }
        if (actions.contains(HealthExtras.ACTION_CHANGE_REQUEST) && !vm.isStaff()) {
            menu.addReview(MENU_CHANGE, getString(R.string.health_submit_change_request),
                    com.zhimu.slmp.base.R.drawable.ic_edit_note);
        }
        if (actions.contains(HealthExtras.ACTION_VIEW_LOG)) {
            menu.add(MENU_LOG, getString(R.string.health_view_log), com.zhimu.slmp.base.R.drawable.ic_history);
        }
        if (actions.contains(HealthExtras.ACTION_DELETE)) {
            menu.addDanger(MENU_DELETE, getString(R.string.health_action_delete), com.zhimu.slmp.base.R.drawable.ic_delete);
        }
        menu.setOnItemClickListener(id -> {
            switch (id) {
                case MENU_EDIT:
                    vm.onRecordAction(r, HealthExtras.ACTION_EDIT);
                    break;
                case MENU_CHANGE:
                    vm.onRecordAction(r, HealthExtras.ACTION_CHANGE_REQUEST);
                    break;
                case MENU_VIEW_REQUEST:
                    vm.onRecordAction(r, HealthExtras.ACTION_VIEW_REQUEST);
                    break;
                case MENU_LOG:
                    vm.onRecordAction(r, HealthExtras.ACTION_VIEW_LOG);
                    break;
                default:
                    vm.onRecordAction(r, HealthExtras.ACTION_DELETE);
                    break;
            }
        }).show();
    }

    /**
     * 休药状态卡：休药中（warning，“休药中 · 剩 10 天”“可出栏 10月29日 周四”、进度、末次用药与休药天数、提醒说明，员工只留一句）、
     * 已到期（success）、暂无用药记录（中性）。
     */
    private void renderWithdrawal(@Nullable WithdrawalVO w) {
        if (w == null) {
            return;
        }
        String unit = HealthFormat.countUnit(CoreInjector.get(this).farmContext());
        header.healthBatchNo.setText(w.getBatchNo());
        header.healthBatchSummary.setText(HealthFormat.nonNull(w.getPenName()) + HealthFormat.DOT + "存栏 "
                + HealthFormat.qty(w.getCurrentCount(), unit));
        String status = HealthFormat.nonNull(w.getStatus());
        int bg;
        int fg;
        int icon;
        boolean showProgress = true;
        if (WithdrawalCalculator.STATUS_IN_WITHDRAWAL.equals(status)) {
            bg = com.zhimu.slmp.base.R.attr.colorWarningContainer;
            fg = com.zhimu.slmp.base.R.attr.colorOnWarningContainer;
            icon = com.zhimu.slmp.base.R.drawable.ic_hourglass_top;
            header.healthWithdrawalStatus.setText(getString(R.string.health_withdrawal_in,
                    w.getDaysLeft() == null ? 0 : w.getDaysLeft()));
            header.healthReleaseDate.setText(getString(R.string.health_release_on, HealthFormat.dateWeek(w.getReleaseDate())));
            String note = getString(R.string.health_withdrawal_note_in);
            if (!vm.isStaff() && w.getRemindText() != null) {
                note += w.getRemindText();
            }
            header.healthWithdrawalNote.setText(note);
        } else if (WithdrawalCalculator.STATUS_ENDED.equals(status)) {
            bg = com.zhimu.slmp.base.R.attr.colorSuccessContainer;
            fg = com.zhimu.slmp.base.R.attr.colorOnSuccessContainer;
            icon = com.zhimu.slmp.base.R.drawable.ic_check_circle;
            header.healthWithdrawalStatus.setText(R.string.health_withdrawal_ended);
            header.healthReleaseDate.setText(getString(R.string.health_release_on, HealthFormat.dateWeek(w.getReleaseDate())));
            header.healthWithdrawalNote.setText(R.string.health_withdrawal_note_ended);
        } else {
            bg = com.google.android.material.R.attr.colorSurfaceContainerHighest;
            fg = com.google.android.material.R.attr.colorOnSurface;
            icon = com.zhimu.slmp.base.R.drawable.ic_medication;
            showProgress = false;
            header.healthWithdrawalStatus.setText(R.string.health_withdrawal_none);
            header.healthReleaseDate.setText(R.string.health_withdrawal_none_text);
            header.healthWithdrawalNote.setText(null);
        }
        int bgColor = MaterialColors.getColor(header.healthWithdrawalCard, bg);
        int fgColor = MaterialColors.getColor(header.healthWithdrawalCard, fg);
        header.healthWithdrawalCard.setCardBackgroundColor(bgColor);
        header.healthWithdrawalStatus.setTextColor(fgColor);
        header.healthWithdrawalStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        header.healthWithdrawalStatus.setCompoundDrawableTintList(ColorStateList.valueOf(fgColor));
        header.healthReleaseDate.setTextColor(fgColor);
        header.healthReleaseDate.setTextAppearance(showProgress ? com.zhimu.slmp.base.R.style.TextAppearance_Zmt_HeadlineSmall
                : com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
        header.healthReleaseDate.setTextColor(fgColor);
        header.healthLastMed.setTextColor(fgColor);
        header.healthWithdrawalDays.setTextColor(fgColor);
        header.healthWithdrawalNote.setTextColor(fgColor);
        header.healthWithdrawalNote.setVisibility(header.healthWithdrawalNote.getText().length() == 0 ? View.GONE
                : View.VISIBLE);
        header.healthProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        header.healthProgressLabels.setVisibility(showProgress && w.getLastMedDate() != null ? View.VISIBLE : View.GONE);
        if (showProgress) {
            double p = w.getProgress() != null ? w.getProgress() : WithdrawalCalculator.STATUS_ENDED.equals(status) ? 1d
                    : WithdrawalCalculator.progress(com.zhimu.slmp.base.util.DateFormatter.parseDate(w.getLastMedDate()),
                    w.getWithdrawalDays() == null ? 0 : w.getWithdrawalDays(), com.zhimu.slmp.base.util.DateFormatter.today());
            header.healthProgress.setProgressCompat((int) Math.round(p * 100), false);
            header.healthProgress.setIndicatorColor(MaterialColors.getColor(header.healthProgress,
                    WithdrawalCalculator.STATUS_ENDED.equals(status) ? com.zhimu.slmp.base.R.attr.colorSuccess
                            : com.zhimu.slmp.base.R.attr.colorWarning));
            header.healthProgress.setTrackColor(getColor(com.zhimu.slmp.base.R.color.zm_surface_container_lowest));
            header.healthLastMed.setText(getString(R.string.health_last_med, HealthFormat.date(w.getLastMedDate())));
            header.healthWithdrawalDays.setText(getString(R.string.health_withdrawal_days,
                    w.getWithdrawalDays() == null ? 0 : w.getWithdrawalDays()));
        }
        header.healthDrugGroup.setVisibility(w.getDrugs().isEmpty() ? View.GONE : View.VISIBLE);
        WithdrawalDrugAdapter.bind(header.healthDrugRows, w.getDrugs());
    }

    private void showBatchPick(@NonNull List<MedicationTargetVO> list) {
        String unit = HealthFormat.countUnit(CoreInjector.get(this).farmContext());
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQ_BATCH)
                .title(getString(R.string.health_pick_batch));
        for (MedicationTargetVO t : list) {
            if (t.getBatchId() != null) {
                b.option(String.valueOf(t.getBatchId()), HealthFormat.nonNull(t.getBatchNo()),
                        HealthFormat.nonNull(t.getPenName()) + HealthFormat.DOT + "存栏 "
                                + HealthFormat.qty(t.getCurrentCount(), unit));
            }
        }
        b.build().showSafely(getSupportFragmentManager());
    }
}
