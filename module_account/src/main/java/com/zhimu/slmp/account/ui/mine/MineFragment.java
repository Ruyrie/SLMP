package com.zhimu.slmp.account.ui.mine;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.zhimu.slmp.account.BuildConfig;
import com.zhimu.slmp.account.databinding.AccountFragmentMineBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.util.NextStepRouter;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;

import java.util.List;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.common.service.TabRegistry;

/**
 * A07 我的（MainActivity“我的”标签）：首次进入 load，返回时不全量刷新；切换养殖场、从 T05 / F01 返回时 refresh。
 */
public class MineFragment extends BaseFragment {

    private static final String REQ_QUIT_FARM = "account_mine_quit_farm";

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
        // Activity 范围：切换或退出养殖场后 MainActivity 会按新角色重建标签页，ViewModel 与未消费的事件要保留下来
        vm = obtainActivityViewModel(AccountInjector.factory(), MineViewModel.class);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        observeBase(vm);
        vm.setVersionName(versionName());
        vm.getAvatarUrl().observe(getViewLifecycleOwner(), this::renderAvatar);

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
        ClickThrottle.setOnClickListener(binding.accountMineQuitFarm, v -> vm.onQuitFarmClick());
        vm.getConfirmQuitFarm().observe(getViewLifecycleOwner(), name -> ConfirmDialogFragment.builder(REQ_QUIT_FARM)
                .icon(com.zhimu.slmp.base.R.drawable.ic_logout)
                .title(getString(R.string.account_mine_quit_title, name))
                .message(getString(R.string.account_mine_quit_message, name))
                .positive(getString(R.string.account_mine_quit_confirm))
                .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                .danger(true)
                .keepOpenOnConfirm()
                .build()
                .showSafely(getChildFragmentManager()));
        getChildFragmentManager().setFragmentResultListener(REQ_QUIT_FARM, getViewLifecycleOwner(), (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.quitFarm();
            }
        });
        vm.getSubmitting().observe(getViewLifecycleOwner(), s ->
                ConfirmDialogFragment.setLoading(getChildFragmentManager(), REQ_QUIT_FARM, Boolean.TRUE.equals(s)));
        vm.getQuitFarmDone().observe(getViewLifecycleOwner(), message -> {
            ConfirmDialogFragment.dismiss(getChildFragmentManager(), REQ_QUIT_FARM);
            if (requireActivity() instanceof MainTabHost) {
                ((MainTabHost) requireActivity()).selectTab(TabRegistry.TODAY);
            }
            GlobalEvents.postMessage(message);
        });

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

    /**
     * 个人信息卡头像：有 avatar_url 时 Glide 加载圆形图片，加载成功隐藏姓名首字；
     * 地址为空或加载失败时保留姓名首字回退。
     */
    private void renderAvatar(@Nullable String url) {
        if (binding == null) {
            return;
        }
        ImageView image = binding.accountMineAvatarImage;
        View initial = binding.accountMineAvatarInitial;
        if (url == null || url.isEmpty()) {
            Glide.with(this).clear(image);
            image.setVisibility(View.GONE);
            initial.setVisibility(View.VISIBLE);
            return;
        }
        image.setVisibility(View.VISIBLE);
        initial.setVisibility(View.GONE);
        Glide.with(this)
                .load(url)
                .centerCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model,
                                                @NonNull Target<Drawable> target, boolean isFirstResource) {
                        if (binding != null) {
                            image.setVisibility(View.GONE);
                            initial.setVisibility(View.VISIBLE);
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                   @NonNull Target<Drawable> target,
                                                   @NonNull DataSource dataSource, boolean isFirstResource) {
                        if (binding != null) {
                            image.setVisibility(View.VISIBLE);
                            initial.setVisibility(View.GONE);
                        }
                        return false;
                    }
                })
                .into(image);
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
        if (binding != null) {
            Glide.with(this).clear(binding.accountMineAvatarImage);
        }
        binding = null;
        super.onDestroyView();
    }
}
