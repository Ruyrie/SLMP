package com.zhimu.slmp.finance.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatCardView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.finance.databinding.FinanceFragmentHomeBinding;
import com.zhimu.slmp.finance.databinding.FinanceViewToolBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.BusinessOverviewVO;
import com.zhimu.slmp.finance.ui.common.FinanceNav;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.List;

/**
 * E01 经营页（底部导航“经营”，TabRegistry.BUSINESS）：顶部状态栏 insets 由 MainActivity 处理。
 */
public class FinanceHomeFragment extends BaseFragment {

    private FinanceFragmentHomeBinding binding;
    private FinanceHomeViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FinanceFragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = obtainViewModel(FinanceInjector.factory(), FinanceHomeViewModel.class);
        observeBase(vm);
        binding.financeTopBar.bind(requireActivity(), getViewLifecycleOwner());
        binding.financeTopBar.bindLiftOnScroll(binding.financeScroll);

        binding.financeState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(getViewLifecycleOwner(), binding.financeState::render);
        vm.getRefreshing().observe(getViewLifecycleOwner(),
                r -> binding.financeRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.financeRefresh.setOnRefreshListener(vm::refresh);
        vm.getCurrentFarm().observe(getViewLifecycleOwner(), vm::onFarmChanged);

        bindTool(binding.financeToolFinance, R.drawable.ic_payments,
                com.zhimu.slmp.finance.R.string.finance_e01_tool_finance, FinanceHomeViewModel.TOOL_FINANCE);
        bindTool(binding.financeToolSale, R.drawable.ic_local_shipping,
                com.zhimu.slmp.finance.R.string.finance_e01_tool_sale, FinanceHomeViewModel.TOOL_SALE);
        bindTool(binding.financeToolMarket, R.drawable.ic_trending_up,
                com.zhimu.slmp.finance.R.string.finance_e01_tool_market, FinanceHomeViewModel.TOOL_MARKET);
        bindTool(binding.financeToolReport, R.drawable.ic_bar_chart,
                com.zhimu.slmp.finance.R.string.finance_e01_tool_report, FinanceHomeViewModel.TOOL_REPORT);
        binding.financeToolMarket.financeToolSubtitle.setText(com.zhimu.slmp.finance.R.string.finance_e01_tool_market_sub);
        binding.financeToolReport.financeToolSubtitle.setText(com.zhimu.slmp.finance.R.string.finance_e01_tool_report_sub);
        ClickThrottle.setOnClickListener(binding.financeCardBalance,
                v -> vm.onCardClick(FinanceHomeViewModel.CARD_BALANCE));
        ClickThrottle.setOnClickListener(binding.financeCardReceivable,
                v -> vm.onCardClick(FinanceHomeViewModel.CARD_RECEIVABLE));
        ClickThrottle.setOnClickListener(binding.financeCardPrice,
                v -> vm.onCardClick(FinanceHomeViewModel.CARD_PRICE));
        binding.financeTodoHeader.setAction(getString(com.zhimu.slmp.finance.R.string.finance_e01_todos_all), 0,
                v -> vm.onCardClick(FinanceHomeViewModel.TODO_ALL));

        vm.getDateNote().observe(getViewLifecycleOwner(), binding.financeDateNote::setText);
        vm.getOverview().observe(getViewLifecycleOwner(), this::render);
        vm.getSalesSubtitle().observe(getViewLifecycleOwner(), binding.financeToolSale.financeToolSubtitle::setText);
        vm.getRecordSubtitle().observe(getViewLifecycleOwner(),
                binding.financeToolFinance.financeToolSubtitle::setText);
        vm.getTodoItems().observe(getViewLifecycleOwner(), this::renderTodos);
        vm.getShowAllTodos().observe(getViewLifecycleOwner(), show ->
                binding.financeTodoHeader.getActionButton().setVisibility(Boolean.TRUE.equals(show) ? View.VISIBLE
                        : View.GONE));
        vm.getOpenPage().observe(getViewLifecycleOwner(), t -> FinanceNav.open(requireContext(), t, null));
    }

    private void bindTool(@NonNull FinanceViewToolBinding tool, int icon, int title, int id) {
        tool.financeToolIcon.setImageResource(icon);
        tool.financeToolTitle.setText(title);
        ClickThrottle.setOnClickListener(tool.getRoot(), v -> vm.onCardClick(id));
    }

    private void render(@Nullable BusinessOverviewVO o) {
        if (o == null) {
            return;
        }
        BusinessOverviewVO.MonthBalance b = o.getMonthBalance();
        StatCardView balance = binding.financeCardBalance;
        balance.clearSubItems();
        if (b != null) {
            balance.setValue(FinanceFormat.num(b.getBalanceAmount()));
            int tone = FinanceFormat.tone(b.getBalanceMomRate(), b.isComparable(), true);
            balance.setTrend(vm.getBalanceMomText().getValue(), statTrend(tone),
                    statDirection(b.getBalanceMomRate(), b.isComparable()));
            balance.addSubItem(getString(com.zhimu.slmp.finance.R.string.finance_e02_income),
                    FinanceFormat.yuan(b.getIncome()));
            balance.addSubItem(getString(com.zhimu.slmp.finance.R.string.finance_e02_expense),
                    FinanceFormat.yuan(b.getExpenseAmount()));
        }
        binding.financeCardReceivable.setValue(o.getReceivable() == null ? "0"
                : FinanceFormat.num(o.getReceivable().getTotalUnpaidAmount()));
        binding.financeCardReceivable.setTrend(vm.getReceivableNote().getValue(),
                Boolean.TRUE.equals(vm.getReceivableOverdue().getValue()) ? StatCardView.TREND_BAD
                        : StatCardView.TREND_NEUTRAL, StatCardView.DIRECTION_NONE);
        BusinessOverviewVO.PriceCard p = o.getPriceCard();
        if (p == null) {
            binding.financeCardPrice.setValue(FinanceFormat.DASH, null);
            binding.financeCardPrice.setTrend(null, StatCardView.TREND_NONE, StatCardView.DIRECTION_NONE);
        } else {
            binding.financeCardPrice.setLabel(p.getCategoryName() + "出栏价");
            binding.financeCardPrice.setValue(FinanceFormat.price(p.getPrice()), p.getPriceUnitText());
            int tone = FinanceFormat.priceTone(p.getChangeRate(), p.getDirection(), true);
            binding.financeCardPrice.setTrend(FinanceFormat.priceChange(p.getChangeRate(), p.getDirection()) + " · "
                            + p.getProvinceName(), statTrend(tone),
                    FinanceConst.UP.equals(p.getDirection()) ? StatCardView.DIRECTION_UP
                            : FinanceConst.DOWN.equals(p.getDirection()) ? StatCardView.DIRECTION_DOWN
                            : StatCardView.DIRECTION_NONE);
        }
    }

    public static int statTrend(int tone) {
        switch (tone) {
            case FinanceFormat.TONE_GOOD:
                return StatCardView.TREND_GOOD;
            case FinanceFormat.TONE_BAD:
                return StatCardView.TREND_BAD;
            case FinanceFormat.TONE_NEUTRAL:
                return StatCardView.TREND_NEUTRAL;
            default:
                return StatCardView.TREND_NONE;
        }
    }

    public static int statDirection(@Nullable java.math.BigDecimal rate, boolean comparable) {
        if (!comparable || rate == null || rate.signum() == 0) {
            return StatCardView.DIRECTION_NONE;
        }
        return rate.signum() > 0 ? StatCardView.DIRECTION_UP : StatCardView.DIRECTION_DOWN;
    }

    private void renderTodos(@Nullable List<BusinessOverviewVO.TodoItem> items) {
        boolean empty = items == null || items.isEmpty();
        binding.financeTodoHeader.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.financeTodos.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.financeTodos.removeAllViews();
        if (empty) {
            return;
        }
        for (BusinessOverviewVO.TodoItem t : items) {
            ListRowView row = new ListRowView(requireContext());
            row.setLeadingIcon(R.drawable.ic_payments, t.isOverdue() ? ListRowView.TONE_ERROR : ListRowView.TONE_NEUTRAL);
            row.setTitle(t.getTitle());
            row.setSubtitle(t.getSubtitle());
            if (t.getTagText() != null) {
                row.setStatusTag(t.getTagText(), t.isOverdue() ? StatusTagView.ERROR : StatusTagView.NEUTRAL,
                        t.isOverdue() ? R.drawable.ic_schedule : 0);
            }
            ClickThrottle.setOnClickListener(row, v -> vm.onTodoClick(t));
            binding.financeTodos.addView(row);
        }
        binding.financeTodos.updatePositions();
    }
}
