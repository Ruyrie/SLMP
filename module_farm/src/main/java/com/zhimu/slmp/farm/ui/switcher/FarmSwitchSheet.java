package com.zhimu.slmp.farm.ui.switcher;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.status.StatusActivity;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmSheetSwitchBinding;
import com.zhimu.slmp.farm.di.FarmInjector;

import kotlin.Unit;
import com.zhimu.slmp.common.service.IFarmContextService;

/**
 * F01 切换养殖场（底部面板，FarmSwitcher 的 App 形态）：标题“切换养殖场”→“全部养殖场”项 → 分隔线 → 养殖场单选列表，
 * 底部固定色调按钮“新建养殖场”。ViewModel 在宿主 Activity 范围。
 * <p>
 * “全部养殖场”选中、或在全部视角下选了某个养殖场时，经 FragmentResult 回传宿主：requestKey 为 {@link #REQUEST_KEY}，
 * 结果 {@link #RESULT_ALL_FARMS}（boolean）、{@link #RESULT_FARM_ID}（long）。
 */
public class FarmSwitchSheet extends BaseBottomSheetDialogFragment {

    /** 宿主（T03、T06、E02、E13）监听的结果键 */
    public static final String REQUEST_KEY = IFarmContextService.SWITCHER_REQUEST_KEY;
    public static final String RESULT_ALL_FARMS = IFarmContextService.SWITCHER_RESULT_ALL_FARMS;
    public static final String RESULT_FARM_ID = IFarmContextService.SWITCHER_RESULT_FARM_ID;

    private static final String ARG_ALLOW_ALL = "allow_all_farms";
    private static final String ARG_IN_ALL = "in_all_farms";
    private static final long CLOSE_DELAY_MS = 200;

    private FarmSheetSwitchBinding binding;
    private FarmSwitchViewModel vm;
    private FarmSwitchAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @NonNull
    public static FarmSwitchSheet newInstance(boolean allowAllFarms, boolean inAllFarms) {
        Bundle args = baseArgs(REQUEST_KEY, null, null, null);
        args.putBoolean(ARG_ALLOW_ALL, allowAllFarms);
        args.putBoolean(ARG_IN_ALL, inAllFarms);
        FarmSwitchSheet sheet = new FarmSwitchSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.farm_switch_title);
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.farm_switch_create);
    }

    @Override
    protected int getPrimaryButtonIcon() {
        return com.zhimu.slmp.base.R.drawable.ic_add;
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm.onCreateClick();
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = FarmSheetSwitchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(requireActivity(), FarmInjector.factory()).get(FarmSwitchViewModel.class);
        if (savedInstanceState == null) {
            Bundle args = requireArguments();
            vm.setup(args.getBoolean(ARG_ALLOW_ALL), args.getBoolean(ARG_IN_ALL));
        }
        styleCreateButton();

        adapter = new FarmSwitchAdapter(vm::onFarmClick);
        binding.farmSwitchList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.farmSwitchList.setAdapter(adapter);
        adapter.addLoadStateListener(states -> {
            renderLoadState(states.getRefresh());
            return Unit.INSTANCE;
        });
        binding.farmSwitchRetry.setOnClickListener(v -> adapter.retry());
        binding.farmSwitchAll.setOnClickListener(v -> vm.onAllFarmsClick());

        vm.getFarms().observe(getViewLifecycleOwner(),
                data -> adapter.submitData(getViewLifecycleOwner().getLifecycle(), data));
        vm.load();
        vm.getAllFarmsEnabled().observe(getViewLifecycleOwner(), v -> renderAllFarms());
        vm.getAllFarmsChecked().observe(getViewLifecycleOwner(), v -> {
            renderAllFarms();
            renderChecked();
        });
        vm.getCurrentFarm().observe(getViewLifecycleOwner(), c -> renderChecked());
        vm.getSwitchingFarmId().observe(getViewLifecycleOwner(), adapter::setSwitchingFarmId);
        vm.getMessage().observe(getViewLifecycleOwner(), this::showHostMessage);

        vm.getSwitched().observe(getViewLifecycleOwner(), this::onSwitched);
        vm.getOpenAudit().observe(getViewLifecycleOwner(), farmId -> {
            FragmentActivity host = requireActivity();
            dismiss();
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_FARM_ID, farmId);
            Router.open(host, RouterPath.F03, extras);
        });
        vm.getOpenFrozen().observe(getViewLifecycleOwner(), text -> {
            FragmentActivity host = requireActivity();
            dismiss();
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_STATUS_TYPE, StatusActivity.TYPE_NO_PERMISSION);
            extras.putString(RouterPath.EXTRA_TITLE, text);
            extras.putString(RouterPath.EXTRA_PAGE_TITLE, getString(R.string.farm_switch_title));
            Router.open(host, RouterPath.U03, extras);
        });
        vm.getAllFarmsChosen().observe(getViewLifecycleOwner(), v -> {
            Bundle result = new Bundle();
            result.putBoolean(RESULT_ALL_FARMS, true);
            handler.postDelayed(() -> {
                if (isAdded()) {
                    setResultAndDismiss(result);
                }
            }, CLOSE_DELAY_MS);
        });
        vm.getOpenCreate().observe(getViewLifecycleOwner(), v -> {
            FragmentActivity host = requireActivity();
            dismiss();
            Router.open(host, RouterPath.F02);
        });
    }

    private void onSwitched(@Nullable FarmContextVO ctx) {
        renderChecked();
        if (ctx != null && Boolean.TRUE.equals(vm.getAllFarmsEnabled().getValue())) {
            Bundle result = new Bundle();
            result.putBoolean(RESULT_ALL_FARMS, false);
            result.putLong(RESULT_FARM_ID, ctx.getFarmId());
            setResult(result);
        }
        // 单选切换后 200 ms 关闭面板，不弹 Snackbar（UI 文档 2.4 节）
        handler.postDelayed(() -> {
            if (isAdded()) {
                dismiss();
            }
        }, CLOSE_DELAY_MS);
    }

    private void renderAllFarms() {
        boolean enabled = Boolean.TRUE.equals(vm.getAllFarmsEnabled().getValue());
        boolean checked = Boolean.TRUE.equals(vm.getAllFarmsChecked().getValue());
        binding.farmSwitchAllRadio.setChecked(enabled && checked);
        binding.farmSwitchAllRadio.setEnabled(enabled);
        binding.farmSwitchAllTitle.setTextColor(MaterialColors.getColor(binding.farmSwitchAllTitle, enabled
                ? com.google.android.material.R.attr.colorOnSurface
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    private void renderChecked() {
        boolean all = Boolean.TRUE.equals(vm.getAllFarmsChecked().getValue());
        FarmContextVO current = vm.getCurrentFarm().getValue();
        long currentId = current == null ? -1 : current.getFarmId();
        adapter.setCheckedFarmId(all ? -1 : currentId, currentId);
    }

    private void renderLoadState(@NonNull LoadState refresh) {
        boolean empty = adapter.getItemCount() == 0;
        binding.farmSwitchLoading.setVisibility(refresh instanceof LoadState.Loading && empty
                ? View.VISIBLE : View.GONE);
        binding.farmSwitchError.setVisibility(refresh instanceof LoadState.Error && empty
                ? View.VISIBLE : View.GONE);
        binding.farmSwitchEmpty.setVisibility(refresh instanceof LoadState.NotLoading && empty
                ? View.VISIBLE : View.GONE);
    }

    /** 色调按钮：secondary-container 底 + on-secondary-container 文字与图标 */
    private void styleCreateButton() {
        MaterialButton button = getPrimaryButton();
        if (button == null) {
            return;
        }
        int bg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorSecondaryContainer);
        int fg = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSecondaryContainer);
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(fg);
        button.setIconTint(ColorStateList.valueOf(fg));
    }

    private void showHostMessage(@Nullable UiMessage message) {
        if (message != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showMessage(message);
        }
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        binding = null;
    }
}
