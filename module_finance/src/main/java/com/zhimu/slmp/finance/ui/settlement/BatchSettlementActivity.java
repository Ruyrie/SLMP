package com.zhimu.slmp.finance.ui.settlement;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.TipPopup;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivitySettlementBinding;
import com.zhimu.slmp.finance.databinding.FinanceItemCostLegendBinding;
import com.zhimu.slmp.finance.databinding.FinanceViewColumnBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.BatchSettlementVO;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.widget.CostStackBarView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * E10 批次核算（图 10-14）。
 */
public class BatchSettlementActivity extends FinancePageActivity {

    static final String REQ_CONFIRM = "finance_e10_confirm";

    private FinanceActivitySettlementBinding binding;
    private BatchSettlementViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivitySettlementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), BatchSettlementViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e10_title), binding.financeScroll);
        TopAppBarHelper.inflateMenu(binding.financeTopBar.baseToolbar, R.menu.finance_menu_settlement);
        binding.financeTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            vm.openLinkedRecords();
            return true;
        });
        bindState(binding.financeState, vm);
        observeNav(vm.getOpenPage());
        vm.getRefreshing().observe(this, r -> binding.financeRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.financeRefresh.setOnRefreshListener(vm::refresh);
        binding.financeSupplementNotice.setAction(getString(R.string.finance_e10_confirm), v -> vm.requestConfirm());

        binding.financeColIncome.financeColLabel.setText(R.string.finance_e10_income);
        binding.financeColCost.financeColLabel.setText(R.string.finance_e10_cost_total);
        binding.financeColCostHead.financeColLabel.setText(R.string.finance_e10_cost_per_head);
        binding.financeColProfitHead.financeColLabel.setText(R.string.finance_e10_profit_per_head);
        binding.financeColFcr.financeColLabel.setText(R.string.finance_e10_fcr);
        android.graphics.drawable.Drawable info = AppCompatResources.getDrawable(this,
                com.zhimu.slmp.base.R.drawable.ic_info);
        if (info != null) {
            int s = (int) (16 * getResources().getDisplayMetrics().density);
            info = info.mutate();
            info.setBounds(0, 0, s, s);
            info.setTint(FinanceViews.color(binding.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant));
            binding.financeColFcr.financeColLabel.setCompoundDrawablesRelative(null, null, info, null);
        }
        binding.financeColFcr.financeColLabel.setMinHeight((int) (32 * getResources().getDisplayMetrics().density));
        binding.financeColFcr.financeColLabel.setOnClickListener(v ->
                TipPopup.show(v, R.string.finance_e10_fcr_tip));

        vm.getSettlement().observe(this, this::render);
        vm.getSupplements().observe(this, l -> {
            boolean show = l != null && !l.isEmpty();
            binding.financeSupplementNotice.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                binding.financeSupplementNotice.setNotice(BatchSettlementViewModel.supplementTitle(l),
                        BatchSettlementViewModel.supplementText(l));
            }
        });
        vm.getShowConfirmDialog().observe(this, arr -> ConfirmDialogFragment.builder(REQ_CONFIRM).title(arr[0])
                .message(arr[1]).positive(getString(R.string.finance_e10_confirm_positive))
                .negative(getString(R.string.finance_cancel)).keepOpenOnConfirm().build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_CONFIRM, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.confirmSupplements();
            }
        });
        vm.getConfirming().observe(this, c -> ConfirmDialogFragment.setLoading(getSupportFragmentManager(),
                REQ_CONFIRM, Boolean.TRUE.equals(c)));
        vm.getDismissConfirmDialog().observe(this, b -> ConfirmDialogFragment.dismiss(getSupportFragmentManager(),
                REQ_CONFIRM));
    }

    /** 关联收支（E04）、出栏单（E07）返回：重新取核算 */
    @Override
    protected void onChildResult(@NonNull ActivityResult result) {
        vm.onChildResult();
    }

    private void render(@Nullable BatchSettlementVO s) {
        if (s == null) {
            return;
        }
        boolean locked = FinanceConst.SETTLEMENT_LOCKED.equals(s.getStatus());
        binding.financeBatchNo.setText(s.getBatch().getBatchNo());
        binding.financeLockTag.setStatus(getString(locked ? R.string.finance_e10_locked : R.string.finance_e10_to_date),
                locked ? StatusTagView.NEUTRAL : StatusTagView.PRIMARY);
        binding.financeLockTag.setTagIcon(locked ? com.zhimu.slmp.base.R.drawable.ic_lock : 0);
        binding.financeBatchLine.setText(BatchSettlementViewModel.batchLine(s));
        binding.financeCountLine.setText(BatchSettlementViewModel.countLine(s.getBatch()));
        binding.financeProfitLabel.setText(locked ? R.string.finance_e10_profit : R.string.finance_e10_profit_to_date);
        BigDecimal profit = s.getProfit();
        binding.financeProfit.setText(FinanceViews.amountWithUnit(binding.financeProfit, FinanceFormat.signed(profit),
                "元", .45f));
        binding.financeProfit.setTextColor(FinanceViews.color(binding.financeProfit, profit == null || profit.signum() == 0
                ? com.google.android.material.R.attr.colorOnSurface : profit.signum() > 0
                ? com.zhimu.slmp.base.R.attr.colorSuccess : androidx.appcompat.R.attr.colorError));
        binding.financeColIncome.financeColValue.setText(FinanceFormat.yuan(s.getSaleIncome()));
        binding.financeColCost.financeColValue.setText(FinanceFormat.yuan(s.getCostTotal()));
        binding.financeIncomeNote.setText(getString(R.string.finance_e10_income_note,
                FinanceFormat.yuan(s.getReceivedAmount())));
        setPerHead(binding.financeColCostHead, s.getCostPerHead(), false);
        setPerHead(binding.financeColProfitHead, s.getProfitPerHead(), true);
        binding.financeColFcr.financeColValue.setText(s.getFcr() == null ? FinanceFormat.NO_DATA
                : FinanceFormat.price(s.getFcr()));
        binding.financePerHeadNote.setText(BatchSettlementViewModel.perHeadNote(s));
        binding.financeCostTotal.setText(getString(R.string.finance_e10_cost_total_value,
                FinanceFormat.yuan(s.getCostTotal())));
        renderCosts(s.getCosts());
    }

    private void setPerHead(FinanceViewColumnBinding col, @Nullable BigDecimal v, boolean signed) {
        if (v == null) {
            col.financeColValue.setText(FinanceFormat.NO_DATA);
            return;
        }
        String text = signed && v.signum() > 0 ? "+" + FinanceFormat.price(v) : FinanceFormat.price(v);
        col.financeColValue.setText(FinanceViews.amountWithUnit(col.financeColValue, text, "元", .67f));
        if (signed) {
            col.financeColValue.setTextColor(FinanceViews.color(col.financeColValue, v.signum() >= 0
                    ? com.zhimu.slmp.base.R.attr.colorSuccess : androidx.appcompat.R.attr.colorError));
        }
    }

    /** 顺序固定：饲料 primary、引种 inverse-primary、兽药疫苗 secondary、其他 outline */
    private int colorOf(String group) {
        View v = binding.getRoot();
        switch (group == null ? "" : group) {
            case FinanceConst.COST_FEED:
                return FinanceViews.color(v, androidx.appcompat.R.attr.colorPrimary);
            case FinanceConst.COST_SEEDLING:
                return FinanceViews.color(v, com.google.android.material.R.attr.colorPrimaryInverse);
            case FinanceConst.COST_DRUG:
                return FinanceViews.color(v, com.google.android.material.R.attr.colorSecondary);
            default:
                return FinanceViews.color(v, com.google.android.material.R.attr.colorOutline);
        }
    }

    private void renderCosts(@Nullable List<BatchSettlementVO.CostItem> costs) {
        binding.financeCostLegend.removeAllViews();
        List<CostStackBarView.Segment> segments = new ArrayList<>();
        if (costs == null) {
            binding.financeCostBar.setSegments(segments);
            return;
        }
        for (BatchSettlementVO.CostItem c : costs) {
            int color = colorOf(c.getCostGroup());
            segments.add(new CostStackBarView.Segment(c.getAmount() == null ? 0 : c.getAmount().floatValue(), color));
            FinanceItemCostLegendBinding row = FinanceItemCostLegendBinding.inflate(LayoutInflater.from(this),
                    binding.financeCostLegend, false);
            row.financeCostDot.setBackgroundTintList(ColorStateList.valueOf(color));
            row.financeCostName.setText(c.getCostGroupText());
            row.financeCostNote.setText(c.getNote());
            row.financeCostAmount.setText(FinanceFormat.yuan(c.getAmount()));
            row.financeCostShare.setText(FinanceFormat.percent(c.getShareRate()));
            if (FinanceConst.COST_OTHER.equals(c.getCostGroup())) {
                ClickThrottle.setOnClickListener(row.getRoot(), v -> vm.openOtherCosts());
            }
            binding.financeCostLegend.addView(row.getRoot());
        }
        binding.financeCostBar.setSegments(segments);
    }
}
