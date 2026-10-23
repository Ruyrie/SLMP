package com.zhimu.slmp.finance.ui.saledetail;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.dialog.ReasonDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.KeyValueRowView;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivitySaleDetailBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.ui.common.FinanceNav;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.ui.common.ReceiptBottomSheet;
import com.zhimu.slmp.finance.ui.common.ReceiptHost;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * E07 出栏单详情（图 10-10、10-11）。
 */
public class SaleOrderDetailActivity extends FinancePageActivity implements ReceiptBottomSheet.Provider {

    static final String REQ_VOID = "finance_e07_void";

    private FinanceActivitySaleDetailBinding binding;
    private SaleOrderDetailViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivitySaleDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), SaleOrderDetailViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e07_title), binding.financeScroll);
        TopAppBarHelper.inflateMenu(binding.financeTopBar.baseToolbar, R.menu.finance_menu_more);
        binding.financeTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            showMenu();
            return true;
        });
        bindState(binding.financeState, vm);
        observeNav(vm.getOpenPage());
        vm.getRefreshing().observe(this, r -> binding.financeRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.financeRefresh.setOnRefreshListener(vm::refresh);
        binding.financeBottomBar.setOnSecondaryClickListener(v -> vm.openSettlement());
        binding.financeBottomBar.setOnPrimaryClickListener(v -> vm.openReceipt());
        vm.getReceipt().getShowSheet().observe(this, show -> {
            if (Boolean.TRUE.equals(show)) {
                ReceiptBottomSheet.show(getSupportFragmentManager());
            }
        });
        vm.getDetail().observe(this, this::render);
        vm.getBannerMode().observe(this, m -> renderBanner());
        vm.getShowLogSheet().observe(this, id -> RecordLogSheet.show(getSupportFragmentManager(),
                FinanceConst.LOG_SALE_ORDER, id, null, getString(R.string.finance_log_empty)));
        vm.getShowReasonDialog().observe(this, msg -> ReasonDialogFragment.builder(REQ_VOID)
                .title(getString(R.string.finance_e07_void_title)).message(msg)
                .label(getString(R.string.finance_e07_void_label)).maxLength(200)
                .positive(getString(R.string.finance_e07_void_positive)).negative(getString(R.string.finance_cancel))
                .danger(true).build().showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_VOID, this, (k, r) -> {
            if (ReasonDialogFragment.isConfirmed(r)) {
                vm.voidOrder(ReasonDialogFragment.getReason(r));
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.isChanged()) {
                    setResult(RESULT_OK);
                }
                finish();
            }
        });
    }

    @NonNull
    @Override
    public ReceiptHost receiptHost() {
        return vm;
    }

    @Override
    protected void onChildResult(@NonNull ActivityResult result) {
        vm.onChildResult(FinanceNav.messageOf(result));
    }

    private void showMenu() {
        View anchor = binding.financeTopBar.baseToolbar.findViewById(R.id.finance_menu_more);
        List<SaleOrderDetailViewModel.MenuSpec> items = vm.getMenuItems().getValue();
        if (anchor == null || items == null || items.isEmpty()) {
            return;
        }
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor);
        for (SaleOrderDetailViewModel.MenuSpec m : items) {
            switch (m.id) {
                case SaleOrderDetailViewModel.MENU_APPLY_CHANGE:
                    b.add(new MoreMenuHelper.Item(m.id, getString(R.string.finance_e07_menu_apply),
                            com.zhimu.slmp.base.R.drawable.ic_edit_note, null, m.note, false, m.enabled));
                    break;
                case SaleOrderDetailViewModel.MENU_EDIT:
                    b.add(m.id, getString(R.string.finance_e07_menu_edit), com.zhimu.slmp.base.R.drawable.ic_edit);
                    break;
                case SaleOrderDetailViewModel.MENU_VOID:
                    b.addDanger(m.id, getString(R.string.finance_e07_menu_void), com.zhimu.slmp.base.R.drawable.ic_block);
                    break;
                default:
                    b.add(m.id, getString(R.string.finance_e07_menu_log), com.zhimu.slmp.base.R.drawable.ic_history);
                    break;
            }
        }
        b.setOnItemClickListener(vm::onMenuSelected).show();
    }

    private void render(@Nullable SaleOrderDetailVO d) {
        if (d == null) {
            return;
        }
        boolean voided = FinanceFormat.isVoided(d);
        binding.financeBatchNo.setText(d.getBatchNo());
        FinanceViews.bindPaymentTag(binding.financePayTag, d);
        binding.financePenDate.setText(d.getPenName() + " · " + FinanceFormat.monthDay(d.getSaleDate()) + "出栏");
        binding.financeSaleAmount.setText(FinanceViews.amountWithUnit(binding.financeSaleAmount,
                FinanceFormat.num(d.getSaleAmount()), "元", .45f));
        FinanceViews.strike(binding.financeSaleAmount, voided);
        binding.financeAmountNote.setText(SaleOrderDetailViewModel.amountNote(d));
        binding.financeProgress.setVisibility(voided ? View.GONE : View.VISIBLE);
        binding.financePaidRow.setVisibility(voided ? View.GONE : View.VISIBLE);
        binding.financeProgress.setProgressCompat(FinanceFormat.progress(d.getReceivedAmount(), d.getSaleAmount()), true);
        binding.financeReceived.setText(getString(R.string.finance_e07_received, FinanceFormat.yuan(d.getReceivedAmount())));
        binding.financeUnpaid.setText(getString(R.string.finance_e07_unpaid, FinanceFormat.yuan(d.getUnpaidAmount())));
        binding.financeDueDivider.setVisibility(voided ? View.GONE : View.VISIBLE);
        binding.financeDueRow.setVisibility(voided ? View.GONE : View.VISIBLE);
        binding.financeDue.setText(SaleOrderDetailViewModel.dueText(d));
        FinanceViews.bindDueTag(binding.financeDueTag, d);

        binding.financeReceiptsTitle.setTitle(getString(R.string.finance_e07_receipts, d.getReceipts().size()));
        binding.financeReceipts.removeAllViews();
        for (SaleOrderDetailVO.Receipt r : d.getReceipts()) {
            ListRowView row = new ListRowView(this);
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_payments, ListRowView.TONE_SECONDARY);
            row.setTitle(FinanceFormat.yuan(r.getAmount()));
            row.setSubtitle(FinanceFormat.monthDay(r.getReceiptDate()) + " · " + r.getPaymentMethodText() + " · "
                    + r.getOperatorName());
            if (r.getFinanceRecordId() != null) {
                row.setStatusTag(getString(R.string.finance_e07_receipt_income), StatusTagView.NEUTRAL,
                        com.zhimu.slmp.base.R.drawable.ic_receipt_long);
                ClickThrottle.setOnClickListener(row, v -> vm.openIncome(r));
            }
            binding.financeReceipts.addView(row);
        }
        binding.financeReceipts.updatePositions();
        binding.financeReceiptsEmpty.setVisibility(d.getReceipts().isEmpty() ? View.VISIBLE : View.GONE);

        binding.financeInfo.removeAllViews();
        addInfo(R.string.finance_e07_pricing, d.getPricingMethodText());
        if (d.getAvgWeight() != null) {
            addInfo(R.string.finance_e07_avg, d.getAvgWeight().setScale(1, java.math.RoundingMode.HALF_UP).toPlainString()
                    + FinanceFormat.NBSP + "千克");
        }
        addInfo(R.string.finance_e07_buyer, d.getBuyerName());
        if (d.getPenAllocations().size() > 1) {
            List<String> pens = new ArrayList<>();
            for (SaleOrderDetailVO.PenAllocation a : d.getPenAllocations()) {
                pens.add(a.getPenName() + " " + FinanceFormat.count(a.getQuantity(), d.getCountUnitText()));
            }
            addInfo(R.string.finance_e07_pens, String.join("、", pens));
        }
        addInfo(R.string.finance_e07_registrant, (d.getRegistrant() == null ? "" : d.getRegistrant().getName() + " · ")
                + DateFormatter.formatDateTime(d.getRegisteredAt()));
        if (voided && d.getVoidReason() != null) {
            addInfo(R.string.finance_e07_void_reason, d.getVoidReason());
        }
        binding.financeInfo.updatePositions();

        boolean canReceive = d.getActions() != null && d.getActions().isCanReceive() && !voided;
        binding.financeBottomBar.getPrimaryButton().setVisibility(canReceive ? View.VISIBLE : View.GONE);
        renderBanner();
    }

    private void addInfo(int key, @Nullable String value) {
        KeyValueRowView row = new KeyValueRowView(this);
        row.setKey(getString(key));
        row.setValue(value);
        row.setValueAlign(KeyValueRowView.ALIGN_END);
        binding.financeInfo.addView(row);
    }

    private void renderBanner() {
        Integer mode = vm.getBannerMode().getValue();
        SaleOrderDetailVO d = vm.getDetail().getValue();
        if (mode == null || mode == SaleOrderDetailViewModel.BANNER_NONE || d == null) {
            binding.financeChangeNotice.setVisibility(View.GONE);
            return;
        }
        binding.financeChangeNotice.setVisibility(View.VISIBLE);
        if (mode == SaleOrderDetailViewModel.BANNER_PENDING) {
            binding.financeChangeNotice.setText(SaleOrderDetailViewModel.pendingText(d));
            binding.financeChangeNotice.setAction(getString(R.string.finance_view), v -> vm.openPendingRequest());
        } else {
            binding.financeChangeNotice.setText(getString(R.string.finance_e07_notice_receipt));
            binding.financeChangeNotice.setAction(null, null);
        }
    }
}
