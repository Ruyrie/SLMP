package com.zhimu.slmp.farm.ui.detail;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.PenBriefVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmActivityMemberDetailBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.model.vo.MemberActionsVO;
import com.zhimu.slmp.farm.model.vo.MemberDetailVO;
import com.zhimu.slmp.farm.model.vo.RemovalImpactVO;
import com.zhimu.slmp.farm.ui.FarmUi;

import java.util.ArrayList;
import java.util.List;

/**
 * F09 成员详情：所有者看员工（修改角色、调整栏舍、移出）、所有者看场长（修改角色、移出）、场长看员工（调整栏舍）、
 * 只读、本人为场长（退出养殖场）。返回 F07 时带回提示与“没有负责员工”的栏舍。
 */
public class MemberDetailActivity extends BaseActivity {

    private static final String REQ_REMOVE = "farm_member_remove";
    private static final String REQ_QUIT = "farm_member_quit";

    private FarmActivityMemberDetailBinding binding;
    private MemberDetailViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.farm_activity_member_detail);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(FarmInjector.factory(), MemberDetailViewModel.class);
        observeBase(vm);

        FarmUi.setupTopBar(binding.topBar, getString(R.string.farm_member_title));
        FarmContextVO ctx = CoreInjector.get(this).farmContext().current();
        FarmUi.setupNoPermission(binding.state, ctx == null ? null : ctx.getName());
        binding.state.setOnRetryListener(v -> vm.retry());
        binding.refresh.setOnRefreshListener(vm::load);
        ClickThrottle.setOnClickListener(binding.call, v -> vm.onCallClick());
        ClickThrottle.setOnClickListener(binding.changeRole, v -> vm.openRoleSheet());
        ClickThrottle.setOnClickListener(binding.adjustPens, v -> vm.openPenSheet());
        ClickThrottle.setOnClickListener(binding.dangerCard, v -> {
            MemberDetailVO m = vm.getMember().getValue();
            if (m == null) {
                return;
            }
            if (m.getActions().isCanQuit()) {
                showQuitDialog(m);
            } else {
                vm.prepareRemove();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_REMOVE, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.remove();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_QUIT, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.quit();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWith(null);
            }
        });

        vm.getUiState().observe(this, s -> {
            binding.state.render(s);
            binding.refresh.setRefreshing(false);
        });
        vm.getRefreshing().observe(this, r -> {
            if (!Boolean.TRUE.equals(r)) {
                binding.refresh.setRefreshing(false);
            }
        });
        vm.getMember().observe(this, this::render);
        vm.getShowRoleSheet().observe(this, v -> RoleSheet.newInstance().showSafely(getSupportFragmentManager()));
        vm.getShowPenSheet().observe(this, v -> PenSheet.newInstance().showSafely(getSupportFragmentManager()));
        vm.getRoleChanged().observe(this, this::showMessage);
        vm.getShowRemoveDialog().observe(this, this::showRemoveDialog);
        vm.getRemoved().observe(this, r -> {
            MemberDetailVO m = vm.getMember().getValue();
            String name = m == null ? "" : nonNull(m.getName());
            String farm = m == null ? "" : nonNull(m.getFarmName());
            finishWith("已把" + name + "移出" + farm);
        });
        vm.getDial().observe(this, phone -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            } catch (android.content.ActivityNotFoundException e) {
                showMessage(phone);
            }
        });
        vm.getQuitDone().observe(this, text -> {
            GlobalEvents.postMessage(text);
            Router.openClearTask(this, RouterPath.MAIN, null);
            finish();
        });
        vm.getGone().observe(this, msg -> {
            setResult(RESULT_OK, new Intent().putExtra(FarmExtras.RESULT_MESSAGE, msg));
            finish();
        });
    }

    private void render(@Nullable MemberDetailVO m) {
        if (m == null) {
            return;
        }
        MemberActionsVO a = m.getActions();
        binding.avatar.setName(m.getName());
        binding.name.setText(m.getName());
        binding.roleTag.setStatus(m.getRoleText(), FarmContextVO.ROLE_OWNER.equals(m.getRole())
                ? StatusTagView.PRIMARY : StatusTagView.NEUTRAL);
        boolean created = "CREATE".equals(m.getJoinType());
        binding.joined.setText(getString(created ? R.string.farm_member_created : R.string.farm_member_joined,
                FarmUi.monthDay(m.getJoinedAt()), nonNull(m.getFarmName())));
        binding.phone.setText(m.getPhoneMasked());
        binding.call.setVisibility(m.getPhone() == null ? View.GONE : View.VISIBLE);
        binding.roleText.setText(m.getRoleText());
        binding.changeRole.setVisibility(a.isCanChangeRole() ? View.VISIBLE : View.GONE);
        binding.adjustPens.setVisibility(a.isCanAdjustPens() ? View.VISIBLE : View.GONE);

        boolean staff = FarmContextVO.ROLE_STAFF.equals(m.getRole()) && !m.isAllPens();
        binding.pensAll.setVisibility(staff && !m.getPens().isEmpty() ? View.GONE : View.VISIBLE);
        binding.pensAll.setText(staff ? getString(R.string.farm_members_no_pens) : getString(R.string.farm_members_all_pens));
        binding.penTags.removeAllViews();
        binding.penTags.setVisibility(staff && !m.getPens().isEmpty() ? View.VISIBLE : View.GONE);
        if (staff) {
            for (PenBriefVO p : m.getPens()) {
                StatusTagView tag = new StatusTagView(this);
                tag.setStatus(p.getName(), StatusTagView.NEUTRAL);
                binding.penTags.addView(tag);
            }
        }

        boolean quit = a.isCanQuit();
        boolean remove = a.isCanRemove();
        binding.dangerCard.setVisibility(quit || remove ? View.VISIBLE : View.GONE);
        if (quit) {
            binding.dangerIcon.setImageResource(com.zhimu.slmp.base.R.drawable.ic_logout);
            binding.dangerTitle.setText(R.string.farm_info_quit);
            binding.dangerNote.setText(getString(R.string.farm_member_quit_note, nonNull(m.getFarmName())));
        } else {
            binding.dangerIcon.setImageResource(com.zhimu.slmp.base.R.drawable.ic_person_remove);
            binding.dangerTitle.setText(R.string.farm_member_remove);
            binding.dangerNote.setText(R.string.farm_member_remove_note);
        }
    }

    private void showRemoveDialog(@Nullable RemovalImpactVO impact) {
        MemberDetailVO m = vm.getMember().getValue();
        if (impact == null || m == null) {
            return;
        }
        String name = impact.getMemberName() != null ? impact.getMemberName() : nonNull(m.getName());
        String farm = nonNull(m.getFarmName());
        StringBuilder message = new StringBuilder(getString(R.string.farm_member_remove_p1, name, farm));
        if (!FarmContextVO.ROLE_MANAGER.equals(impact.getRole())) {
            message.append("\n\n").append(getString(R.string.farm_member_remove_p2));
            List<String> pens = new ArrayList<>();
            for (PenBriefVO p : impact.getOrphanPens()) {
                if (p.getName() != null) {
                    pens.add(p.getName());
                }
            }
            if (!pens.isEmpty()) {
                message.append(getString(R.string.farm_member_remove_p2_pens, FarmUi.joinNames(pens)));
            }
        }
        ConfirmDialogFragment.builder(REQ_REMOVE)
                .icon(com.zhimu.slmp.base.R.drawable.ic_person_remove)
                .title(getString(R.string.farm_member_remove_title, name, farm))
                .message(message.toString())
                .positive(getString(R.string.farm_member_remove_confirm))
                .negative(getString(R.string.farm_action_cancel))
                .danger(true)
                .build()
                .showSafely(getSupportFragmentManager());
    }

    private void showQuitDialog(@NonNull MemberDetailVO m) {
        String farm = nonNull(m.getFarmName());
        ConfirmDialogFragment.builder(REQ_QUIT)
                .icon(com.zhimu.slmp.base.R.drawable.ic_logout)
                .title(getString(R.string.farm_quit_title, farm))
                .message(getString(R.string.farm_quit_message, farm))
                .positive(getString(R.string.farm_quit_confirm))
                .negative(getString(R.string.farm_action_cancel))
                .danger(true)
                .build()
                .showSafely(getSupportFragmentManager());
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (MemberDetailViewModel.ACTION_ASSIGN_PENS.equals(actionId)) {
            vm.openPenSheet();
        }
    }

    /** 返回 F07：有改动时 RESULT_OK，带提示与“没有负责员工”的栏舍 */
    private void finishWith(@Nullable String message) {
        if (vm.isChanged() || message != null) {
            Intent data = new Intent();
            if (message != null) {
                data.putExtra(FarmExtras.RESULT_MESSAGE, message);
            }
            List<String> pens = vm.getUnassigned();
            if (!pens.isEmpty()) {
                data.putExtra(FarmExtras.RESULT_UNASSIGNED_PENS, pens.toArray(new String[0]));
            }
            setResult(RESULT_OK, data);
        }
        finish();
    }

    @NonNull
    private static String nonNull(@Nullable String s) {
        return s == null ? "" : s;
    }
}
