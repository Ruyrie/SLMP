package com.zhimu.slmp.finance.ui.recordlist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.FinanceRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.FinanceRecordQuery;
import com.zhimu.slmp.finance.model.request.VersionRequest;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordPageVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.ui.common.FinanceListViewModel;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.NavTarget;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * E04 收支流水：按月份、类型、分类、批次、凭证与关键词筛选，按记账日期分组（API-E03）；
 * 自动记录跳原单据，手工记录修改（E03）、查看修改记录（U14）、作废（E04 取 version → E07）。
 */
public class FinanceRecordListViewModel extends FinanceListViewModel<FinanceRecordQuery, FinanceRecordVO> {

    static final long SEARCH_DEBOUNCE_MS = 300;
    static final int KEYWORD_MAX = 30;

    private final FinanceRepository repository;
    private final BaseDataRepository baseData;

    private final MutableLiveData<FinanceRecordPageVO.Totals> totals = new MutableLiveData<>();
    private final MutableLiveData<Long> highlightId = new MutableLiveData<>();
    private final MutableLiveData<Boolean> searchMode = new MutableLiveData<>(false);
    private final MutableLiveData<List<FinanceCategoryVO>> expenseCategories =
            new MutableLiveData<>(Collections.emptyList());
    private final SingleLiveEvent<List<RecordLogVO>> showLogSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<FinanceRecordVO> confirmVoid = new SingleLiveEvent<>();
    private final PublishSubject<String> searchInput = PublishSubject.create();

    @Inject
    public FinanceRecordListViewModel(@NonNull FinanceRepository repository, @NonNull BaseDataRepository baseData,
                                      @NonNull FarmContext farm) {
        super(farm, null);
        this.repository = repository;
        this.baseData = baseData;
        disposables.add(searchInput
                .debounce(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS, Schedulers.computation())
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::applyKeyword, e -> {
                }));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        if (denyStaff()) {
            return;
        }
        String month = handle.get(FinanceExtras.MONTH);
        FinanceRecordQuery q = FinanceRecordQuery.of(month != null && month.length() == 7 ? month
                : FinanceFormat.currentMonth());
        String ids = handle.get(FinanceExtras.CATEGORY_IDS);
        if (ids != null) {
            q = q.withCategories(ids, handle.get(FinanceExtras.CATEGORY_NAME));
        }
        Long batch = handle.get(FinanceExtras.BATCH_ID);
        if (batch != null && batch > 0) {
            q = q.withBatch(batch, handle.get(FinanceExtras.BATCH_NO));
        }
        String type = handle.get(FinanceExtras.RECORD_FILTER_TYPE);
        if (type != null) {
            q = q.withType(type);
        }
        if (FinanceConst.SCOPE_ALL.equals(handle.get(FinanceExtras.SCOPE))) {
            q = q.withScope(FinanceConst.SCOPE_ALL);
        }
        Long highlight = handle.get(FinanceExtras.HIGHLIGHT_ID);
        if (highlight != null && highlight > 0) {
            highlightId.setValue(highlight);
        }
        setFilter(q);
        launch(repository.getCategories(FinanceConst.EXPENSE), expenseCategories::setValue, e -> {
        });
    }

    @NonNull
    @Override
    protected PagingSource<Integer, FinanceRecordVO> createPagingSource(@Nullable FinanceRecordQuery filter) {
        FinanceRecordQuery q = filter == null ? FinanceRecordQuery.of(FinanceFormat.currentMonth()) : filter;
        return repository.pagingRecords(q, p -> totals.postValue(p.getTotals()));
    }

    public LiveData<FinanceRecordPageVO.Totals> getTotals() {
        return totals;
    }

    public LiveData<Long> getHighlightId() {
        return highlightId;
    }

    public LiveData<Boolean> getSearchMode() {
        return searchMode;
    }

    public LiveData<List<FinanceCategoryVO>> getExpenseCategories() {
        return expenseCategories;
    }

    public SingleLiveEvent<List<RecordLogVO>> getShowLogSheet() {
        return showLogSheet;
    }

    public SingleLiveEvent<FinanceRecordVO> getConfirmVoid() {
        return confirmVoid;
    }

    /** 合计文字“收入 400,000 元 · 支出 312,400 元” */
    @NonNull
    public static String totalsText(@Nullable FinanceRecordPageVO.Totals t) {
        if (t == null) {
            return "";
        }
        return "收入 " + FinanceFormat.yuan(t.getIncome()) + " · 支出 " + FinanceFormat.yuan(t.getExpenseAmount());
    }

    @NonNull
    private FinanceRecordQuery query() {
        FinanceRecordQuery q = currentFilter();
        return q == null ? FinanceRecordQuery.of(FinanceFormat.currentMonth()) : q;
    }

    public void setMonth(@NonNull String ym) {
        setFilter(query().withMonth(ym));
    }

    public void setType(@NonNull String type) {
        setFilter(query().withType(type));
    }

    public void clearCategory() {
        setFilter(query().withCategories(null, null));
    }

    public void clearBatch() {
        setFilter(query().withBatch(null, null));
    }

    /** 筛选面板“应用” */
    public void applyFilter(@Nullable String categoryIds, @Nullable String categoryLabel,
                            @Nullable Boolean hasVoucher) {
        setFilter(query().withCategories(categoryIds, categoryLabel).withHasVoucher(hasVoucher));
    }

    public void enterSearch() {
        searchMode.setValue(true);
    }

    public void exitSearch() {
        searchMode.setValue(false);
        setFilter(query().withKeyword(null));
    }

    /** 搜索框输入（防抖 300 毫秒，最多 30 字） */
    public void search(@Nullable String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.length() > KEYWORD_MAX) {
            k = k.substring(0, KEYWORD_MAX);
        }
        searchInput.onNext(k);
    }

    void applyKeyword(@NonNull String k) {
        if (Boolean.TRUE.equals(searchMode.getValue())) {
            setFilter(query().withKeyword(k));
        }
    }

    public void consumeHighlight() {
        highlightId.setValue(null);
    }

    // ===== 行操作 =====

    /** 原单据：INBOUND → M03 只读，DRUG_LOT → M06，SALE_ORDER → E07 */
    public void openSource(@NonNull FinanceRecordVO r) {
        FinanceRecordVO.SourceTarget t = r.getSourceTarget();
        if (t == null || t.getBizId() == null) {
            return;
        }
        if (FinanceConst.BIZ_INBOUND.equals(t.getBizType())) {
            // 模块09 的参数键未约定：同时带 biz_id、record_id、inbound_id（见交付说明）
            open(NavTarget.of(RouterPath.M03).put(RouterPath.EXTRA_BIZ_ID, t.getBizId())
                    .put(RouterPath.EXTRA_RECORD_ID, t.getBizId()).put(FinanceExtras.INBOUND_ID, t.getBizId())
                    .put(RouterPath.EXTRA_MODE, "VIEW"));
        } else if (FinanceConst.BIZ_DRUG_LOT.equals(t.getBizType())) {
            open(NavTarget.of(RouterPath.M06).put(RouterPath.EXTRA_BIZ_ID, t.getBizId())
                    .put(FinanceExtras.LOT_ID, t.getBizId()));
        } else if (FinanceConst.BIZ_SALE_ORDER.equals(t.getBizType())) {
            open(NavTarget.of(RouterPath.E07).put(FinanceExtras.SALE_ORDER_ID, t.getBizId()).forResult());
        }
    }

    /** 点按整项：手工记录进入 E03 只读查看；已作废打开修改记录 */
    public void onItemClick(@NonNull FinanceRecordVO r) {
        if (FinanceConst.STATUS_VOID.equals(r.getStatus())) {
            showLog(r);
        } else if (FinanceConst.SOURCE_MANUAL.equals(r.getSourceType())) {
            open(NavTarget.of(RouterPath.E03).put(FinanceExtras.RECORD_ID, r.getId())
                    .put(FinanceExtras.MODE, "VIEW"));
        } else {
            openSource(r);
        }
    }

    public void edit(@NonNull FinanceRecordVO r) {
        open(NavTarget.of(RouterPath.E03).put(FinanceExtras.RECORD_ID, r.getId()).forResult());
    }

    /** 查看修改记录（U14，FINANCE_RECORD） */
    public void showLog(@NonNull FinanceRecordVO r) {
        launch(baseData.getRecordLogs(FinanceConst.LOG_FINANCE_RECORD, r.getId()), showLogSheet::setValue);
    }

    public void requestVoid(@NonNull FinanceRecordVO r) {
        confirmVoid.setValue(r);
    }

    /** 作废：先调 E04 取 version，再调 E07 */
    public void voidRecord(long id) {
        launch(repository.getRecordDetail(id).flatMap(d -> repository.voidRecord(id,
                new VersionRequest(d.getVersion()))), vo -> {
            showMessage("已作废，不再计入汇总");
            notifyTodoChanged(FinanceConst.LOG_FINANCE_RECORD, id);
            refresh();
        });
    }

    /** 从 E03、E07 返回：刷新并显示来源页提示 */
    public void onChildResult(@Nullable String text) {
        refresh();
        if (text != null) {
            showMessage(text);
        }
    }
}
