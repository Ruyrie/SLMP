package com.zhimu.slmp.account.ui.mine;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.account.BuildConfig;
import com.zhimu.slmp.account.databinding.AccountFragmentMineBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.util.NextStepRouter;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;

import java.util.List;

/**
 * A07 我的（MainActivity“我的”标签）：首次进入 load，返回时不全量刷新；切换养殖场、从 T05 / F01 返回时 refresh。
 */
public class MineFragment extends BaseFragment {

    private static final String TAG_EDIT_NAME = "account_edit_name";

    private AccountFragmentMineBinding binding;
    private MineViewModel vm;
    private ActivityResultLauncher<Intent> refreshLauncher;
    @Nullable
    private Long lastFarmId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshLauncher = Router.registerForResult(this, r -> {
            if (vm != null) {
                vm.refresh();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AccountFragmentMineBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = obtainViewModel(AccountInjector.factory(), MineViewModel.class);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        observeBase(vm);
        vm.setVersionName(versionName());

        binding.accountMineTopBar.bind(requireActivity(), getViewLifecycleOwner());
        binding.accountMineTopBar.bindLiftOnScroll(binding.accountMineScroll);
        binding.accountMineRefresh.setOnRefreshListener(vm::refresh);
        binding.accountMineState.setOnRetryListener(v -> vm.retry());

        ClickThrottle.setOnClickListener(binding.accountMineProfile, v -> vm.openEditName());
        ClickThrottle.setOnClickListener(binding.accountMineFarms, v -> vm.onEntryClick(MineViewModel.ENTRY_FARMS));
        ClickThrottle.setOnClickListener(binding.accountMineChangeRequests,
                v -> vm.onEntryClick(MineViewModel.ENTRY_CHANGE_REQUESTS));
        ClickThrottle.setOnClickListener(binding.accountMineReminder, v -> vm.onEntryClick(MineViewModel.ENTRY_REMINDER));
        ClickThrottle.setOnClickListener(binding.accountMineSecurity, v -> vm.onEntryClick(MineViewModel.ENTRY_SECURITY));
        ClickThrottle.setOnClickListener(binding.accountMineAbout, v -> vm.onEntryClick(MineViewModel.ENTRY_ABOUT));
        ClickThrottle.setOnClickListener(binding.accountMineLogout, v -> vm.logout());

        vm.getFarmTags().observe(getViewLifecycleOwner(), this::renderFarmTags);
        vm.getFarmChanged().observe(getViewLifecycleOwner(), id -> {
            if (lastFarmId != null && !lastFarmId.equals(id)) {
                vm.refresh();
            }
            lastFarmId = id;
        });
        vm.getNavigate().observe(getViewLifecycleOwner(), this::navigate);
        vm.getShowNameSheet().observe(getViewLifecycleOwner(), show -> {
            if (getChildFragmentManager().findFragmentByTag(TAG_EDIT_NAME) == null) {
                new EditNameBottomSheet().showSafely(getChildFragmentManager(), TAG_EDIT_NAME);
            }
        });
        vm.getLoggedOut().observe(getViewLifecycleOwner(), done ->
                NextStepRouter.openLogin(requireActivity(), null, MineViewModel.MSG_LOGGED_OUT));
    }

    private void renderFarmTags(@Nullable List<String> tags) {
        LinearLayout box = binding.accountMineFarmTags;
        box.removeAllViews();
        if (tags == null) {
            return;
        }
        int gap = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_2);
        for (String t : tags) {
            StatusTagView tag = new StatusTagView(requireContext());
            tag.setStatus(t, StatusTagView.WARNING);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(gap);
            box.addView(tag, lp);
        }
    }

    private void navigate(@NonNull String entry) {
        switch (entry) {
            case MineViewModel.ENTRY_FARMS:
                if (vm.hasNoFarm()) {
                    Router.openForResult(requireContext(), refreshLauncher, RouterPath.F02, null);
                    return;
                }
                IFarmContextService farm = ServiceHub.get(IFarmContextService.class);
                if (farm != null) {
                    farm.showSwitcher(requireActivity(), false, false);
                } else {
                    Router.open(requireContext(), RouterPath.F01);
                }
                break;
            case MineViewModel.ENTRY_CHANGE_REQUESTS:
                Router.openForResult(requireContext(), refreshLauncher, RouterPath.U02, null);
                break;
            case MineViewModel.ENTRY_REMINDER:
                Router.openForResult(requireContext(), refreshLauncher, RouterPath.T05, null);
                break;
            case MineViewModel.ENTRY_SECURITY:
                Router.open(requireContext(), RouterPath.A08);
                break;
            case MineViewModel.ENTRY_ABOUT:
                Router.open(requireContext(), RouterPath.A07_ABOUT);
                break;
            default:
                break;
        }
    }

    private String versionName() {
        try {
            String v = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            return v != null ? v : BuildConfig.APP_VERSION_NAME;
        } catch (Exception e) {
            return BuildConfig.APP_VERSION_NAME;
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
