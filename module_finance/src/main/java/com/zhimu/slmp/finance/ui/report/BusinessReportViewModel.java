package com.zhimu.slmp.finance.ui.report;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.ReportRepository;
import com.zhimu.slmp.finance.data.SettlementRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.ReportQuery;
import com.zhimu.slmp.finance.model.vo.BusinessReportVO;
import com.zhimu.slmp.finance.model.vo.ReportPeriodVO;
import com.zhimu.slmp.finance.model.vo.ReportSummaryVO;
import com.zhimu.slmp.finance.model.vo.ReportTrendVO;
import com.zhimu.slmp.finance.model.vo.SettlementOptionVO;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.util.ExportedFile;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.NavTarget;
import com.zhimu.slmp.finance.util.ReportFileHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * E13 经营报表：周报 / 月报 / 本期至今，养殖场（含全部养殖场合计）与统计范围，AI 分析摘要（轮询 API-E40），
 * 四项指标与趋势图（API-E39），批次盈亏对比；导出表格 / 文档（API-E42 → 系统下载 → 另存、打开、分享，技术准备文档 2.6.4）。
 */
public class BusinessReportViewModel extends FinanceViewModel {

    public static final String PICK_FARM = "farm";
    public static final String PICK_RANGE = "range";
    public static final String PICK_PERIOD = "period";
    public static final String PICK_CHECK_BATCH = "check_batch";
    public static final String ACTION_OPEN_FILE = "finance_open_report";
    public static final String ACTION_RETRY_EXPORT = "finance_retry_export";
    static final String MSG_EXPORT_FAILED = "导出失败，请检查网络后重试";
    /** 系统下载每 2 秒查一次进度；连续 {@link #exportStallSec} 秒没有进展（含 PAUSED 等待重试）按失败处理 */
    static final long EXPORT_WATCH_INTERVAL_SEC = 2;
    @androidx.annotation.VisibleForTesting
    static long exportStallSec = 60;
    static final String KEY_DOWNLOAD_ID = "finance_report_download_id";
    static final String KEY_DOWNLOAD_FORMAT = "finance_report_download_format";

    /** 单选面板的一项 */
    public static final class PickOption {
        public final String value;
        public final String label;
        public final boolean selected;

        PickOption(String value, String label, boolean selected) {
            this.value = value;
            this.label = label;
            this.selected = selected;
        }
    }

    private final ReportRepository repository;
    private final SettlementRepository settlementRepository;
    private final BaseDataRepository baseData;
    private final ReportFileHelper files;

    private final MutableLiveData<String> reportType = new MutableLiveData<>(FinanceConst.REPORT_TO_DATE);
    private final MutableLiveData<ReportPeriodVO> periods = new MutableLiveData<>();
    private final MutableLiveData<ReportQuery> query = new MutableLiveData<>(ReportQuery.toDate());
    private final MutableLiveData<BusinessReportVO> report = new MutableLiveData<>();
    private final MutableLiveData<String> metric = new MutableLiveData<>(FinanceConst.METRIC_STOCK);
    private final MutableLiveData<ReportTrendVO> trend = new MutableLiveData<>();
    private final MutableLiveData<ReportSummaryVO> summary = new MutableLiveData<>();
    private final MutableLiveData<Boolean> exporting = new MutableLiveData<>(false);
    private final MutableLiveData<ExportedFile> exportedFile = new MutableLiveData<>();
    private final MutableLiveData<String> notGeneratedTip = new MutableLiveData<>();
    private final MutableLiveData<String> rangeLabel = new MutableLiveData<>("整场");
    private final MutableLiveData<String> farmLabel = new MutableLiveData<>("");
    private final SingleLiveEvent<String> showPicker = new SingleLiveEvent<>();
    private final SingleLiveEvent<ExportedFile> openSaveDocument = new SingleLiveEvent<>();
    private final SingleLiveEvent<ExportedFile> openFile = new SingleLiveEvent<>();
    private final SingleLiveEvent<ExportedFile> shareFile = new SingleLiveEvent<>();

    private final List<FarmBriefVO> farms = new ArrayList<>();
    private final List<CategoryVO> categories = new ArrayList<>();
    private final List<SettlementOptionVO> batches = new ArrayList<>();
    @Nullable
    private Disposable summaryPoll;
    @Nullable
    private Disposable downloadWatch;
    @Nullable
    private String presetPeriod;
    private boolean regeneratingSummary;
    private boolean ownedMode;

    @Inject
    public BusinessReportViewModel(@NonNull ReportRepository repository,
                                   @NonNull SettlementRepository settlementRepository,
                                   @NonNull BaseDataRepository baseData, @NonNull ReportFileHelper files,
                                   @NonNull FarmContext farm) {
        super(farm);
        this.repository = repository;
        this.settlementRepository = settlementRepository;
        this.baseData = baseData;
        this.files = files;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        if (denyStaff()) {
            return;
        }
        String type = handle.get(RouterPath.EXTRA_REPORT_TYPE);
        presetPeriod = handle.get(RouterPath.EXTRA_PERIOD_START);
        ownedMode = RouterPath.MODE_OWNED_FARMS.equals(handle.get(RouterPath.EXTRA_MODE));
        ReportQuery q = ReportQuery.toDate();
        if (ownedMode) {
            q = q.withScope(FinanceConst.SCOPE_ALL);
        }
        query.setValue(q);
        updateFarmLabel();
        loadPickers();
        disposables.add(Completable.fromAction(files::cleanupOld).subscribeOn(Schedulers.io())
                .onErrorComplete().subscribe());
        if (type != null && !FinanceConst.REPORT_TO_DATE.equals(type)) {
            setReportType(type);
        } else {
            loadReport();
        }
    }

    // ===== 对外 =====

    public LiveData<String> getReportType() {
        return reportType;
    }

    public LiveData<ReportPeriodVO> getPeriods() {
        return periods;
    }

    public LiveData<ReportQuery> getQuery() {
        return query;
    }

    public LiveData<BusinessReportVO> getReport() {
        return report;
    }

    public LiveData<String> getMetric() {
        return metric;
    }

    public LiveData<ReportTrendVO> getTrend() {
        return trend;
    }

    public LiveData<ReportSummaryVO> getSummary() {
        return summary;
    }

    public LiveData<Boolean> getExporting() {
        return exporting;
    }

    public LiveData<ExportedFile> getExportedFile() {
        return exportedFile;
    }

    public LiveData<String> getNotGeneratedTip() {
        return notGeneratedTip;
    }

    public LiveData<String> getRangeLabel() {
        return rangeLabel;
    }

    public LiveData<String> getFarmLabel() {
        return farmLabel;
    }

    public SingleLiveEvent<String> getShowPicker() {
        return showPicker;
    }

    public SingleLiveEvent<ExportedFile> getOpenSaveDocument() {
        return openSaveDocument;
    }

    public SingleLiveEvent<ExportedFile> getOpenFile() {
        return openFile;
    }

    public SingleLiveEvent<ExportedFile> getShareFile() {
        return shareFile;
    }

    public boolean isOwnedMode() {
        return ownedMode;
    }

    @NonNull
    private ReportQuery q() {
        ReportQuery v = query.getValue();
        return v == null ? ReportQuery.toDate() : v;
    }

    private void loadPickers() {
        IFarmContextService svc = ServiceHub.get(IFarmContextService.class);
        if (svc != null) {
            launch(svc.listMyFarms(), list -> {
                farms.clear();
                for (FarmBriefVO f : list) {
                    if (!"STAFF".equals(f.getRole())) {
                        farms.add(f);
                    }
                }
            }, e -> {
            });
        }
        launch(baseData.getCategories(), list -> {
            categories.clear();
            categories.addAll(list);
        }, e -> {
        });
        launch(settlementRepository.getOptions(null, 1, SettlementRepository.OPTION_SIZE), p -> {
            batches.clear();
            batches.addAll(p.getList());
        }, e -> {
        });
    }

    // ===== 报表 =====

    public void loadReport() {
        ReportQuery q = q();
        if (!q.isComplete()) {
            return;
        }
        cancelSummary();
        notGeneratedTip.setValue(null);
        load(repository.getReport(q), r -> {
            report.setValue(r);
            if (!r.isEmpty()) {
                loadTrend();
                loadSummary();
            } else {
                trend.setValue(null);
                summary.setValue(null);
            }
        });
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data == null;
    }

    @Override
    public void retry() {
        if (FinanceConst.REPORT_TO_DATE.equals(reportType.getValue()) || q().isComplete()) {
            loadReport();
        } else {
            setReportType(reportType.getValue());
        }
    }

    public void refresh() {
        retry();
    }

    /** 切换周报 / 月报 / 本期至今：周报、月报先取期次（API-E37） */
    public void setReportType(@NonNull String type) {
        reportType.setValue(type);
        if (FinanceConst.REPORT_TO_DATE.equals(type)) {
            query.setValue(q().withType(type, null));
            loadReport();
            return;
        }
        cancelSummary();
        report.setValue(null);
        setUiState(UiState.loading());
        launch(repository.getPeriods(type, q().getScope()), p -> {
            periods.setValue(p);
            if (p.getPeriods().isEmpty()) {
                query.setValue(q().withType(type, null));
                String next = p.getNextGenerationDate();
                notGeneratedTip.setValue((FinanceConst.REPORT_WEEKLY.equals(type) ? "本周的周报将在 " : "本月的月报将在 ")
                        + FinanceFormat.dateWeek(next) + "生成，可以先查看本期至今。");
                setUiState(UiState.content());
                return;
            }
            String start = p.getPeriods().get(0).getPeriodStart();
            if (presetPeriod != null) {
                for (ReportPeriodVO.ReportPeriod rp : p.getPeriods()) {
                    if (presetPeriod.equals(rp.getPeriodStart())) {
                        start = presetPeriod;
                    }
                }
                presetPeriod = null;
            }
            query.setValue(q().withType(type, start));
            loadReport();
        }, e -> applyErrorState(e));
    }

    public void selectPeriod(@NonNull String periodStart) {
        if (Objects.equals(periodStart, q().getPeriodStart())) {
            return;
        }
        query.setValue(q().withPeriod(periodStart));
        loadReport();
    }

    /** 期次 Chip 文字“10月12日—18日”“9月” */
    @NonNull
    public String periodLabel() {
        ReportPeriodVO p = periods.getValue();
        String start = q().getPeriodStart();
        if (p != null && start != null) {
            for (ReportPeriodVO.ReportPeriod rp : p.getPeriods()) {
                if (start.equals(rp.getPeriodStart())) {
                    return rp.getLabel();
                }
            }
        }
        return "";
    }

    // ===== 养殖场与范围 =====

    public void openPicker(@NonNull String which) {
        showPicker.setValue(which);
    }

    @NonNull
    public List<PickOption> farmOptions() {
        List<PickOption> list = new ArrayList<>();
        boolean all = q().isAllFarms();
        for (FarmBriefVO f : farms) {
            list.add(new PickOption(String.valueOf(f.getFarmId()), f.getName(),
                    !all && f.getFarmId() == farm.farmIdOrZero()));
        }
        if (list.isEmpty()) {
            list.add(new PickOption(String.valueOf(farm.farmIdOrZero()), currentFarmName(), !all));
        }
        list.add(new PickOption(FinanceConst.SCOPE_ALL, "全部养殖场合计", all));
        return list;
    }

    @NonNull
    public List<PickOption> rangeOptions() {
        String t = q().getRangeType() == null ? FinanceConst.RANGE_FARM : q().getRangeType();
        List<PickOption> list = new ArrayList<>();
        list.add(new PickOption(FinanceConst.RANGE_FARM, "整场", FinanceConst.RANGE_FARM.equals(t)));
        list.add(new PickOption(FinanceConst.RANGE_CATEGORY, "按品类", FinanceConst.RANGE_CATEGORY.equals(t)));
        if (!q().isAllFarms()) {
            list.add(new PickOption(FinanceConst.RANGE_BATCH, "按批次", FinanceConst.RANGE_BATCH.equals(t)));
        }
        return list;
    }

    @NonNull
    public List<PickOption> categoryOptions() {
        List<PickOption> list = new ArrayList<>();
        for (CategoryVO c : categories) {
            list.add(new PickOption(String.valueOf(c.getId()), c.getName(),
                    Objects.equals(q().getRangeRefId(), c.getId())));
        }
        return list;
    }

    @NonNull
    public List<PickOption> batchOptions() {
        List<PickOption> list = new ArrayList<>();
        for (SettlementOptionVO b : batches) {
            list.add(new PickOption(String.valueOf(b.getBatchId()),
                    b.getBatchNo() + (b.getPenName() == null ? "" : " · " + b.getPenName()),
                    Objects.equals(q().getRangeRefId(), b.getBatchId())));
        }
        return list;
    }

    @NonNull
    public List<PickOption> periodOptions() {
        List<PickOption> list = new ArrayList<>();
        ReportPeriodVO p = periods.getValue();
        if (p != null) {
            for (ReportPeriodVO.ReportPeriod rp : p.getPeriods()) {
                list.add(new PickOption(rp.getPeriodStart(), rp.getLabel(),
                        rp.getPeriodStart().equals(q().getPeriodStart())));
            }
        }
        return list;
    }

    /** 选择养殖场：其他养殖场先切换当前养殖场（F03）；“全部养殖场合计”为 scope=ALL */
    public void selectFarm(@NonNull String value) {
        if (FinanceConst.SCOPE_ALL.equals(value)) {
            selectAllFarms();
            return;
        }
        long id = Long.parseLong(value);
        IFarmContextService svc = ServiceHub.get(IFarmContextService.class);
        if (id == farm.farmIdOrZero() || svc == null) {
            query.setValue(q().withScope(null));
            rangeLabel.setValue("整场");
            updateFarmLabel();
            reloadForScope();
            return;
        }
        launch(svc.switchTo(id), ctx -> {
            query.setValue(q().withScope(null));
            rangeLabel.setValue("整场");
            updateFarmLabel();
            loadPickers();
            reloadForScope();
            showMessage(UiMessage.of("已切换到" + ctx.getName()));
        });
    }

    public void selectAllFarms() {
        if (q().isAllFarms()) {
            return;
        }
        query.setValue(q().withScope(FinanceConst.SCOPE_ALL));
        rangeLabel.setValue("整场");
        updateFarmLabel();
        reloadForScope();
    }

    private void reloadForScope() {
        String type = reportType.getValue();
        if (FinanceConst.REPORT_TO_DATE.equals(type)) {
            loadReport();
        } else {
            setReportType(type);
        }
    }

    /** 选择统计范围：整场直接生效；品类、批次在面板第二步选择具体对象 */
    public void selectRangeType(@NonNull String type) {
        if (FinanceConst.RANGE_FARM.equals(type)) {
            selectRange(FinanceConst.RANGE_FARM, null, "整场");
        } else {
            showPicker.setValue(FinanceConst.RANGE_CATEGORY.equals(type) ? FinanceConst.RANGE_CATEGORY
                    : FinanceConst.RANGE_BATCH);
        }
    }

    public void selectRange(@NonNull String type, @Nullable Long refId, @NonNull String label) {
        ReportQuery next = q().withRange(type, refId);
        if (next.equals(q())) {
            return;
        }
        query.setValue(next);
        rangeLabel.setValue(label);
        loadReport();
    }

    private void updateFarmLabel() {
        farmLabel.setValue(q().isAllFarms() ? "全部养殖场合计" : currentFarmName());
    }

    @NonNull
    private String currentFarmName() {
        return farm.current() == null || farm.current().getName() == null ? "当前养殖场" : farm.current().getName();
    }

    // ===== 指标与趋势 =====

    public void selectMetric(@NonNull String m) {
        if (m.equals(metric.getValue())) {
            return;
        }
        metric.setValue(m);
        loadTrend();
    }

    void loadTrend() {
        ReportQuery q = q().withMetric(metric.getValue());
        launch(repository.getReportTrend(q), t -> {
            if (Objects.equals(q.getMetric(), metric.getValue())) {
                trend.setValue(t);
            }
        }, e -> trend.setValue(null));
    }

    // ===== AI 摘要 =====

    void loadSummary() {
        cancelSummary();
        summary.setValue(null);
        ReportQuery q = q();
        final ReportSummaryVO[] last = new ReportSummaryVO[1];
        summaryPoll = repository.pollSummary(q)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> {
                    last[0] = s;
                    summary.setValue(s);
                    if (regeneratingSummary && FinanceConst.GEN_SUCCESS.equals(s.getStatus()) && !s.isOutdated()) {
                        regeneratingSummary = false;
                        showMessage("已重新生成 AI 摘要");
                    }
                }, e -> summary.setValue(failedSummary()), () -> {
                    if (last[0] == null || FinanceConst.GEN_GENERATING.equals(last[0].getStatus())) {
                        summary.setValue(failedSummary());
                    }
                    regeneratingSummary = false;
                });
        disposables.add(summaryPoll);
    }

    /** 重新生成（API-E41），生成中重复调用返回当前状态 */
    public void regenerateSummary() {
        ReportSummaryVO s = summary.getValue();
        if (s != null && FinanceConst.GEN_GENERATING.equals(s.getStatus())) {
            return;
        }
        cancelSummary();
        regeneratingSummary = true;
        launch(repository.regenerateSummary(q()), r -> {
            summary.setValue(r);
            if (FinanceConst.GEN_GENERATING.equals(r.getStatus())) {
                loadSummaryKeepFlag();
            } else {
                regeneratingSummary = false;
                if (FinanceConst.GEN_SUCCESS.equals(r.getStatus())) {
                    showMessage("已重新生成 AI 摘要");
                }
            }
        }, e -> {
            regeneratingSummary = false;
            handleError(e);
        });
    }

    private void loadSummaryKeepFlag() {
        boolean keep = regeneratingSummary;
        loadSummary();
        regeneratingSummary = keep;
    }

    @NonNull
    private static ReportSummaryVO failedSummary() {
        return com.zhimu.slmp.network.GsonFactory.get().fromJson("{\"status\":\"FAILED\"}", ReportSummaryVO.class);
    }

    private void cancelSummary() {
        if (summaryPoll != null) {
            summaryPoll.dispose();
            summaryPoll = null;
        }
    }

    // ===== 导出 =====

    /**
     * 导出（API-E42，读超时 45 秒）→ 系统下载；下载完成后另存并提示“打开”。
     * 下载失败或 60 秒没有进展时取消下载、恢复按钮，并给出“重试”。
     */
    public void export(@NonNull String format) {
        if (Boolean.TRUE.equals(exporting.getValue()) || !q().isComplete()) {
            return;
        }
        exporting.setValue(true);
        getSavedState().set(KEY_DOWNLOAD_FORMAT, format);
        launch(repository.exportReport(q().toExport(format))
                .flatMap(vo -> Single.fromCallable(() -> files.enqueue(vo))), id -> {
            getSavedState().set(KEY_DOWNLOAD_ID, id);
            if (id == ReportFileHelper.LOCAL_DONE) {
                onDownloadComplete(id);
            } else {
                watchDownload(id);
            }
        }, e -> {
            exporting.setValue(false);
            exportFailed();
        });
    }

    /** 失败提示里的“重试”：按上次的格式再导出一次 */
    public void retryExport() {
        String format = pendingFormat();
        export(format == null ? FinanceConst.FORMAT_XLSX : format);
    }

    /** 下载完成广播或回到前台时按 downloadId 主动查一次 */
    public void onDownloadComplete(long id) {
        Long pending = getSavedState().get(KEY_DOWNLOAD_ID);
        if (pending == null || pending != id) {
            return;
        }
        launch(Single.fromCallable(() -> files.query(id)), st -> {
            if (st.state == ReportFileHelper.Status.RUNNING) {
                if (downloadWatch == null || downloadWatch.isDisposed()) {
                    watchDownload(id);
                }
                return;
            }
            finishDownload(st);
        }, e -> {
            stopDownloadWatch();
            getSavedState().set(KEY_DOWNLOAD_ID, null);
            exporting.setValue(false);
            exportFailed();
        });
    }

    /** 轮询系统下载：完成或失败时收尾；已下载字节数连续 exportStallSec 秒不变时取消下载并提示失败 */
    private void watchDownload(long id) {
        stopDownloadWatch();
        final long[] lastBytes = {-1};
        final long[] idleSec = {0};
        downloadWatch = io.reactivex.rxjava3.core.Observable
                .interval(EXPORT_WATCH_INTERVAL_SEC, EXPORT_WATCH_INTERVAL_SEC, java.util.concurrent.TimeUnit.SECONDS)
                .concatMapSingle(t -> Single.fromCallable(() -> files.query(id)).subscribeOn(Schedulers.io()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(st -> {
                    Long pending = getSavedState().get(KEY_DOWNLOAD_ID);
                    if (pending == null || pending != id) {
                        stopDownloadWatch();
                        return;
                    }
                    if (st.state != ReportFileHelper.Status.RUNNING) {
                        finishDownload(st);
                        return;
                    }
                    if (st.bytes > lastBytes[0]) {
                        lastBytes[0] = st.bytes;
                        idleSec[0] = 0;
                    } else {
                        idleSec[0] += EXPORT_WATCH_INTERVAL_SEC;
                    }
                    if (idleSec[0] >= exportStallSec) {
                        stopDownloadWatch();
                        getSavedState().set(KEY_DOWNLOAD_ID, null);
                        disposables.add(Completable.fromAction(() -> files.cancel(id))
                                .subscribeOn(Schedulers.io()).onErrorComplete().subscribe());
                        exporting.setValue(false);
                        exportFailed();
                    }
                }, e -> {
                    stopDownloadWatch();
                    getSavedState().set(KEY_DOWNLOAD_ID, null);
                    exporting.setValue(false);
                    exportFailed();
                });
        disposables.add(downloadWatch);
    }

    private void stopDownloadWatch() {
        if (downloadWatch != null) {
            downloadWatch.dispose();
            disposables.remove(downloadWatch);
            downloadWatch = null;
        }
    }

    private void finishDownload(@NonNull ReportFileHelper.Status st) {
        stopDownloadWatch();
        getSavedState().set(KEY_DOWNLOAD_ID, null);
        exporting.setValue(false);
        if (st.state == ReportFileHelper.Status.SUCCESSFUL && st.file != null) {
            exportedFile.setValue(st.file);
            openSaveDocument.setValue(st.file);
            showMessage(UiMessage.withAction("已导出经营报表", "打开", ACTION_OPEN_FILE));
        } else {
            exportFailed();
        }
    }

    private void exportFailed() {
        showMessage(UiMessage.withAction(MSG_EXPORT_FAILED, "重试", ACTION_RETRY_EXPORT));
    }

    public void checkPendingDownload() {
        Long pending = getSavedState().get(KEY_DOWNLOAD_ID);
        if (pending != null) {
            exporting.setValue(true);
            onDownloadComplete(pending);
        }
    }

    /** CreateDocument 选择位置后复制；取消时文件留在专属目录 */
    public void saveAs(@Nullable Uri target) {
        ExportedFile f = exportedFile.getValue();
        if (f == null || target == null) {
            return;
        }
        launch(Completable.fromAction(() -> files.copyTo(f, target)), () -> showMessage("已保存到所选位置"),
                e -> showMessage("保存失败，请重新选择位置"));
    }

    public void openExported() {
        ExportedFile f = exportedFile.getValue();
        if (f != null) {
            openFile.setValue(f);
        }
    }

    public void shareExported() {
        ExportedFile f = exportedFile.getValue();
        if (f == null) {
            showMessage("请先导出表格或文档");
            return;
        }
        shareFile.setValue(f);
    }

    @Nullable
    public String pendingFormat() {
        return getSavedState().get(KEY_DOWNLOAD_FORMAT);
    }

    // ===== 跳转 =====

    public void openBatchSettlement(long batchId) {
        open(NavTarget.of(RouterPath.E10).put(RouterPath.EXTRA_BATCH_ID, batchId));
    }

    public void openBatchArchive(long batchId) {
        open(NavTarget.of(RouterPath.L06).put(RouterPath.EXTRA_BATCH_ID, batchId).forResult());
    }

    /** “查看批次记录”：1 个差异批次直接进入 L06，多个时选择 */
    public void openCheckRecords() {
        BusinessReportVO r = report.getValue();
        List<BusinessReportVO.CheckItem> items = r == null || r.getDataCheck() == null ? Collections.emptyList()
                : r.getDataCheck().getItems();
        if (items.size() == 1) {
            openBatchArchive(items.get(0).getBatchId());
        } else if (!items.isEmpty()) {
            showPicker.setValue(PICK_CHECK_BATCH);
        }
    }

    @NonNull
    public List<PickOption> checkBatchOptions() {
        List<PickOption> list = new ArrayList<>();
        BusinessReportVO r = report.getValue();
        if (r != null && r.getDataCheck() != null) {
            for (BusinessReportVO.CheckItem i : r.getDataCheck().getItems()) {
                list.add(new PickOption(String.valueOf(i.getBatchId()),
                        i.getBatchNo() + (i.getPenName() == null ? "" : " · " + i.getPenName()), false));
            }
        }
        return list;
    }

    @Override
    protected void onCleared() {
        cancelSummary();
        super.onCleared();
    }
}
