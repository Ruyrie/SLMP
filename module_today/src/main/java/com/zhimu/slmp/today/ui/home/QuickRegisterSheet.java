package com.zhimu.slmp.today.ui.home;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayItemQuickCellBinding;
import com.zhimu.slmp.today.databinding.TodaySheetQuickRegisterBinding;
import com.zhimu.slmp.today.di.TodayInjector;

import java.util.List;

/**
 * T02 快捷登记面板（第 4 级）：“作业”组 8 项，所有者与场长另有“养殖户”组 4 项；选中一项后面板先收起（250 ms）再进入登记页。
 * 与 TodayFragment 共用 TodayViewModel（以父 Fragment 为范围），只登记到当前养殖场。
 */
public class QuickRegisterSheet extends BaseBottomSheetDialogFragment {

    public static final String TAG = "today_quick_register";
    private static final long NAVIGATE_DELAY_MS = 250;

    private TodayViewModel vm;
    private boolean navigating;
    @Nullable
    private TodaySheetQuickRegisterBinding binding;

    public static void show(@NonNull FragmentManager fm) {
        QuickRegisterSheet sheet = new QuickRegisterSheet();
        sheet.setArguments(baseArgs(null, null, null, null));
        sheet.showSafely(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(getParentFragment() != null ? requireParentFragment() : requireActivity(),
                TodayInjector.factory()).get(TodayViewModel.class);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.today_quick_title);
    }

    @Nullable
    @Override
    protected CharSequence getSheetDescription() {
        FarmBriefVO farm = vm.getCurrentFarm().getValue();
        String name = farm == null || farm.getName() == null ? "" : farm.getName();
        return getString(R.string.today_quick_desc, name);
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = TodaySheetQuickRegisterBinding.inflate(inflater, container, false);
        ClickThrottle.setOnClickListener(binding.stockBack, v -> showStockIn(false));
        buildStockIn(inflater);
        vm.getCurrentFarm().observe(this, farm -> setDescription(getSheetDescription()));
        vm.getQuickItems().observe(this, this::render);
        return binding.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // 二级“选择入库类型”中按返回键先回到上一层
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && binding != null
                    && binding.stockIn.getVisibility() == View.VISIBLE) {
                showStockIn(false);
                return true;
            }
            return false;
        });
        return dialog;
    }

    private void render(@Nullable List<QuickRegisterItem> items) {
        if (binding == null || items == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        binding.gridWork.removeAllViews();
        binding.gridOwner.removeAllViews();
        boolean hasOwner = false;
        for (QuickRegisterItem item : items) {
            GridLayout grid = item.isOwnerGroup() ? binding.gridOwner : binding.gridWork;
            hasOwner |= item.isOwnerGroup();
            grid.addView(cell(inflater, grid, item));
        }
        binding.groupOwner.setVisibility(hasOwner ? View.VISIBLE : View.GONE);
        binding.gridOwner.setVisibility(hasOwner ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private View cell(@NonNull LayoutInflater inflater, @NonNull GridLayout grid, @NonNull QuickRegisterItem item) {
        TodayItemQuickCellBinding c = TodayItemQuickCellBinding.inflate(inflater, grid, false);
        c.icon.setImageResource(item.getIcon());
        int bg = item.isOwnerGroup()
                ? com.google.android.material.R.attr.colorSurfaceContainerHighest
                : com.google.android.material.R.attr.colorSecondaryContainer;
        int fg = item.isOwnerGroup()
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSecondaryContainer;
        c.icon.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(c.icon, bg)));
        c.icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(c.icon, fg)));
        c.name.setText(item.getName());
        c.getRoot().setContentDescription(getString(item.getName()));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        c.getRoot().setLayoutParams(lp);
        ClickThrottle.setOnClickListener(c.getRoot(), v -> onItemClick(item));
        return c.getRoot();
    }

    private void buildStockIn(@NonNull LayoutInflater inflater) {
        if (binding == null) {
            return;
        }
        binding.stockList.removeAllViews();
        for (QuickRegisterItem item : QuickRegisterItem.STOCK_IN) {
            ListRowView row = new ListRowView(inflater.getContext());
            row.setLeadingIcon(item.getIcon(), ListRowView.TONE_NEUTRAL);
            row.setTitle(getString(item.getName()));
            row.setShowChevron(true);
            row.setOnClickListener(v -> onItemClick(item));
            binding.stockList.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        binding.stockList.updatePositions();
    }

    private void showStockIn(boolean show) {
        if (binding == null) {
            return;
        }
        binding.main.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.stockIn.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void onItemClick(@NonNull QuickRegisterItem item) {
        if (QuickRegisterItem.ROUTE_STOCK_IN.equals(item.getRoute())) {
            showStockIn(true);
            return;
        }
        TodayViewModel model = vm;
        navigating = true;
        dismiss();
        new Handler(Looper.getMainLooper()).postDelayed(() -> model.onQuickItemClick(item), NAVIGATE_DELAY_MS);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (!navigating && getActivity() instanceof QuickRegisterActivity
                && !requireActivity().isChangingConfigurations()) {
            requireActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
