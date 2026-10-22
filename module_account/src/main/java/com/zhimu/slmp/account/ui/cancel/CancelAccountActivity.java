package com.zhimu.slmp.account.ui.cancel;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityCancelBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.model.vo.CancelExitFarmVO;
import com.zhimu.slmp.account.model.vo.CancelOwnedFarmVO;
import com.zhimu.slmp.account.model.vo.CancellationPreviewVO;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.account.util.NextStepRouter;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A09 注销账号：转让、停用经切换后用 Activity Result 打开 F10、F05，返回后重新加载；导出打开 E13；
 * 注销确认对话框“注销”为 error 色；成功后打开 A01 清空返回栈并提示“账号已注销”。
 */
public class CancelAccountActivity extends BaseActivity {

    /** E13 的 EXTRA_MODE 取值：统计范围为本人创建的养殖场（待与模块10 约定） */
    private static final String REQ_CONFIRM = "account_cancel_confirm";

    private CancelAccountViewModel vm;
    private AccountActivityCancelBinding binding;
    private ActivityResultLauncher<Intent> farmLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(AccountInjector.factory(), CancelAccountViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.account_activity_cancel);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.accountCancelTopBar.baseToolbar, getString(R.string.account_cancel_title));
        TopAppBarHelper.bindLiftOnScroll(binding.accountCancelTopBar.baseAppBar, binding.accountCancelScroll);
        binding.accountCancelState.setOnRetryListener(v -> vm.retry());
        farmLauncher = Router.registerForResult(this, r -> vm.load());

        for (MaterialButton b : new MaterialButton[]{binding.accountCancelTransfer, binding.accountCancelDisable,
                binding.accountCancelExport}) {
            b.setPaintFlags(b.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        }
        ClickThrottle.setOnClickListener(binding.accountCancelTransfer, v -> vm.onTransferClick());
        ClickThrottle.setOnClickListener(binding.accountCancelDisable, v -> vm.onDisableClick());
        ClickThrottle.setOnClickListener(binding.accountCancelExport, v -> vm.onExportClick());
        binding.accountCancelBar.setOnPrimaryClickListener(v -> vm.onCancelClick());

        Map<String, TextInputLayout> fields = new LinkedHashMap<>();
        fields.put(CancelAccountViewModel.FIELD_SMS_CODE, binding.accountCancelSms);
        FormBinder.bindFieldErrors(this, vm.getFieldErrors(), fields);
        FormBinder.bindFocus(this, vm.getFocusField(), fields);
        FormBinder.bindSmsField(binding.accountCancelSms, this, vm.getSmsCountdown(), vm.getSmsSentHint(),
                vm::sendSmsCode);
        vm.getFieldErrors().observe(this, map ->
                binding.accountCancelConfirm.setErrorShown(map != null && map.containsKey(CancelAccountViewModel.FIELD_CONFIRMED)));

        vm.getPreview().observe(this, this::renderList);
        vm.getUiState().observe(this, state -> binding.accountCancelBar.setVisibility(
                state != null && state.is(UiState.Status.ERROR) ? View.GONE : View.VISIBLE));

        vm.getShowFarmPicker().observe(this, action ->
                FarmPickBottomSheet.show(this, getSupportFragmentManager(), action, vm.getPickFarms().getValue() == null
                        ? new ArrayList<>() : vm.getPickFarms().getValue()));
        getSupportFragmentManager().setFragmentResultListener(FarmPickBottomSheet.REQUEST_KEY, this, (key, result) -> {
            Long id = FarmPickBottomSheet.farmIdOf(result);
            if (id != null) {
                vm.onFarmPicked(id);
            }
        });
        vm.getOpenFarmPage().observe(this, farmId -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_FARM_ID, farmId);
            String action = CancelAccountViewModel.ACTION_DISABLE.equals(vm.getPendingFarmAction())
                    ? RouterPath.F05 : RouterPath.F10;
            Router.openForResult(this, farmLauncher, action, extras);
        });
        vm.getOpenExport().observe(this, b -> {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_MODE, RouterPath.MODE_OWNED_FARMS);
            Router.open(this, RouterPath.E13, extras);
        });
        vm.getShowConfirmDialog().observe(this, message -> ConfirmDialogFragment.builder(REQ_CONFIRM)
                .title(getString(R.string.account_cancel_dialog_title))
                .message(message)
                .positive(getString(R.string.account_cancel_dialog_positive))
                .negative(getString(R.string.account_cancel))
                .danger(true)
                .keepOpenOnConfirm()
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_CONFIRM, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.confirmCancel();
            }
        });
        // 确认后对话框保持打开，“注销”按钮显示提交中；失败时恢复，可重试或取消（成功后直接回到 A01）
        vm.getSubmitting().observe(this, s -> {
            ConfirmDialogFragment.setLoading(getSupportFragmentManager(), REQ_CONFIRM, Boolean.TRUE.equals(s));
        });
        vm.getCancelled().observe(this, d -> NextStepRouter.openLogin(this, null, CancelAccountViewModel.MSG_CANCELLED));
    }

    private void renderList(@Nullable CancellationPreviewVO p) {
        ViewGroup list = binding.accountCancelList;
        list.removeAllViews();
        if (p == null) {
            return;
        }
        int error = MaterialColors.getColor(list, androidx.appcompat.R.attr.colorError);

        ListRowView personal = new ListRowView(this);
        personal.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_person, ListRowView.TONE_NEUTRAL);
        personal.setTitle(getString(R.string.account_cancel_personal));
        String desc = p.getPersonalDataDesc() == null ? getString(R.string.account_cancel_personal_default)
                : p.getPersonalDataDesc();
        personal.setSubtitle(desc + "\n" + getString(R.string.account_cancel_personal_disposal));
        list.addView(personal);

        for (CancelOwnedFarmVO f : p.getOwnedFarms()) {
            ListRowView row = new ListRowView(this);
            int tone = f.isBlocking() ? ListRowView.TONE_WARNING
                    : CancelOwnedFarmVO.DISPOSAL_DELETE_NOW.equals(f.getDisposal()) ? ListRowView.TONE_ERROR
                    : ListRowView.TONE_NEUTRAL;
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_domain, tone);
            row.setTitle(f.getName());
            SpannableStringBuilder sb = new SpannableStringBuilder(CancelAccountViewModel.farmLine1(f)).append("\n");
            int start = sb.length();
            sb.append(CancelAccountViewModel.farmLine2(f));
            if (CancelAccountViewModel.farmLine2IsError(f)) {
                sb.setSpan(new ForegroundColorSpan(error), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            row.setSubtitle(sb);
            row.setStatusTag(getString(R.string.account_cancel_members, f.getMemberCount()), StatusTagView.NEUTRAL,
                    com.zhimu.slmp.base.R.drawable.ic_group);
            list.addView(row);
        }

        List<String> exitNames = new ArrayList<>();
        for (CancelExitFarmVO f : p.getExitFarms()) {
            exitNames.add(f.getName() == null ? "" : f.getName());
        }
        binding.accountCancelExit.setVisibility(exitNames.isEmpty() ? View.GONE : View.VISIBLE);
        binding.accountCancelExit.setTitle("退出" + String.join("、", exitNames));
    }
}
