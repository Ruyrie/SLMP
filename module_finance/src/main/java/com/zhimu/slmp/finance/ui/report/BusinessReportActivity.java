package com.zhimu.slmp.finance.ui.report;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivityReportBinding;
import com.zhimu.slmp.finance.databinding.FinanceItemKeyValueBinding;
import com.zhimu.slmp.finance.databinding.FinanceViewMetricBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.ReportQuery;
import com.zhimu.slmp.finance.model.vo.BusinessReportVO;
import com.zhimu.slmp.finance.model.vo.ReportSummaryVO;
import com.zhimu.slmp.finance.model.vo.ReportTrendVO;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.util.DownloadCompleteReceiver;
import com.zhimu.slmp.finance.util.ExportedFile;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.ReportFileHelper;
import com.zhimu.slmp.finance.widget.ProfitBarsView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * E13 经营报表（图 10-17、10-18）。导出：API-E42 → 系统下载 → CreateDocument 另存、Snackbar“打开”、更多菜单“分享报表”。
 */
public class BusinessReportActivity extends FinancePageActivity {

    static final String REQ_PICK = "finance_e13_pick";
    static final String EXTRA_WHICH = "which";

    private FinanceActivityReportBinding binding;
    private BusinessReportViewModel vm;
    private DownloadCompleteReceiver receiver;
    private ActivityResultLauncher<String> saveXlsx;
    private boolean renderingType;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        saveXlsx = registerForActivityResult(new ActivityResultContracts.CreateDocument(ReportFileHelper.MIME_XLSX),
                uri -> vm.saveAs(uri));
        super.onCreate(savedInstanceState);
        binding = FinanceActivityReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), BusinessReportViewModel.class);
        observeBase(vm);
        receiver = new DownloadCompleteReceiver(vm::onDownloadComplete);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e13_title), binding.financeScroll);
        TopAppBarHelper.inflateMenu(binding.financeTopBar.baseToolbar, R.menu.finance_menu_report);
        binding.financeTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.finance_menu_export_xlsx) {
                vm.export(FinanceConst.FORMAT_XLSX);
            } else if (item.getItemId() == R.id.finance_menu_share) {
                vm.shareExported();
            }
            return true;
        });
        bindState(binding.financeState, vm);
        observeNav(vm.getOpenPage());
        vm.getRefreshing().observe(this, r -> binding.financeRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.financeRefresh.setOnRefreshListener(vm::refresh);

        binding.financeTypeGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked && !renderingType) {
                vm.setReportType(id == R.id.finance_type_weekly ? FinanceConst.REPORT_WEEKLY
                        : id == R.id.finance_type_monthly ? FinanceConst.REPORT_MONTHLY : FinanceConst.REPORT_TO_DATE);
            }
        });
        binding.financeChipFarm.setOnClickListener(v -> vm.openPicker(BusinessReportViewModel.PICK_FARM));
        binding.financeChipRange.setOnClickListener(v -> vm.openPicker(BusinessReportViewModel.PICK_RANGE));
        binding.financeChipPeriod.setOnClickListener(v -> vm.openPicker(BusinessReportViewModel.PICK_PERIOD));
        ClickThrottle.setOnClickListener(binding.financeAiRegenerate, v -> vm.regenerateSummary());
        ClickThrottle.setOnClickListener(binding.financeCheckAction, v -> vm.openCheckRecords());
        bindMetric(binding.financeMetricStock, R.string.finance_e13_metric_stock, FinanceConst.METRIC_STOCK);
        bindMetric(binding.financeMetricMortality, R.string.finance_e13_metric_mortality, FinanceConst.METRIC_MORTALITY);
        bindMetric(binding.financeMetricFeed, R.string.finance_e13_metric_feed, FinanceConst.METRIC_FEED);
        bindMetric(binding.financeMetricBalance, R.string.finance_e13_metric_balance, FinanceConst.METRIC_BALANCE);

        vm.getShowPicker().observe(this, this::showPicker);
        getSupportFragmentManager().setFragmentResultListener(REQ_PICK, this, (k, r) -> onPicked(r));
        vm.getReportType().observe(this, t -> {
            renderingType = true;
            binding.financeTypeGroup.check(FinanceConst.REPORT_WEEKLY.equals(t) ? R.id.finance_type_weekly
                    : FinanceConst.REPORT_MONTHLY.equals(t) ? R.id.finance_type_monthly : R.id.finance_type_to_date);
            renderingType = false;
        });
        vm.getQuery().observe(this, q -> renderScope());
        vm.getFarmLabel().observe(this, binding.financeChipFarm::setText);
        vm.getRangeLabel().observe(this, binding.financeChipRange::setText);
        vm.getPeriods().observe(this, p -> renderScope());
        vm.getNotGeneratedTip().observe(this, tip -> {
            binding.financeTipNotice.setVisibility(tip == null ? View.GONE : View.VISIBLE);
            binding.financeTipNotice.setText(tip);
            if (tip != null) {
                binding.financeReportContent.setVisibility(View.GONE);
                binding.financeCheckCard.setVisibility(View.GONE);
                binding.financeReportEmpty.setVisibility(View.GONE);
            }
        });
        vm.getReport().observe(this, this::renderReport);
        vm.getMetric().observe(this, m -> renderMetrics());
        vm.getTrend().observe(this, this::renderTrend);
        vm.getSummary().observe(this, this::renderSummary);
        vm.getExporting().observe(this, e -> renderMenu());
        vm.getExportedFile().observe(this, f -> renderMenu());
        vm.getOpenSaveDocument().observe(this, f -> {
            try {
                saveXlsx.launch(f.getFileName());
            } catch (ActivityNotFoundException ignored) {
                // 没有系统“保存文件”界面时文件留在专属目录
            }
        });
        vm.getOpenFile().observe(this, this::openFile);
        vm.getShareFile().observe(this, this::shareFile);
    }

    @Override
    protected void onStart() {
        super.onStart();
        receiver.register(this);
        vm.checkPendingDownload();
    }

    @Override
    protected void onStop() {
        receiver.unregister(this);
        super.onStop();
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (BusinessReportViewModel.ACTION_OPEN_FILE.equals(actionId)) {
            vm.openExported();
        } else if (BusinessReportViewModel.ACTION_RETRY_EXPORT.equals(actionId)) {
            vm.retryExport();
        } else {
            super.onMessageAction(actionId);
        }
    }

    private void renderMenu() {
        Menu m = binding.financeTopBar.baseToolbar.getMenu();
        boolean exporting = Boolean.TRUE.equals(vm.getExporting().getValue());
        m.findItem(R.id.finance_menu_export_xlsx).setEnabled(!exporting);
        m.findItem(R.id.finance_menu_share).setVisible(vm.getExportedFile().getValue() != null);
        binding.financeTopBar.baseToolbar.setSubtitle(exporting ? "正在导出…" : null);
    }

    private void bindMetric(@NonNull FinanceViewMetricBinding m, int label, @NonNull String code) {
        m.financeMetricLabel.setText(label);
        ClickThrottle.setOnClickListener(m.getRoot(), v -> vm.selectMetric(code));
    }

    private void showPicker(@NonNull String which) {
        List<BusinessReportViewModel.PickOption> list;
        String title;
        switch (which) {
            case BusinessReportViewModel.PICK_FARM:
                list = vm.farmOptions();
                title = getString(R.string.finance_e13_pick_farm);
                break;
            case BusinessReportViewModel.PICK_RANGE:
                list = vm.rangeOptions();
                title = getString(R.string.finance_e13_pick_range);
                break;
            case FinanceConst.RANGE_CATEGORY:
                list = vm.categoryOptions();
                title = getString(R.string.finance_e13_pick_category);
                break;
            case FinanceConst.RANGE_BATCH:
                list = vm.batchOptions();
                title = getString(R.string.finance_e13_pick_batch);
                break;
            case BusinessReportViewModel.PICK_CHECK_BATCH:
                list = vm.checkBatchOptions();
                title = getString(R.string.finance_e13_pick_check);
                break;
            default:
                list = vm.periodOptions();
                title = getString(R.string.finance_e13_pick_period);
                break;
        }
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        String selected = null;
        for (BusinessReportViewModel.PickOption o : list) {
            options.add(new OptionSheetDialogFragment.Option(o.value, o.label));
            if (o.selected) {
                selected = o.value;
            }
        }
        Bundle extras = new Bundle();
        extras.putString(EXTRA_WHICH, which);
        OptionSheetDialogFragment.builder(REQ_PICK).title(title).options(options).selected(selected).extras(extras)
                .build().showSafely(getSupportFragmentManager());
    }

    private void onPicked(@NonNull Bundle r) {
        Bundle extras = OptionSheetDialogFragment.getExtras(r);
        String which = extras == null ? null : extras.getString(EXTRA_WHICH);
        String value = OptionSheetDialogFragment.getValue(r);
        String label = r.getString(OptionSheetDialogFragment.RESULT_LABEL);
        if (which == null || value == null) {
            return;
        }
        switch (which) {
            case BusinessReportViewModel.PICK_FARM:
                vm.selectFarm(value);
                break;
            case BusinessReportViewModel.PICK_RANGE:
                vm.selectRangeType(value);
                break;
            case FinanceConst.RANGE_CATEGORY:
            case FinanceConst.RANGE_BATCH:
                vm.selectRange(which, Long.parseLong(value), label == null ? value : label.split(" · ")[0]);
                break;
            case BusinessReportViewModel.PICK_CHECK_BATCH:
                vm.openBatchArchive(Long.parseLong(value));
                break;
            default:
                vm.selectPeriod(value);
                break;
        }
    }

    private void renderScope() {
        ReportQuery q = vm.getQuery().getValue();
        if (q == null) {
            return;
        }
        boolean toDate = FinanceConst.REPORT_TO_DATE.equals(q.getReportType());
        binding.financeChipPeriod.setVisibility(toDate ? View.GONE : View.VISIBLE);
        binding.financePeriodText.setVisibility(toDate ? View.VISIBLE : View.GONE);
        binding.financeChipPeriod.setText(vm.periodLabel());
        boolean notice = q.isAllFarms() || vm.isOwnedMode();
        binding.financeScopeNotice.setVisibility(notice ? View.VISIBLE : View.GONE);
        if (notice) {
            BusinessReportVO r = vm.getReport().getValue();
            String text = getString(vm.isOwnedMode() ? R.string.finance_e13_owned_notice : R.string.finance_e13_all_notice);
            if (!vm.isOwnedMode() && r != null && !r.getStaffFarmNames().isEmpty()) {
                text += String.join("、", r.getStaffFarmNames()) + "你是员工，不计入。";
            }
            binding.financeScopeNotice.setText(text);
        }
    }

    private void renderReport(@Nullable BusinessReportVO r) {
        if (r == null) {
            return;
        }
        binding.financeTipNotice.setVisibility(View.GONE);
        binding.financePeriodText.setText(r.getPeriod() == null ? null : r.getPeriod().getLabel());
        renderScope();
        binding.financeReportEmpty.setVisibility(r.isEmpty() ? View.VISIBLE : View.GONE);
        binding.financeReportContent.setVisibility(r.isEmpty() ? View.GONE : View.VISIBLE);
        renderCheck(r.getDataCheck());
        renderMetrics();
        renderProfits(r);
        binding.financeAiTitle.setText(getString(R.string.finance_e13_ai_title,
                r.getPeriod() == null ? "" : r.getPeriod().getLabel()));
    }

    private void renderCheck(@Nullable BusinessReportVO.DataCheck c) {
        boolean show = c != null && c.isHasIssue() && !c.getItems().isEmpty();
        binding.financeCheckCard.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.financeCheckItems.removeAllViews();
        if (!show) {
            return;
        }
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(
                getString(R.string.finance_e13_check_title));
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, sb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(String.valueOf(c.getBatchCount())).append(" 个批次的期初存栏加期间变动与期末存栏不一致，相关指标可能不准确。修正记录后报表会自动重新计算。");
        binding.financeCheckText.setText(sb);
        for (BusinessReportVO.CheckItem i : c.getItems()) {
            ListRowView row = new ListRowView(this);
            row.setTitle(i.getBatchNo() + (i.getPenName() == null ? "" : " · " + i.getPenName()));
            row.setSubtitle("期初加变动 " + i.getExpected() + " 头 · 期末 " + i.getActual() + " 头");
            row.setStatusTag("差 " + Math.abs(i.getDiff()) + " 头", StatusTagView.WARNING, 0);
            ClickThrottle.setOnClickListener(row, v -> vm.openBatchArchive(i.getBatchId()));
            binding.financeCheckItems.addView(row);
        }
        if (c.getMoreCount() > 0) {
            ListRowView more = new ListRowView(this);
            more.setTitle("还有 " + c.getMoreCount() + " 个批次");
            binding.financeCheckItems.addView(more);
        }
        binding.financeCheckItems.updatePositions();
    }

    private void renderMetrics() {
        BusinessReportVO r = vm.getReport().getValue();
        String sel = vm.getMetric().getValue();
        BusinessReportVO.ReportMetrics m = r == null ? null : r.getMetrics();
        // 期末存栏
        String stockValue = FinanceFormat.NO_DATA;
        String stockChange = "";
        if (m != null && !m.getStock().isEmpty()) {
            BusinessReportVO.StockMetric s = m.getStock().get(0);
            stockValue = FinanceFormat.count(s.getEnd(), s.getCountUnitText());
            stockChange = "较期初 " + (s.getChange() > 0 ? "+" : s.getChange() < 0 ? "−" : "") + Math.abs(s.getChange());
        }
        setMetric(binding.financeMetricStock, stockValue, stockChange, FinanceFormat.TONE_NEUTRAL, 0,
                FinanceConst.METRIC_STOCK.equals(sel));
        BusinessReportVO.Mortality mo = m == null ? null : m.getMortality();
        String rate = mo == null || mo.getRate() == null ? FinanceFormat.NO_DATA
                : mo.getRate().multiply(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString() + FinanceFormat.NBSP + "%";
        String moChange = mo == null || !mo.isComparable() || mo.getChangePoint() == null ? FinanceFormat.NOT_COMPARABLE
                : mo.getChangePoint().abs().multiply(BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_UP)
                .toPlainString() + " 个百分点";
        setMetric(binding.financeMetricMortality, rate, moChange, mo == null ? FinanceFormat.TONE_NONE
                        : FinanceFormat.tone(mo.getChangePoint(), mo.isComparable(), false),
                mo == null ? 0 : FinanceFormat.direction(mo.getChangePoint()), FinanceConst.METRIC_MORTALITY.equals(sel));
        BusinessReportVO.Feed f = m == null ? null : m.getFeed();
        setMetric(binding.financeMetricFeed, f == null || f.getKg() == null ? FinanceFormat.NO_DATA : FinanceFormat.kg(f.getKg()),
                f == null || !f.isComparable() || f.getChangeRate() == null ? FinanceFormat.NOT_COMPARABLE
                        : FinanceFormat.percent(f.getChangeRate().abs()), FinanceFormat.TONE_NEUTRAL,
                f == null ? 0 : FinanceFormat.direction(f.getChangeRate()), FinanceConst.METRIC_FEED.equals(sel));
        BusinessReportVO.Balance b = m == null ? null : m.getBalance();
        setMetric(binding.financeMetricBalance, b == null || b.getAmount() == null ? FinanceFormat.NO_DATA
                        : FinanceFormat.yuan(b.getAmount()),
                b == null || !b.isComparable() || b.getChangeRate() == null ? FinanceFormat.NOT_COMPARABLE
                        : FinanceFormat.percent(b.getChangeRate().abs()),
                b == null ? FinanceFormat.TONE_NONE : FinanceFormat.tone(b.getChangeRate(), b.isComparable(), true),
                b == null ? 0 : FinanceFormat.direction(b.getChangeRate()), FinanceConst.METRIC_BALANCE.equals(sel));
    }

    private void setMetric(@NonNull FinanceViewMetricBinding v, @NonNull String value, @NonNull String change, int tone,
                           int direction, boolean selected) {
        View root = v.getRoot();
        root.setBackgroundTintList(ColorStateList.valueOf(FinanceViews.color(root, selected
                ? com.google.android.material.R.attr.colorSecondaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerLow)));
        int label = FinanceViews.color(root, selected ? com.google.android.material.R.attr.colorOnSecondaryContainer
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        v.financeMetricLabel.setTextColor(label);
        v.financeMetricValue.setText(value);
        v.financeMetricValue.setTextColor(FinanceViews.color(root, selected
                ? com.google.android.material.R.attr.colorOnSecondaryContainer
                : com.google.android.material.R.attr.colorOnSurface));
        FinanceViews.setTrend(v.financeMetricChange, change, tone, direction);
        if (tone == FinanceFormat.TONE_NEUTRAL || tone == FinanceFormat.TONE_NONE) {
            v.financeMetricChange.setTextColor(label);
        }
        root.setSelected(selected);
    }

    private void renderTrend(@Nullable ReportTrendVO t) {
        if (t == null) {
            binding.financeTrend.clear();
            binding.financeEquation.setVisibility(View.GONE);
            return;
        }
        binding.financeTrend.setTrend(t);
        ReportTrendVO.StockEquation e = t.getEquation();
        boolean show = FinanceConst.METRIC_STOCK.equals(t.getMetric()) && e != null;
        binding.financeEquation.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.financeEquation.setText("期末 " + fmt(e.getEnd()) + " = 期初 " + fmt(e.getBegin()) + " + 入栏 "
                    + fmt(e.getEntry()) + " − 死淘 " + fmt(e.getDeath()) + " − 出栏 " + fmt(e.getSold()));
        }
    }

    @NonNull
    private static String fmt(int n) {
        return FinanceFormat.num(BigDecimal.valueOf(n));
    }

    private void renderSummary(@Nullable ReportSummaryVO s) {
        boolean generating = s == null || FinanceConst.GEN_GENERATING.equals(s.getStatus()) && s.getHighlight() == null;
        boolean failed = s != null && FinanceConst.GEN_FAILED.equals(s.getStatus());
        binding.financeAiSkeleton.setVisibility(generating ? View.VISIBLE : View.GONE);
        binding.financeAiFailed.setVisibility(failed ? View.VISIBLE : View.GONE);
        binding.financeAiRegenerate.setEnabled(!(s != null && FinanceConst.GEN_GENERATING.equals(s.getStatus())));
        binding.financeAiRows.removeAllViews();
        boolean content = s != null && !failed && s.getHighlight() != null;
        binding.financeAiRows.setVisibility(content ? View.VISIBLE : View.GONE);
        if (!content) {
            return;
        }
        addSummaryRow(R.string.finance_e13_key_highlight, s.getHighlight());
        addSummaryRow(R.string.finance_e13_key_problem, s.getProblem());
        addSummaryRow(R.string.finance_e13_key_suggestion, s.getSuggestion());
    }

    private void addSummaryRow(int key, @Nullable String value) {
        if (value == null) {
            return;
        }
        FinanceItemKeyValueBinding row = FinanceItemKeyValueBinding.inflate(LayoutInflater.from(this),
                binding.financeAiRows, false);
        row.financeKvKey.setText(key);
        row.financeKvKey.setTextColor(FinanceViews.color(row.getRoot(), com.google.android.material.R.attr.colorOnSurface));
        row.financeKvValue.setText(value);
        binding.financeAiRows.addView(row.getRoot());
    }

    private void renderProfits(@NonNull BusinessReportVO r) {
        BusinessReportVO.BatchProfits p = r.getBatchProfits();
        List<ProfitBarsView.Row> rows = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        if (p != null) {
            for (BusinessReportVO.ProfitItem i : p.getItems()) {
                String label = r.isEmpty() || i.getFarmName() == null || !FinanceConst.SCOPE_ALL.equals(r.getScope())
                        ? i.getBatchNo() : i.getFarmName() + " " + i.getBatchNo();
                rows.add(new ProfitBarsView.Row(label, i.getProfit() == null ? 0 : i.getProfit().floatValue(),
                        FinanceFormat.signed(i.getProfit()) + " 元"));
                ids.add(i.getBatchId());
            }
        }
        binding.financeProfitBars.setRows(rows);
        binding.financeProfitBars.setOnRowClickListener(i -> vm.openBatchSettlement(ids.get(i)));
        binding.financeProfitEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        BigDecimal total = p == null ? null : p.getProfitTotal();
        binding.financeProfitTotal.setText(total == null ? null : "本期合计 " + FinanceFormat.signed(total) + " 元");
        binding.financeProfitTotal.setTextColor(FinanceViews.color(binding.financeProfitTotal, total != null
                && total.signum() < 0 ? androidx.appcompat.R.attr.colorError : com.zhimu.slmp.base.R.attr.colorSuccess));
        StringBuilder running = new StringBuilder();
        for (BusinessReportVO.RunningBatch b : r.getRunningBatches()) {
            running.append(running.length() == 0 ? "" : "，").append(b.getBatchNo()).append(' ')
                    .append(FinanceFormat.yuan(b.getCostTotal()));
        }
        binding.financeRunningNote.setVisibility(running.length() == 0 ? View.GONE : View.VISIBLE);
        binding.financeRunningNote.setText("仍在饲养的批次只显示累计成本：" + running + "。");
    }

    private void openFile(@NonNull ExportedFile f) {
        try {
            Uri uri = new ReportFileHelper(this).contentUri(f);
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, f.getMimeType())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, getString(R.string.finance_e13_open_chooser)));
        } catch (RuntimeException e) {
            showMessage(getString(R.string.finance_e13_no_app));
        }
    }

    private void shareFile(@NonNull ExportedFile f) {
        try {
            Uri uri = new ReportFileHelper(this).contentUri(f);
            Intent send = new Intent(Intent.ACTION_SEND).setType(f.getMimeType()).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri(f.getFileName(), uri));
            startActivity(Intent.createChooser(send, getString(R.string.finance_e13_share_chooser)));
        } catch (RuntimeException e) {
            showMessage(getString(R.string.finance_e13_no_app));
        }
    }
}
