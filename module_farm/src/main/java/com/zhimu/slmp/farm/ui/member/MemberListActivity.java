package com.zhimu.slmp.farm.ui.member;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmActivityMembersBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.model.vo.InviteItemVO;
import com.zhimu.slmp.farm.ui.FarmUi;

import java.util.List;

import kotlin.Unit;

/**
 * F07 成员管理：ConcatAdapter（成员标题、MemberPagingAdapter、邀请标题、InvitePagingAdapter），按筛选增删段；
 * 所有者有扩展 FAB“邀请成员”与“重新邀请”，场长只读；子页 F08、F09 返回后刷新并提示。
 */
public class MemberListActivity extends BaseActivity {

    private FarmActivityMembersBinding binding;
    private MemberListViewModel vm;
    private final SectionAdapter memberHeader = new SectionAdapter();
    private final SectionAdapter inviteHeader = new SectionAdapter();
    private MemberPagingAdapter memberAdapter;
    private InvitePagingAdapter inviteAdapter;
    private OnBackPressedCallback searchBack;
    private boolean membersNotLoading;
    private boolean invitesNotLoading;

    private final ActivityResultLauncher<Intent> childLauncher = Router.registerForResult(this, result -> {
        Intent data = result.getData();
        String msg = data == null ? null : data.getStringExtra(FarmExtras.RESULT_MESSAGE);
        String[] pens = data == null ? null : data.getStringArrayExtra(FarmExtras.RESULT_UNASSIGNED_PENS);
        if (result.getResultCode() == RESULT_OK || msg != null) {
            vm.onChildResult(msg, pens);
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.farm_activity_members);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(FarmInjector.factory(), MemberListViewModel.class);
        observeBase(vm);

        FarmUi.setupTopBar(binding.topBar, getString(R.string.farm_members_title));
        TopAppBarHelper.inflateMenu(binding.topBar.baseToolbar, R.menu.farm_menu_search);
        binding.topBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.farm_menu_search) {
                showSearch(true);
                return true;
            }
            return false;
        });
        FarmContextVO ctx = CoreInjector.get(this).farmContext().current();
        FarmUi.setupNoPermission(binding.state, ctx == null ? null : ctx.getName());
        binding.state.setOnRetryListener(v -> vm.retry());

        memberAdapter = new MemberPagingAdapter(vm::onMemberClick);
        inviteAdapter = new InvitePagingAdapter(vm::onReinviteClick);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(new ConcatAdapter(memberHeader, memberAdapter, inviteHeader, inviteAdapter));
        binding.list.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int adapterPosition) {
                if (view != null) {
                    return view instanceof MaterialCardView;
                }
                RecyclerView.ViewHolder vh = parent.findViewHolderForAdapterPosition(adapterPosition);
                return vh != null && vh.itemView instanceof MaterialCardView;
            }
        });
        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 4) {
                    binding.fabInvite.shrink();
                } else if (dy < -4) {
                    binding.fabInvite.extend();
                }
            }
        });
        memberAdapter.addLoadStateListener(states -> {
            membersNotLoading = notLoading(states);
            binding.list.post(this::renderSections);
            return Unit.INSTANCE;
        });
        inviteAdapter.addLoadStateListener(states -> {
            invitesNotLoading = notLoading(states);
            binding.list.post(this::renderSections);
            return Unit.INSTANCE;
        });
        binding.refresh.setOnRefreshListener(() -> {
            vm.refresh();
            binding.refresh.setRefreshing(false);
        });

        binding.filterGroup.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chip_all : ids.get(0);
            String f = id == R.id.chip_manager ? MemberListViewModel.FILTER_MANAGER
                    : id == R.id.chip_staff ? MemberListViewModel.FILTER_STAFF
                    : id == R.id.chip_invite ? MemberListViewModel.FILTER_INVITE : MemberListViewModel.FILTER_ALL;
            vm.onFilterChanged(f);
        });
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.onKeywordChanged(s.toString());
            }
        });
        binding.searchBack.setOnClickListener(v -> showSearch(false));
        searchBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showSearch(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, searchBack);
        ClickThrottle.setOnClickListener(binding.fabInvite, v -> vm.onInviteClick());

        observeViewModel();
    }

    private void observeViewModel() {
        vm.getUiState().observe(this, binding.state::render);
        vm.getMembers().observe(this, data -> memberAdapter.submitData(getLifecycle(), data));
        vm.getInvites().observe(this, data -> inviteAdapter.submitData(getLifecycle(), data));
        vm.getMemberHeader().observe(this, t -> renderSections());
        vm.getInviteHeader().observe(this, t -> renderSections());
        vm.getSelectedFilter().observe(this, f -> renderSections());
        vm.getOnlyOwnerHint().observe(this, h -> renderSections());
        vm.getCanInvite().observe(this, can -> {
            boolean c = Boolean.TRUE.equals(can);
            binding.fabInvite.setVisibility(c ? View.VISIBLE : View.GONE);
            inviteAdapter.setCanInvite(c);
        });
        vm.getUnassignedBanner().observe(this, this::renderBanner);
        vm.getOpenDetail().observe(this, memberId -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_FARM_ID, vm.getFarmId());
            extras.putLong(RouterPath.EXTRA_MEMBER_ID, memberId);
            Router.openForResult(this, childLauncher, RouterPath.F09, extras);
        });
        vm.getOpenInvite().observe(this, this::openInvite);
    }

    private void openInvite(@Nullable InviteItemVO reinvite) {
        Bundle extras = new Bundle();
        extras.putLong(RouterPath.EXTRA_FARM_ID, vm.getFarmId());
        if (reinvite != null) {
            extras.putString(FarmExtras.EXTRA_REINVITE_JSON, new Gson().toJson(reinvite));
        }
        Router.openForResult(this, childLauncher, RouterPath.F08, extras);
    }

    private void renderBanner(@Nullable List<String> names) {
        boolean show = names != null && !names.isEmpty();
        binding.unassignedBanner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.unassignedBanner.setText(getString(R.string.farm_members_unassigned, FarmUi.joinNames(names)));
        }
    }

    private void renderSections() {
        String filter = vm.getSelectedFilter().getValue();
        boolean invite = MemberListViewModel.FILTER_INVITE.equals(filter);
        boolean all = filter == null || MemberListViewModel.FILTER_ALL.equals(filter);
        String keyword = vm.getKeyword().getValue();
        boolean searching = !TextUtils.isEmpty(keyword == null ? null : keyword.trim());

        boolean membersLoaded = membersNotLoading;
        memberHeader.setVisible(!invite);
        memberHeader.setTitle(vm.getMemberHeader().getValue());
        String memberMsg = null;
        if (!invite && memberAdapter.getItemCount() == 0 && membersLoaded) {
            memberMsg = MemberListViewModel.FILTER_MANAGER.equals(filter) ? getString(R.string.farm_members_empty_manager)
                    : MemberListViewModel.FILTER_STAFF.equals(filter) ? getString(R.string.farm_members_empty_staff)
                    : searching ? getString(R.string.farm_members_empty_search) : null;
        } else if (all && !searching && Boolean.TRUE.equals(vm.getOnlyOwnerHint().getValue())) {
            memberMsg = getString(R.string.farm_members_only_owner);
        }
        memberHeader.setMessage(memberMsg);

        boolean inviteEmpty = inviteAdapter.getItemCount() == 0;
        inviteHeader.setVisible(invite || (all && !inviteEmpty));
        inviteHeader.setTitle(vm.getInviteHeader().getValue());
        inviteHeader.setMessage(invite && inviteEmpty && invitesNotLoading
                ? getString(searching ? R.string.farm_members_empty_search : R.string.farm_members_empty_invite)
                : null);
    }

    private void showSearch(boolean show) {
        binding.searchBar.setVisibility(show ? View.VISIBLE : View.GONE);
        searchBack.setEnabled(show);
        if (show) {
            binding.searchInput.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.searchInput, 0);
            }
        } else {
            binding.searchInput.setText("");
            hideKeyboard(binding.searchInput);
        }
    }

    private static boolean notLoading(@NonNull CombinedLoadStates states) {
        LoadState refresh = states.getRefresh();
        return refresh instanceof LoadState.NotLoading;
    }
}
