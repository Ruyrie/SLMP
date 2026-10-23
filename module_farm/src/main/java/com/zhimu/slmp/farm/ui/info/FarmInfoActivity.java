package com.zhimu.slmp.farm.ui.info;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.FarmPermissionVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmActivityInfoBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.model.vo.FarmBasicVO;
import com.zhimu.slmp.farm.model.vo.FarmDetailVO;
import com.zhimu.slmp.farm.model.vo.RegistrationVO;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.farm.util.CountUnitText;

/**
 * F05 养殖场信息：所有者版、场长版（注册信息只读、只有“成员管理”和“退出养殖场”）、已停用、资料变更审核中 / 被驳回、转让待确认。
 * 行与按钮显隐只取 FarmPermissionVO。
 */
public class FarmInfoActivity extends BaseActivity {

    private static final String REQ_DISABLE = "farm_info_disable";
    private static final String REQ_ENABLE = "farm_info_enable";
    private static final String REQ_QUIT = "farm_info_quit";

    private FarmActivityInfoBinding binding;
    private FarmInfoViewModel vm;

    private final ActivityResultLauncher<Intent> childLauncher = Router.registerForResult(this, result -> {
        vm.refresh();
        Intent data = result.getData();
        String msg = data == null ? null : data.getStringExtra(FarmExtras.RESULT_MESSAGE);
        if (result.getResultCode() == RESULT_OK && msg != null) {
            showMessage(msg);
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.farm_activity_info);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(FarmInjector.factory(), FarmInfoViewModel.class);
        observeBase(vm);

        FarmUi.setupTopBar(binding.topBar, getString(R.string.farm_info_title));
        FarmContext farmContext = CoreInjector.get(this).farmContext();
        FarmContextVO ctx = farmContext.current();
        FarmUi.setupNoPermission(binding.state, ctx == null ? null : ctx.getName());
        binding.state.setOnRetryListener(v -> vm.retry());
        binding.refresh.setOnRefreshListener(vm::refresh);

        for (ListRowView row : new ListRowView[]{binding.rowContactName, binding.rowContactPhone, binding.rowScale,
                binding.rowRegName, binding.rowRegAddress, binding.rowRegQual}) {
            FarmUi.trailingBodyMedium(row);
        }
        binding.rowIntro.setSubtitleMaxLines(2);
        ClickThrottle.setOnClickListener(binding.actionMembers, v -> vm.onMembersClick());
        ClickThrottle.setOnClickListener(binding.actionTransfer, v -> vm.onTransferClick());
        ClickThrottle.setOnClickListener(binding.actionDisable, v -> vm.onDisableClick());
        ClickThrottle.setOnClickListener(binding.actionEnable, v -> vm.onEnableClick());
        ClickThrottle.setOnClickListener(binding.actionQuit, v -> vm.onQuitClick());

        getSupportFragmentManager().setFragmentResultListener(REQ_DISABLE, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.disable();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_ENABLE, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.enable();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_QUIT, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.quit();
            }
        });
        observeViewModel();
    }

    private void observeViewModel() {
        vm.getUiState().observe(this, this::renderState);
        vm.getRefreshing().observe(this, r -> {
            if (!Boolean.TRUE.equals(r)) {
                binding.refresh.setRefreshing(false);
            }
        });
        vm.getDetail().observe(this, d -> render());
        vm.getBasic().observe(this, b -> renderBasic());
        vm.getPermissions().observe(this, p -> render());
        vm.getChangeBanner().observe(this, b -> render());
        vm.getTransferText().observe(this, t -> binding.transferText.setText(
                t == null ? null : getString(R.string.farm_info_transfer_pending, t)));
        vm.getShowEditSheet().observe(this, field ->
                BasicFieldSheet.newInstance(field).showSafely(getSupportFragmentManager()));
        vm.getShowConfirm().observe(this, this::showConfirm);
        vm.getOpenChange().observe(this, target -> openChange(target, null));
        vm.getOpenMembers().observe(this, v -> openChild(RouterPath.F07));
        vm.getOpenTransfer().observe(this, v -> openChild(RouterPath.F10));
        vm.getQuitDone().observe(this, text -> {
            GlobalEvents.postMessage(text);
            Router.openClearTask(this, RouterPath.MAIN, null);
            finish();
        });
    }

    private void renderState(@Nullable UiState state) {
        binding.state.render(state);
        if (state != null && !state.is(UiState.Status.CONTENT)) {
            binding.refresh.setRefreshing(false);
        }
    }

    private void render() {
        FarmDetailVO d = vm.getDetail().getValue();
        FarmPermissionVO p = vm.getPermissions().getValue();
        if (d == null || p == null) {
            return;
        }
        boolean disabled = FarmContextVO.STATUS_DISABLED.equals(d.getStatus());
        boolean owner = FarmContextVO.ROLE_OWNER.equals(d.getRole());
        FarmUi.setupNoPermission(binding.state, d.getName());
        binding.farmName.setText(d.getName());
        if (disabled) {
            binding.statusTag.setStatus(d.getStatusText() != null ? d.getStatusText() : "已停用", StatusTagView.ERROR);
            binding.statusTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_block);
        } else {
            binding.statusTag.setStatus("已通过", StatusTagView.NEUTRAL);
            binding.statusTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_verified);
        }
        binding.roleLine.setText(getString(R.string.farm_info_role_line, nonNull(d.getRoleText()),
                nonNull(d.getMainCategoryName())));
        binding.disabledBanner.setVisibility(disabled ? View.VISIBLE : View.GONE);
        binding.disabledBanner.setText(!TextUtils.isEmpty(d.getRestrictTip()) ? d.getRestrictTip()
                : d.getName() + "已停用。数据只能查看，不能登记新的记录。");

        // 注册信息
        RegistrationVO r = d.getRegistration();
        boolean canChange = p.isCanSubmitChange();
        if (r != null) {
            binding.rowRegName.setTrailingText(r.getName());
            binding.rowRegAddress.setTrailingText(r.getAddress());
            binding.rowRegQual.setTrailingText(getString(R.string.farm_count_photos, r.qualCount()));
        }
        for (ListRowView row : new ListRowView[]{binding.rowRegName, binding.rowRegAddress, binding.rowRegQual}) {
            row.setShowChevron(canChange);
        }
        if (canChange) {
            binding.rowRegName.setOnClickListener(v -> vm.onRegistrationRowClick(FarmExtras.TARGET_NAME));
            binding.rowRegAddress.setOnClickListener(v -> vm.onRegistrationRowClick(FarmExtras.TARGET_ADDRESS));
            binding.rowRegQual.setOnClickListener(v -> vm.onRegistrationRowClick(FarmExtras.TARGET_QUALIFICATION));
        } else {
            binding.rowRegName.setOnClickListener(null);
            binding.rowRegAddress.setOnClickListener(null);
            binding.rowRegQual.setOnClickListener(null);
        }
        boolean pendingChange = d.getPendingChange() != null;
        binding.registrationTag.setVisibility(pendingChange ? View.VISIBLE : View.GONE);
        binding.registrationTag.setStatus(getString(R.string.farm_info_status_pending), StatusTagView.WARNING);
        binding.registrationNote.setVisibility(pendingChange ? View.GONE : View.VISIBLE);
        binding.registrationNote.setText(owner ? R.string.farm_info_registration_note
                : R.string.farm_info_registration_note_manager);
        renderChangeNotice(d, canChange);

        // 操作分组
        binding.membersCount.setText(d.getMemberSummary() == null ? null
                : getString(R.string.farm_info_members_count, d.getMemberSummary().getMemberTotal()));
        binding.actionMembers.setVisibility(p.isCanViewMembers() ? View.VISIBLE : View.GONE);
        binding.actionTransfer.setVisibility(p.isCanTransfer() ? View.VISIBLE : View.GONE);
        binding.actionDisable.setVisibility(p.isCanDisable() && !disabled ? View.VISIBLE : View.GONE);
        binding.actionEnable.setVisibility(p.isCanEnable() && disabled ? View.VISIBLE : View.GONE);
        binding.actionQuit.setVisibility(p.isCanQuit() ? View.VISIBLE : View.GONE);
        binding.actions.updatePositions();
        renderBasic();
    }

    private void renderChangeNotice(@NonNull FarmDetailVO d, boolean canChange) {
        NoticeView notice = binding.changeNotice;
        if (d.getPendingChange() != null) {
            notice.setLevel(NoticeView.LEVEL_NORMAL);
            notice.setIcon(com.zhimu.slmp.base.R.drawable.ic_info);
            notice.setNotice(null, getString(R.string.farm_info_change_pending,
                    FarmUi.monthDay(d.getPendingChange().getSubmittedAt())));
            notice.setAction(null, null);
        } else if (d.getRejectedChange() != null) {
            notice.setLevel(NoticeView.LEVEL_ERROR);
            notice.setIcon(com.zhimu.slmp.base.R.drawable.ic_rule);
            notice.setNotice(getString(R.string.farm_info_change_rejected), d.getRejectedChange().getRejectReason());
            notice.setAction(canChange ? getString(R.string.farm_info_change_reedit) : null,
                    canChange ? v -> openChange(FarmExtras.TARGET_NAME, FarmExtras.MODE_REEDIT) : null);
        } else {
            notice.setLevel(NoticeView.LEVEL_NORMAL);
            notice.setIcon(com.zhimu.slmp.base.R.drawable.ic_info);
            notice.setNotice(null, getString(R.string.farm_info_notice));
            notice.setAction(null, null);
        }
    }

    private void renderBasic() {
        FarmBasicVO b = vm.getBasic().getValue();
        FarmDetailVO d = vm.getDetail().getValue();
        FarmPermissionVO p = vm.getPermissions().getValue();
        if (b == null || d == null) {
            return;
        }
        boolean editable = p != null && p.isCanEditBasic() && !FarmContextVO.STATUS_DISABLED.equals(d.getStatus());
        binding.rowContactName.setTrailingText(b.getContactName());
        binding.rowContactPhone.setTrailingText(b.getContactPhoneMasked());
        binding.rowScale.setTrailingText(getString(R.string.farm_info_scale_value,
                NumberFormatter.formatQuantity(b.getScale(), CountUnitText.of(d.getCountUnit()))));
        binding.rowIntro.setSubtitle(b.getIntro());
        String[] fields = {FarmInfoViewModel.FIELD_CONTACT_NAME, FarmInfoViewModel.FIELD_CONTACT_PHONE,
                FarmInfoViewModel.FIELD_SCALE, FarmInfoViewModel.FIELD_INTRO};
        ListRowView[] rows = {binding.rowContactName, binding.rowContactPhone, binding.rowScale, binding.rowIntro};
        for (int i = 0; i < rows.length; i++) {
            String field = fields[i];
            rows[i].setTrailingIcon(editable ? com.zhimu.slmp.base.R.drawable.ic_edit : 0);
            rows[i].setOnClickListener(editable ? v -> vm.openEdit(field) : null);
        }
        binding.basicNote.setVisibility(editable ? View.VISIBLE : View.GONE);
    }

    private void showConfirm(@Nullable String type) {
        FarmDetailVO d = vm.getDetail().getValue();
        String name = d == null ? "" : nonNull(d.getName());
        ConfirmDialogFragment.Builder b;
        String key;
        if (FarmInfoViewModel.CONFIRM_DISABLE.equals(type)) {
            key = REQ_DISABLE;
            b = ConfirmDialogFragment.builder(key)
                    .icon(com.zhimu.slmp.base.R.drawable.ic_block)
                    .title(getString(R.string.farm_disable_title, name))
                    .message(getString(R.string.farm_disable_message, name))
                    .positive(getString(R.string.farm_disable_confirm))
                    .danger(true);
        } else if (FarmInfoViewModel.CONFIRM_ENABLE.equals(type)) {
            key = REQ_ENABLE;
            b = ConfirmDialogFragment.builder(key)
                    .icon(com.zhimu.slmp.base.R.drawable.ic_restart_alt)
                    .title(getString(R.string.farm_enable_title, name))
                    .message(getString(R.string.farm_enable_message))
                    .positive(getString(R.string.farm_enable_confirm));
        } else {
            key = REQ_QUIT;
            b = ConfirmDialogFragment.builder(key)
                    .icon(com.zhimu.slmp.base.R.drawable.ic_logout)
                    .title(getString(R.string.farm_quit_title, name))
                    .message(getString(R.string.farm_quit_message, name))
                    .positive(getString(R.string.farm_quit_confirm))
                    .danger(true);
        }
        b.negative(getString(R.string.farm_action_cancel)).build().showSafely(getSupportFragmentManager());
    }

    private void openChange(@Nullable String target, @Nullable String mode) {
        String json = vm.registrationJson();
        if (json == null) {
            return;
        }
        Bundle extras = new Bundle();
        extras.putLong(RouterPath.EXTRA_FARM_ID, vm.getFarmId());
        extras.putString(FarmExtras.EXTRA_REGISTRATION_JSON, json);
        extras.putString(RouterPath.EXTRA_SCROLL_TARGET, target);
        if (mode != null) {
            extras.putString(RouterPath.EXTRA_MODE, mode);
        }
        Router.openForResult(this, childLauncher, RouterPath.F06, extras);
    }

    private void openChild(@NonNull String action) {
        Bundle extras = new Bundle();
        extras.putLong(RouterPath.EXTRA_FARM_ID, vm.getFarmId());
        Router.openForResult(this, childLauncher, action, extras);
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (FarmInfoViewModel.ACTION_REFRESH.equals(actionId)) {
            vm.refresh();
        }
    }

    @NonNull
    private static String nonNull(@Nullable String s) {
        return s == null ? "" : s;
    }
}
