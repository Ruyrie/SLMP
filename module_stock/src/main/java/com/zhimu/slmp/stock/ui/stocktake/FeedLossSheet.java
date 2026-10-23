package com.zhimu.slmp.stock.ui.stocktake;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockSheetFeedLossBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.ui.common.SimpleTextWatcher;

import java.util.Map;

/**
 * 登记报损底部面板（M04，图 9-4）：不建 ViewModel，读写 Activity 范围的 StocktakeViewModel。
 * 报损日期固定今天；数量实时与结存比较；“登记报损”校验通过后提交，成功由页面关闭面板并提示。
 */
public class FeedLossSheet extends BaseBottomSheetDialogFragment {

    public static final String TAG = "stock_feed_loss";

    private static final String[][] REASONS = {
            {StockEnums.LOSS_MILDEW, "霉变"}, {StockEnums.LOSS_DAMP, "受潮"}, {StockEnums.LOSS_RODENT, "鼠害"},
            {StockEnums.LOSS_EXPIRED, "过期"}, {StockEnums.LOSS_OTHER, "其他"}};

    private StockSheetFeedLossBinding binding;
    private StocktakeViewModel vm;

    @NonNull
    public static FeedLossSheet newInstance() {
        FeedLossSheet s = new FeedLossSheet();
        s.setArguments(baseArgs(null, null, null, null));
        return s;
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), StockInjector.factory()).get(StocktakeViewModel.class);
        binding = StockSheetFeedLossBinding.inflate(inflater, container, false);
        setTitle(vm.lossTitle());
        setDescription(vm.lossSubtitle());
        binding.stockLossQtyLayout.setSuffixText(vm.lossUnit());
        String qty = vm.getLossQty().getValue();
        binding.stockLossQty.setText(qty);
        binding.stockLossRemark.setText(vm.getLossRemark().getValue());
        for (String[] r : REASONS) {
            Chip chip = (Chip) inflater.inflate(R.layout.stock_item_filter_chip, binding.stockLossReasons, false);
            chip.setId(View.generateViewId());
            chip.setText(r[1]);
            chip.setTag(r[0]);
            chip.setCheckable(true);
            chip.setChecked(r[0].equals(vm.getLossReason().getValue()));
            binding.stockLossReasons.addView(chip);
        }
        binding.stockLossReasons.setOnCheckedStateChangeListener((group, ids) -> {
            String reason = ids.isEmpty() ? null : (String) group.findViewById(ids.get(0)).getTag();
            vm.onLossReasonPicked(reason);
            binding.stockLossRemarkLayout.setHint(StockEnums.LOSS_OTHER.equals(reason)
                    ? getString(R.string.stock_field_remark) : getString(R.string.stock_field_remark_optional));
        });
        binding.stockLossQty.addTextChangedListener(new SimpleTextWatcher(vm::onLossQtyChanged));
        binding.stockLossRemark.addTextChangedListener(new SimpleTextWatcher(t -> vm.getLossRemark().setValue(t)));
        binding.stockLossCancel.setOnClickListener(v -> dismiss());
        binding.stockLossSubmit.setOnClickListener(v -> {
            vm.submitLoss();
            Map<String, String> e = vm.getLossErrors().getValue();
            if (e != null && e.containsKey(StocktakeViewModel.F_LOSS_QTY)) {
                binding.stockLossQty.requestFocus();
            }
        });
        if (savedInstanceState == null) {
            binding.stockLossQty.post(() -> {
                if (binding != null && binding.stockLossQty.requestFocus()) {
                    InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
                    if (imm != null) {
                        imm.showSoftInput(binding.stockLossQty, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm.getLossErrors().observe(getViewLifecycleOwner(), this::renderErrors);
        vm.getSubmitting().observe(getViewLifecycleOwner(),
                s -> ButtonLoading.setLoading(binding.stockLossSubmit, Boolean.TRUE.equals(s)));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void renderErrors(@Nullable Map<String, String> e) {
        if (binding == null) {
            return;
        }
        String qty = e == null ? null : e.get(StocktakeViewModel.F_LOSS_QTY);
        binding.stockLossQtyLayout.setError(qty);
        binding.stockLossQtyLayout.setErrorEnabled(!TextUtils.isEmpty(qty));
        String reason = e == null ? null : e.get(StocktakeViewModel.F_LOSS_REASON);
        binding.stockLossReasonError.setText(reason);
        binding.stockLossReasonError.setVisibility(TextUtils.isEmpty(reason) ? View.GONE : View.VISIBLE);
        String remark = e == null ? null : e.get(StocktakeViewModel.F_LOSS_REMARK);
        binding.stockLossRemarkLayout.setError(remark);
        binding.stockLossRemarkLayout.setErrorEnabled(!TextUtils.isEmpty(remark));
    }
}
