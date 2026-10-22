package com.zhimu.slmp.farm.ui.transfer;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmActivityTransferBinding;
import com.zhimu.slmp.farm.databinding.FarmItemTransferCandidateBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.model.vo.TransferCandidateVO;
import com.zhimu.slmp.farm.model.vo.TransferFarmVO;
import com.zhimu.slmp.farm.model.vo.TransferPageVO;
import com.zhimu.slmp.farm.model.vo.TransferVO;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.farm.util.CountUnitText;

import java.util.List;

/**
 * F10 转让所有权：候选场长单选（只有一位时默认选中）、后果列表姓名随选择替换、确认后返回 F05；
 * 待对方确认时候选只读、横幅说明并提供“撤回转让”（确认对话框）。
 */
public class TransferActivity extends BaseActivity {

    private static final String REQ_CANCEL = "farm_transfer_cancel";

    private FarmActivityTransferBinding binding;
    private TransferViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.farm_activity_transfer);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(FarmInjector.factory(), TransferViewModel.class);
        observeBase(vm);

        FarmUi.setupTopBar(binding.topBar, getString(R.string.farm_transfer_title));
        FarmContextVO ctx = CoreInjector.get(this).farmContext().current();
        FarmUi.setupNoPermission(binding.state, ctx == null ? null : ctx.getName());
        binding.state.setOnRetryListener(v -> vm.retry());
        ClickThrottle.setOnClickListener(binding.goMembers, v -> vm.onGoMembersClick());
        binding.actionBar.setOnPrimaryClickListener(v -> vm.confirm());
        binding.actionBar.setOnSecondaryClickListener(v -> showCancelDialog());
        getSupportFragmentManager().setFragmentResultListener(REQ_CANCEL, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.cancelTransfer();
            }
        });

        vm.getUiState().observe(this, binding.state::render);
        vm.getPage().observe(this, p -> render());
        vm.getSelectedMemberId().observe(this, id -> render());
        vm.getSelectedName().observe(this, n -> render());
        vm.getPending().observe(this, p -> render());
        vm.getEmptyState().observe(this, e -> render());
        vm.getSubmitting().observe(this, s -> binding.actionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getTransferred().observe(this, msg -> {
            setResult(RESULT_OK, new Intent().putExtra(FarmExtras.RESULT_MESSAGE, msg));
            finish();
        });
        vm.getCanceled().observe(this, v -> setResult(RESULT_OK));
        vm.getOpenMembers().observe(this, v -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_FARM_ID, vm.getFarmId());
            Router.open(this, RouterPath.F07, extras);
        });
    }

    private void render() {
        TransferPageVO p = vm.getPage().getValue();
        if (p == null) {
            return;
        }
        TransferFarmVO farm = p.getFarm();
        String farmName = farm == null || farm.getName() == null ? "" : farm.getName();
        binding.farmName.setText(farmName);
        if (farm != null) {
            binding.farmLine.setText(getString(R.string.farm_transfer_head, nonNull(farm.getMainCategoryName()),
                    NumberFormatter.formatQuantity(farm.getCurrentStock(), CountUnitText.of(farm.getCountUnit()))));
        }
        boolean empty = Boolean.TRUE.equals(vm.getEmptyState().getValue());
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.content.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyText.setText(getString(R.string.farm_transfer_empty, farmName));

        TransferVO pending = vm.getPending().getValue();
        Long selected = vm.getSelectedMemberId().getValue();
        renderCandidates(p.getCandidates(), selected, pending == null);

        String name = vm.getSelectedName().getValue();
        String shown = TextUtils.isEmpty(name) ? getString(R.string.farm_transfer_new_owner_placeholder) : name;
        binding.rowNewOwner.setTitle(getString(R.string.farm_transfer_new_owner, shown));
        if (pending != null) {
            binding.notice.setNotice(null, getString(R.string.farm_transfer_pending_notice,
                    pending.getToName() != null ? pending.getToName() : shown, farmName));
        } else {
            binding.notice.setNotice(getString(R.string.farm_transfer_notice_bold),
                    getString(R.string.farm_transfer_notice, shown, farmName));
        }

        binding.actionBar.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (pending != null) {
            binding.actionBar.setPrimary(null, 0);
            binding.actionBar.setSecondary(getString(R.string.farm_transfer_cancel), 0);
        } else {
            binding.actionBar.setSecondary(null, 0);
            binding.actionBar.setPrimary(getString(R.string.farm_transfer_confirm), 0);
            binding.actionBar.setPrimaryEnabled(selected != null);
        }
    }

    private void renderCandidates(List<TransferCandidateVO> list, @Nullable Long selected, boolean selectable) {
        binding.candidates.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (TransferCandidateVO c : list) {
            FarmItemTransferCandidateBinding row = FarmItemTransferCandidateBinding.inflate(inflater,
                    binding.candidates, false);
            row.candidateAvatar.setName(c.getName());
            row.candidateName.setText(c.getName());
            row.candidateSub.setText(getString(R.string.farm_role_manager) + " · " + nonNull(c.getPhoneMasked()));
            row.candidateRadio.setChecked(selected != null && selected == c.getMemberId());
            if (selectable) {
                row.getRoot().setOnClickListener(v -> vm.select(c.getMemberId()));
            }
            binding.candidates.addView(row.getRoot());
        }
        binding.candidates.updatePositions();
    }

    private void showCancelDialog() {
        TransferVO pending = vm.getPending().getValue();
        TransferPageVO p = vm.getPage().getValue();
        String farmName = p == null || p.getFarm() == null ? "" : nonNull(p.getFarm().getName());
        String to = pending == null ? "" : nonNull(pending.getToName());
        ConfirmDialogFragment.builder(REQ_CANCEL)
                .title(getString(R.string.farm_transfer_cancel_title))
                .message(getString(R.string.farm_transfer_cancel_message, to, farmName))
                .positive(getString(R.string.farm_transfer_cancel_confirm))
                .negative(getString(R.string.farm_action_cancel))
                .build()
                .showSafely(getSupportFragmentManager());
    }

    private static String nonNull(@Nullable String s) {
        return s == null ? "" : s;
    }
}
