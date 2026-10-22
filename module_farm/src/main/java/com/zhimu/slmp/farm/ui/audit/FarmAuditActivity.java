package com.zhimu.slmp.farm.ui.audit;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmActivityAuditBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.model.vo.AuditRecordVO;
import com.zhimu.slmp.farm.model.vo.FarmAuditVO;
import com.zhimu.slmp.farm.model.vo.QualFileVO;
import com.zhimu.slmp.farm.model.vo.RegistrationVO;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.farm.util.CountUnitText;
import com.zhimu.slmp.media.ui.viewer.MediaViewer;
import com.zhimu.slmp.media.ui.viewer.ViewerItem;

import java.util.ArrayList;
import java.util.List;

/**
 * F03 审核结果：已驳回（error 图标底块、驳回原因卡、“1 张需重拍”）、待审核（warning、底部描边按钮“修改注册信息”）；
 * 更多菜单“删除养殖场”（确认对话框）；资质图行打开全屏预览。
 */
public class FarmAuditActivity extends BaseActivity {

    private static final String REQ_DELETE = "farm_audit_delete";
    private static final int MENU_DELETE = 1;

    private FarmActivityAuditBinding binding;
    private FarmAuditViewModel vm;

    private final ActivityResultLauncher<Intent> editLauncher = Router.registerForResult(this, result -> {
        if (result.getResultCode() == RESULT_OK) {
            vm.load();
            Intent data = result.getData();
            String msg = data == null ? null : data.getStringExtra(FarmExtras.RESULT_MESSAGE);
            if (msg != null) {
                showMessage(msg);
            }
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.farm_activity_audit);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(FarmInjector.factory(), FarmAuditViewModel.class);
        observeBase(vm);

        FarmUi.setupTopBar(binding.topBar, getString(R.string.farm_audit_title));
        TopAppBarHelper.inflateMenu(binding.topBar.baseToolbar, R.menu.farm_menu_more);
        binding.topBar.baseToolbar.getMenu().findItem(R.id.farm_menu_more).setVisible(false);
        binding.topBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.farm_menu_more) {
                View anchor = binding.topBar.baseToolbar.findViewById(R.id.farm_menu_more);
                MoreMenuHelper.with(anchor != null ? anchor : binding.topBar.baseToolbar)
                        .addDanger(MENU_DELETE, getString(R.string.farm_audit_menu_delete),
                                com.zhimu.slmp.base.R.drawable.ic_delete)
                        .setOnItemClickListener(id -> vm.onDeleteMenuClick())
                        .show();
                return true;
            }
            return false;
        });
        FarmUi.trailingBodyMedium(binding.rowName);
        FarmUi.trailingBodyMedium(binding.rowAddress);
        FarmUi.trailingBodyMedium(binding.rowScale);
        FarmUi.trailingBodyMedium(binding.rowCategory);
        FarmUi.trailingBodyMedium(binding.rowContact);
        FarmUi.trailingBodyMedium(binding.rowQual);
        binding.rowQual.setOnClickListener(v -> vm.onQualClick(0));
        binding.actionBar.setOnPrimaryClickListener(v -> vm.onEditClick());
        binding.actionBar.setOnSecondaryClickListener(v -> vm.onEditClick());
        binding.state.setOnRetryListener(v -> vm.retry());

        if (savedInstanceState == null) {
            String msg = getIntent().getStringExtra(RouterPath.EXTRA_MESSAGE);
            if (!TextUtils.isEmpty(msg)) {
                binding.getRoot().post(() -> showMessage(msg));
            }
        }
        getSupportFragmentManager().setFragmentResultListener(REQ_DELETE, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.delete();
            }
        });
        observeViewModel();
    }

    private void observeViewModel() {
        vm.getUiState().observe(this, binding.state::render);
        vm.getAudit().observe(this, this::render);
        vm.getTimeLine().observe(this, binding.timeLine::setText);
        vm.getOpenEdit().observe(this, farmId -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_FARM_ID, farmId);
            extras.putString(RouterPath.EXTRA_MODE, FarmExtras.MODE_EDIT);
            Router.openForResult(this, editLauncher, RouterPath.F02, extras);
        });
        vm.getShowDeleteDialog().observe(this, name -> {
            String n = name == null ? "" : name;
            ConfirmDialogFragment.builder(REQ_DELETE)
                    .icon(com.zhimu.slmp.base.R.drawable.ic_delete)
                    .title(getString(R.string.farm_audit_delete_title, n))
                    .message(getString(R.string.farm_audit_delete_message, n))
                    .positive(getString(R.string.farm_audit_delete_confirm))
                    .negative(getString(R.string.farm_action_cancel))
                    .danger(true)
                    .build()
                    .showSafely(getSupportFragmentManager());
        });
        vm.getDeleted().observe(this, name -> {
            GlobalEvents.postMessage(getString(R.string.farm_audit_deleted, name));
            Router.openClearTask(this, RouterPath.MAIN, null);
            finish();
        });
        vm.getApproved().observe(this, farmId -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_FARM_ID, farmId);
            Router.open(this, RouterPath.F05, extras);
            finish();
        });
        vm.getOpenViewer().observe(this, index -> openViewer(index == null ? 0 : index));
    }

    private void render(@Nullable FarmAuditVO audit) {
        if (audit == null) {
            return;
        }
        String status = audit.getStatus();
        boolean rejected = FarmContextVO.STATUS_REJECTED.equals(status);
        boolean pending = FarmContextVO.STATUS_PENDING.equals(status);
        binding.farmName.setText(audit.getName());

        int blockAttr = rejected ? com.google.android.material.R.attr.colorErrorContainer
                : pending ? com.zhimu.slmp.base.R.attr.colorWarningContainer
                : com.google.android.material.R.attr.colorSecondaryContainer;
        int iconAttr = rejected ? com.google.android.material.R.attr.colorOnErrorContainer
                : pending ? com.zhimu.slmp.base.R.attr.colorOnWarningContainer
                : com.google.android.material.R.attr.colorOnSecondaryContainer;
        binding.statusIconBlock.setBackgroundTintList(ColorStateList.valueOf(
                MaterialColors.getColor(binding.statusIconBlock, blockAttr)));
        binding.statusIcon.setImageTintList(ColorStateList.valueOf(
                MaterialColors.getColor(binding.statusIcon, iconAttr)));
        if (rejected) {
            binding.statusTag.setStatus(audit.getStatusText() != null ? audit.getStatusText() : "已驳回",
                    StatusTagView.ERROR);
            binding.statusTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_block);
        } else if (pending) {
            binding.statusTag.setStatus(audit.getStatusText() != null ? audit.getStatusText() : "待审核",
                    StatusTagView.WARNING);
            binding.statusTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_schedule);
        } else {
            binding.statusTag.setStatus(audit.getStatusText(), StatusTagView.NEUTRAL);
            binding.statusTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_verified);
        }

        binding.rejectCard.setVisibility(rejected && !TextUtils.isEmpty(audit.getRejectReason())
                ? View.VISIBLE : View.GONE);
        binding.rejectReason.setText(audit.getRejectReason());
        binding.restrictNotice.setVisibility(TextUtils.isEmpty(audit.getRestrictTip()) ? View.GONE : View.VISIBLE);
        binding.restrictNotice.setText(audit.getRestrictTip());

        RegistrationVO r = audit.getRegistration();
        if (r != null) {
            binding.rowName.setTrailingText(r.getName());
            binding.rowAddress.setTrailingText(r.getAddress());
            binding.rowScale.setTrailingText(NumberFormatter.formatQuantity(r.getScale(),
                    CountUnitText.of(r.getCountUnit(), r.getCountUnitText())));
            binding.rowCategory.setTrailingText(r.getMainCategoryName());
            String phone = r.getContactPhoneMasked();
            binding.rowContact.setTrailingText(TextUtils.isEmpty(phone) ? r.getContactName()
                    : r.getContactName() + " · " + phone);
            binding.rowQual.setTrailingText(getString(R.string.farm_count_photos, r.qualCount()));
            int reshoot = audit.getReshootCount() == null ? 0 : audit.getReshootCount();
            binding.rowQual.setStatusTag(rejected && reshoot > 0 ? getString(R.string.farm_reshoot_tag, reshoot) : null,
                    StatusTagView.ERROR, 0);
        }
        renderRecords(audit.getRecords());

        boolean editable = rejected || pending;
        binding.topBar.baseToolbar.getMenu().findItem(R.id.farm_menu_more).setVisible(editable);
        binding.actionBar.setVisibility(editable ? View.VISIBLE : View.GONE);
        if (rejected) {
            binding.actionBar.setPrimary(getString(R.string.farm_audit_edit_resubmit), com.zhimu.slmp.base.R.drawable.ic_edit);
            binding.actionBar.setSecondary(null, 0);
        } else if (pending) {
            binding.actionBar.setPrimary(null, 0);
            binding.actionBar.setSecondary(getString(R.string.farm_audit_edit), 0);
        }
    }

    private void renderRecords(@NonNull List<AuditRecordVO> records) {
        binding.records.removeAllViews();
        for (AuditRecordVO rec : records) {
            ListRowView row = new ListRowView(this);
            row.setTitle(rec.getTypeText());
            String desc = rec.getDesc();
            String time = FarmUi.monthDayTime(rec.getTime());
            row.setSubtitle(TextUtils.isEmpty(desc) ? time : time + " · " + desc);
            row.setSubtitleMaxLines(3);
            String tag = rec.getStatusTag();
            if (!TextUtils.isEmpty(tag)) {
                int type = AuditRecordVO.TYPE_REJECT.equals(rec.getType()) ? StatusTagView.ERROR
                        : AuditRecordVO.TYPE_APPROVE.equals(rec.getType()) ? StatusTagView.SUCCESS
                        : StatusTagView.WARNING;
                row.setStatusTag(tag, type, 0);
            }
            binding.records.addView(row);
        }
        binding.records.updatePositions();
        int v = records.isEmpty() ? View.GONE : View.VISIBLE;
        binding.records.setVisibility(v);
        binding.recordsTitle.setVisibility(v);
    }

    /** 全屏预览资质图，需重拍的在说明中标注 */
    private void openViewer(int index) {
        FarmAuditVO audit = vm.getAudit().getValue();
        RegistrationVO r = audit == null ? null : audit.getRegistration();
        if (r == null || r.getQualificationFiles().isEmpty()) {
            return;
        }
        List<ViewerItem> items = new ArrayList<>();
        for (QualFileVO f : r.getQualificationFiles()) {
            items.add(new ViewerItem(f.getFileId(), f.getUrl(), f.getThumbUrl(), "IMAGE", null,
                    getString(f.isReshoot() ? R.string.farm_qual_source_reshoot : R.string.farm_qual_source),
                    audit.getName()));
        }
        MediaViewer.open(this, items, Math.max(0, Math.min(index, items.size() - 1)));
    }
}
