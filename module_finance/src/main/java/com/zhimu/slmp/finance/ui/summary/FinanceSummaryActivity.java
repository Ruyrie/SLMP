package com.zhimu.slmp.finance.ui.summary;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatCardView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivitySummaryBinding;
import com.zhimu.slmp.finance.databinding.FinanceItemFarmFinanceBinding;
import com.zhimu.slmp.finance.databinding.FinanceItemShareLegendBinding;
import com.zhimu.slmp.finance.databinding.FinanceViewColumnBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.CategoryShareVO;
import com.zhimu.slmp.finance.model.vo.FarmFinanceVO;
import com.zhimu.slmp.finance.model.vo.FinanceSummaryVO;
import com.zhimu.slmp.finance.ui.common.FinanceNav;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.ui.home.FinanceHomeFragment;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.widget.CategoryDonutView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * E02 收支记账（图 10-2、10-3）。
 */
public class FinanceSummaryActivity extends FinancePageActivity {

    static final String REQ_MONTH = "finance_e02_month";
    static final int MONTHS = 24;

    private FinanceActivitySummaryBinding binding;
    private FinanceSummaryViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivitySummaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), FinanceSummaryViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e02_title), binding.financeScroll);
        TopAppBarHelper.inflateMenu(binding.financeTopBar.baseToolbar, R.menu.finance_menu_summary);
        binding.financeTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.finance_menu_records) {
                vm.openRecords();
            } else if (item.getItemId() == R.id.finance_menu_categories) {
                vm.openCategories();
            }
            return true;
        });
        bindState(binding.financeState, vm);
        observeNav(vm.getOpenPage());
        vm.getRefreshing().observe(this, r -> binding.financeRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.financeRefresh.setOnRefreshListener(vm::load);

        binding.financeScopeFarm.setText(farmName());
        binding.financeScopeGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) {
                vm.switchScope(id == R.id.finance_scope_all);
            }
        });
        binding.financeMonthPrev.setOnClickListener(v -> vm.prevMonth());
        binding.financeMonthNext.setOnClickListener(v -> vm.nextMonth());
        binding.financeMonth.setOnClickListener(v -> showMonthPicker());
        getSupportFragmentManager().setFragmentResultListener(REQ_MONTH, this,
                (k, r) -> vm.pickMonth(OptionSheetDialogFragment.getValue(r)));
        ClickThrottle.setOnClickListener(binding.financeRecordsEntry, v -> vm.openRecords());
        ClickThrottle.setOnClickListener(binding.financeFab, v -> vm.openRecordEdit());
        binding.financeScroll.setOnScrollChangeListener((View.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            if (y > oy + 4) {
                binding.financeFab.shrink();
            } else if (y < oy - 4) {
                binding.financeFab.extend();
            }
        });

        Chip expense = FinanceViews.filterChip(this, binding.financeShareDirection,
                getString(R.string.finance_e02_expense), FinanceConst.EXPENSE);
        Chip income = FinanceViews.filterChip(this, binding.financeShareDirection,
                getString(R.string.finance_e02_income), FinanceConst.INCOME);
        expense.setChecked(true);
        expense.setOnCheckedChangeListener((b, c) -> {
            if (c) {
                vm.switchShareDirection(FinanceConst.EXPENSE);
            }
        });
        income.setOnCheckedChangeListener((b, c) -> {
            if (c) {
                vm.switchShareDirection(FinanceConst.INCOME);
            }
        });
        binding.financeDonut.setOnSliceClickListener(vm::selectShare);

        vm.getMonthLabel().observe(this, binding.financeMonth::setText);
        vm.getPeriodNote().observe(this, binding.financePeriodNote::setText);
        vm.getCanNextMonth().observe(this, can -> {
            binding.financeMonthNext.setEnabled(Boolean.TRUE.equals(can));
            binding.financeMonthNext.setAlpha(Boolean.TRUE.equals(can) ? 1f : .38f);
        });
        vm.getShowScopeSwitch().observe(this, s -> binding.financeScopeGroup.setVisibility(Boolean.TRUE.equals(s)
                ? View.VISIBLE : View.GONE));
        vm.getScopeAll().observe(this, this::renderScope);
        vm.getSummary().observe(this, this::render);
        vm.getShares().observe(this, this::renderShares);
        vm.getSelectedShareIndex().observe(this, this::renderSelected);
        vm.getRecordsSubtitle().observe(this, binding.financeRecordsEntry::setSubtitle);
        vm.getAllFarmsNotice().observe(this, binding.financeAllNotice::setText);
    }

    @NonNull
    private String farmName() {
        com.zhimu.slmp.common.model.vo.FarmContextVO c = com.zhimu.slmp.common.di.CoreInjector.get(this)
                .farmContext().current();
        return c == null || c.getName() == null ? "当前养殖场" : c.getName();
    }

    @Override
    protected void onChildResult(@NonNull ActivityResult result) {
        vm.onChildResult(FinanceNav.messageOf(result));
    }

    private void showMonthPicker() {
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        String m = FinanceFormat.currentMonth();
        for (int i = 0; i < MONTHS; i++) {
            String ym = FinanceFormat.shiftMonth(m, -i);
            String label = ym.substring(0, 4) + "年" + Integer.parseInt(ym.substring(5)) + "月";
            options.add(new OptionSheetDialogFragment.Option(ym, label));
        }
        OptionSheetDialogFragment.builder(REQ_MONTH).title(getString(R.string.finance_month_title)).options(options)
                .selected(vm.getMonth().getValue()).build().showSafely(getSupportFragmentManager());
    }

    private void renderScope(@Nullable Boolean all) {
        boolean a = Boolean.TRUE.equals(all);
        binding.financeScopeGroup.check(a ? R.id.finance_scope_all : R.id.finance_scope_farm);
        binding.financeAllNotice.setVisibility(a ? View.VISIBLE : View.GONE);
        binding.financeShareCard.setVisibility(a ? View.GONE : View.VISIBLE);
        binding.financeFarmsHeader.setVisibility(a ? View.VISIBLE : View.GONE);
        binding.financeFarms.setVisibility(a ? View.VISIBLE : View.GONE);
        binding.financeCardBalance.setLabel(getString(a ? R.string.finance_e02_balance_all : R.string.finance_e02_balance));
        binding.financeCardIncome.setLabel(getString(a ? R.string.finance_e02_income_all : R.string.finance_e02_income));
        binding.financeCardExpense.setLabel(getString(a ? R.string.finance_e02_expense_all
                : R.string.finance_e02_expense));
        if (a) {
            binding.financeFab.hide();
        } else {
            binding.financeFab.show();
        }
    }

    private void render(@Nullable FinanceSummaryVO s) {
        if (s == null) {
            return;
        }
        binding.financeCardBalance.setValue(FinanceFormat.num(s.getBalanceAmount()));
        binding.financeCardBalance.setTrend(vm.getBalanceNote().getValue(),
                FinanceHomeFragment.statTrend(FinanceFormat.tone(s.getBalanceMomRate(), s.isBalanceComparable(), true)),
                FinanceHomeFragment.statDirection(s.getBalanceMomRate(), s.isBalanceComparable()));
        bindStat(binding.financeCardIncome, s.getIncome(), s.getIncomeMomRate(), s.isIncomeComparable(), true);
        bindStat(binding.financeCardExpense, s.getExpenseAmount(), s.getExpenseMomRate(), s.isExpenseComparable(),
                false);
        renderFarms(s.getFarms());
        boolean empty = s.getRecordCount() == 0;
        binding.financeShareContent.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.financeShareEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.financeShareEmpty.setText(getString(R.string.finance_e02_share_empty,
                FinanceFormat.monthLabel(vm.getMonth().getValue())));
    }

    private static void bindStat(@NonNull StatCardView card, @Nullable BigDecimal value, @Nullable BigDecimal rate,
                                 boolean comparable, boolean upIsGood) {
        card.setValue(FinanceFormat.num(value));
        card.setTrend(FinanceFormat.mom("环比", rate, comparable),
                FinanceHomeFragment.statTrend(FinanceFormat.tone(rate, comparable, upIsGood)),
                FinanceHomeFragment.statDirection(rate, comparable));
    }

    private void renderShares(@Nullable List<CategoryShareVO> list) {
        binding.financeLegend.removeAllViews();
        if (list == null) {
            return;
        }
        FinanceSummaryVO s = vm.getSummary().getValue();
        boolean income = FinanceConst.INCOME.equals(vm.getShareDirection().getValue());
        List<Float> amounts = new ArrayList<>();
        int primary = FinanceViews.color(binding.financeDonut, androidx.appcompat.R.attr.colorPrimary);
        for (int i = 0; i < list.size(); i++) {
            CategoryShareVO c = list.get(i);
            amounts.add(c.getAmount() == null ? 0f : c.getAmount().floatValue());
            FinanceItemShareLegendBinding row = FinanceItemShareLegendBinding.inflate(LayoutInflater.from(this),
                    binding.financeLegend, false);
            row.financeLegendDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    CategoryDonutView.colorFor(primary, i)));
            row.financeLegendName.setText(c.getName());
            row.financeLegendShare.setText(FinanceFormat.percent(c.getShareRate()));
            row.financeLegendAmount.setText(FinanceFormat.num(c.getAmount()));
            FinanceViews.setTrend(row.financeLegendMom, FinanceFormat.mom("环比", c.getMomRate(), c.isComparable()),
                    FinanceFormat.tone(c.getMomRate(), c.isComparable(), income),
                    FinanceFormat.direction(c.getMomRate()));
            final int index = i;
            ClickThrottle.setOnClickListener(row.getRoot(), v -> vm.openShare(index));
            binding.financeLegend.addView(row.getRoot());
        }
        String total = s == null ? "" : FinanceFormat.num(income ? s.getIncome() : s.getExpenseAmount());
        binding.financeDonut.setData(amounts, total, income ? "本月收入" : "本月支出");
    }

    private void renderSelected(@Nullable Integer index) {
        int sel = index == null ? -1 : index;
        binding.financeDonut.setSelected(sel);
        int low = FinanceViews.color(binding.financeLegend, com.google.android.material.R.attr.colorSurfaceContainerLow);
        for (int i = 0; i < binding.financeLegend.getChildCount(); i++) {
            View row = binding.financeLegend.getChildAt(i);
            if (i == sel) {
                row.setBackgroundColor(low);
            } else {
                row.setBackground(null);
                row.setBackgroundResource(android.R.color.transparent);
            }
        }
    }

    private void renderFarms(@NonNull List<FarmFinanceVO> farms) {
        binding.financeFarms.removeAllViews();
        binding.financeFarmsTitle.setText(getString(R.string.finance_e02_by_farm, farms.size()));
        for (FarmFinanceVO f : farms) {
            FinanceItemFarmFinanceBinding card = FinanceItemFarmFinanceBinding.inflate(LayoutInflater.from(this),
                    binding.financeFarms, false);
            card.financeFarmTag.setStatus(f.getFarmName(), com.zhimu.slmp.base.widget.StatusTagView.FARM);
            card.financeFarmRole.setText((f.getRoleText() == null ? "" : f.getRoleText())
                    + (f.getMainCategoryName() == null ? "" : " · " + f.getMainCategoryName()));
            card.financeFarmShare.setText(f.getIncomeShareRate() == null ? ""
                    : "收入占 " + FinanceFormat.percent(f.getIncomeShareRate()));
            addColumn(card, "收入", f.getIncome(), f.getIncomeMomRate(), f.isIncomeComparable(), true);
            addColumn(card, "支出", f.getExpenseAmount(), f.getExpenseMomRate(), f.isExpenseComparable(), false);
            addColumn(card, "结余", f.getBalanceAmount(), f.getBalanceMomRate(), f.isBalanceComparable(), true);
            ClickThrottle.setOnClickListener(card.getRoot(), v -> vm.switchToFarm(f));
            binding.financeFarms.addView(card.getRoot());
        }
        binding.financeFarms.updatePositions();
    }

    private void addColumn(@NonNull FinanceItemFarmFinanceBinding card, @NonNull String label,
                           @Nullable BigDecimal value, @Nullable BigDecimal rate, boolean comparable,
                           boolean upIsGood) {
        FinanceViewColumnBinding col = FinanceViewColumnBinding.inflate(LayoutInflater.from(this),
                card.financeFarmColumns, false);
        col.financeColLabel.setText(label);
        col.financeColValue.setText(FinanceViews.amountWithUnit(col.financeColValue, FinanceFormat.num(value), "元",
                .67f));
        col.financeColNote.setVisibility(View.VISIBLE);
        String mom = comparable && rate != null ? FinanceFormat.signedPercent(rate) : FinanceFormat.NOT_COMPARABLE;
        FinanceViews.setTrend(col.financeColNote, mom, FinanceFormat.tone(rate, comparable, upIsGood),
                FinanceFormat.direction(rate));
        card.financeFarmColumns.addView(col.getRoot());
    }

    static int position(int i, int size) {
        return GroupPosition.of(i, size);
    }
}
