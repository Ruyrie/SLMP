package com.zhimu.slmp.finance.ui.salechange;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.widget.KeyValueRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivitySaleChangeBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleChangePreviewVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.ui.common.FinanceNav;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.math.BigDecimal;
import java.util.Map;

/**
 * E08 出栏单修改申请（图 10-12）。
 */
public class SaleChangeRequestActivity extends FinancePageActivity {

    static final String REQ_DISCARD = "finance_e08_discard";

    private FinanceActivitySaleChangeBinding binding;
    private SaleChangeRequestViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivitySaleChangeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), SaleChangeRequestViewModel.class);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e08_title), binding.financeScroll);
        bindState(binding.financeState, vm);
        binding.financeModeGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) {
                vm.setMode(id == R.id.finance_mode_quantity ? FinanceConst.MODE_QUANTITY
                        : id == R.id.finance_mode_void ? FinanceConst.MODE_VOID : FinanceConst.MODE_UNIT_PRICE);
            }
        });
        binding.financeBottomBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getOriginal().observe(this, this::renderOriginal);
        vm.changeMode.observe(this, m -> renderMode());
        vm.getPreview().observe(this, this::renderPreview);
        vm.getFieldErrors().observe(this, this::renderErrors);
        vm.getCanSubmit().observe(this, c -> binding.financeBottomBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getSubmitting().observe(this, s -> binding.financeBottomBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFinishWithResult().observe(this, text -> FinanceNav.finishWithMessage(this, text));
        vm.getFinishWithMessage().observe(this, text -> FinanceNav.finishWithMessage(this, text));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                finish();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.isDirty()) {
                    ConfirmDialogFragment.discardChanges(SaleChangeRequestActivity.this, REQ_DISCARD)
                            .showSafely(getSupportFragmentManager());
                } else {
                    finish();
                }
            }
        });
    }

    private void renderOriginal(@Nullable SaleOrderDetailVO d) {
        if (d == null) {
            return;
        }
        binding.financeOriginal.setTitle("出栏单 " + d.getBatchNo());
        binding.financeOriginal.setSubtitle(FinanceFormat.kg(d.getTotalWeight()) + " · 已收 "
                + FinanceFormat.yuan(d.getReceivedAmount()));
        StatusTagView tag = binding.financeOriginal.getStatusTagView();
        FinanceViews.bindPaymentTag(tag, d);
        tag.setVisibility(View.VISIBLE);
        boolean byHead = FinanceConst.BY_HEAD.equals(d.getPricingMethod());
        binding.financePriceLayout.setSuffixText(byHead ? "元/" + d.getCountUnitText() : "元/千克");
        binding.financePriceLayout.setHelperText((byHead ? "原单价每" + d.getCountUnitText() + " " : "原单价每千克 ")
                + FinanceFormat.price(d.getUnitPrice()) + " 元");
        binding.financeQuantityLayout.setSuffixText(d.getCountUnitText());
        binding.financeQuantityLayout.setHelperText("原数量 " + FinanceFormat.count(d.getQuantity(), d.getCountUnitText()));
        binding.financeWeightLayout.setHelperText("原总重 " + FinanceFormat.kg(d.getTotalWeight()));
        binding.financeWeightLayout.setVisibility(byHead ? View.GONE : View.VISIBLE);
        binding.financeVoidNotice.setText("审核通过后这张出栏单作废，" + d.getReceipts().size()
                + " 笔收款生成的收入同步作废，" + d.getBatchNo() + " 存栏恢复 "
                + FinanceFormat.count(d.getQuantity(), d.getCountUnitText()) + "并回到在养状态。");
        renderPreview(vm.getPreview().getValue());
    }

    private void renderMode() {
        String m = vm.changeMode.getValue();
        binding.financeModeGroup.check(FinanceConst.MODE_QUANTITY.equals(m) ? R.id.finance_mode_quantity
                : FinanceConst.MODE_VOID.equals(m) ? R.id.finance_mode_void : R.id.finance_mode_price);
        binding.financePriceLayout.setVisibility(FinanceConst.MODE_UNIT_PRICE.equals(m) ? View.VISIBLE : View.GONE);
        binding.financeQuantityBox.setVisibility(FinanceConst.MODE_QUANTITY.equals(m) ? View.VISIBLE : View.GONE);
        binding.financeVoidNotice.setVisibility(FinanceConst.MODE_VOID.equals(m) ? View.VISIBLE : View.GONE);
    }

    private void renderPreview(@Nullable SaleChangePreviewVO p) {
        SaleOrderDetailVO d = vm.getOriginal().getValue();
        BigDecimal saleBefore = p != null ? p.getSaleAmountBefore() : d == null ? null : d.getSaleAmount();
        BigDecimal unpaidBefore = p != null ? p.getUnpaidAmountBefore() : d == null ? null : d.getUnpaidAmount();
        BigDecimal profitBefore = p != null ? p.getProfitBefore() : d == null || d.getSettlementBrief() == null ? null
                : d.getSettlementBrief().getProfit();
        setRow(binding.financeRowAmount, FinanceFormat.num(saleBefore), p == null ? null : p.getSaleAmountAfter(),
                false, 0);
        setRow(binding.financeRowUnpaid, FinanceFormat.num(unpaidBefore), p == null ? null : p.getUnpaidAmountAfter(),
                false, 0);
        BigDecimal profitAfter = p == null ? null : p.getProfitAfter();
        setRow(binding.financeRowProfit, FinanceFormat.signed(profitBefore), profitAfter, true,
                profitAfter == null ? 0 : profitAfter.signum());
        StringBuilder note = new StringBuilder();
        BigDecimal received = p != null ? p.getReceivedAmount() : d == null ? null : d.getReceivedAmount();
        if (received != null) {
            note.append("已收 ").append(FinanceFormat.yuan(received)).append("不变");
        }
        if (p != null && Boolean.TRUE.equals(p.getTurnToLoss()) && profitAfter != null && d != null) {
            note.append("；批次 ").append(d.getBatchNo()).append(" 将由盈利转为亏损 ")
                    .append(FinanceFormat.yuan(profitAfter.abs()));
        }
        if (p != null && p.getVoidReceiptCount() != null && p.getVoidReceiptCount() > 0) {
            note.append("；").append(p.getVoidReceiptCount()).append(" 笔收款共 ")
                    .append(FinanceFormat.yuan(p.getVoidReceiptAmount())).append("同步作废");
        }
        binding.financePreviewNote.setText(note);
    }

    /** “188,160 → 182,280 元”；新值为空显示“—”；盈亏为负 error、为正 success */
    private void setRow(@NonNull KeyValueRowView row, @NonNull String before, @Nullable BigDecimal after,
                        boolean signed, int sign) {
        SpannableStringBuilder sb = new SpannableStringBuilder(before).append(" → ");
        int start = sb.length();
        sb.append(after == null ? FinanceFormat.DASH : (signed ? FinanceFormat.signed(after) : FinanceFormat.num(after))
                + " 元");
        if (signed && sign != 0) {
            int color = FinanceViews.color(row, sign < 0 ? androidx.appcompat.R.attr.colorError
                    : com.zhimu.slmp.base.R.attr.colorSuccess);
            sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        row.setValue(sb);
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        FinanceViews.setError(binding.financePriceLayout, errors, SaleChangeRequestViewModel.F_PRICE);
        FinanceViews.setError(binding.financeQuantityLayout, errors, SaleChangeRequestViewModel.F_QUANTITY);
        FinanceViews.setError(binding.financeWeightLayout, errors, SaleChangeRequestViewModel.F_WEIGHT);
        FinanceViews.setError(binding.financeReasonLayout, errors, SaleChangeRequestViewModel.F_REASON);
        if (errors == null || !errors.containsKey(SaleChangeRequestViewModel.F_REASON)) {
            binding.financeReasonLayout.setHelperText(getString(R.string.finance_e08_reason_helper));
        }
        renderOriginal(vm.getOriginal().getValue());
        if (errors != null && !errors.isEmpty()) {
            View first = errors.containsKey(SaleChangeRequestViewModel.F_REASON) && errors.size() == 1
                    ? binding.financeReasonLayout : binding.financeModeGroup;
            binding.financeScroll.smoothScrollTo(0, Math.max(0, first.getTop() - 48));
        }
    }
}
