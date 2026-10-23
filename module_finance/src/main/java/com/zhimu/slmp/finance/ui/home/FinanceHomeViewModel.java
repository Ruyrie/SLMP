package com.zhimu.slmp.finance.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.FinanceRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.BusinessOverviewVO;
import com.zhimu.slmp.finance.model.vo.ReceivableSummary;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.util.FinanceErrors;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.NavTarget;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/**
 * E01 经营页（底部导航“经营”，所有者、场长）：本月结余、待收款合计、出栏价、经营工具与需要处理（API-E01）。
 */
public class FinanceHomeViewModel extends FinanceViewModel {

    public static final int CARD_BALANCE = 1;
    public static final int CARD_RECEIVABLE = 2;
    public static final int CARD_PRICE = 3;
    public static final int TOOL_FINANCE = 4;
    public static final int TOOL_SALE = 5;
    public static final int TOOL_MARKET = 6;
    public static final int TOOL_REPORT = 7;
    public static final int TODO_ALL = 8;
    /** T03 经营类待办的类别 */
    static final String TODO_CATEGORY_BUSINESS = "BUSINESS";
    static final int MAX_TODOS = 3;

    private final FinanceRepository repository;

    private final MutableLiveData<BusinessOverviewVO> overview = new MutableLiveData<>();
    private final MutableLiveData<String> balanceMomText = new MutableLiveData<>("");
    private final MutableLiveData<String> salesSubtitle = new MutableLiveData<>("");
    private final MutableLiveData<String> recordSubtitle = new MutableLiveData<>("");
    private final MutableLiveData<String> receivableNote = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> receivableOverdue = new MutableLiveData<>(false);
    private final MutableLiveData<String> dateNote = new MutableLiveData<>("");
    private final MutableLiveData<List<BusinessOverviewVO.TodoItem>> todoItems =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> showAllTodos = new MutableLiveData<>(false);
    private final LiveData<Long> currentFarm;
    @Nullable
    private Long loadedFarmId;

    @Inject
    public FinanceHomeViewModel(@NonNull FinanceRepository repository, @NonNull FarmContext farm) {
        super(farm);
        this.repository = repository;
        currentFarm = Transformations.distinctUntilChanged(Transformations.map(farm.getContext(),
                c -> c == null ? 0L : c.getFarmId()));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            disposables.add(todo.todoChanges().observeOn(AndroidSchedulers.mainThread())
                    .subscribe(c -> refresh(), e -> {
                    }));
        }
    }

    public LiveData<BusinessOverviewVO> getOverview() {
        return overview;
    }

    public LiveData<String> getBalanceMomText() {
        return balanceMomText;
    }

    public LiveData<String> getSalesSubtitle() {
        return salesSubtitle;
    }

    public LiveData<String> getRecordSubtitle() {
        return recordSubtitle;
    }

    public LiveData<String> getReceivableNote() {
        return receivableNote;
    }

    public LiveData<Boolean> getReceivableOverdue() {
        return receivableOverdue;
    }

    public LiveData<String> getDateNote() {
        return dateNote;
    }

    public LiveData<List<BusinessOverviewVO.TodoItem>> getTodoItems() {
        return todoItems;
    }

    public LiveData<Boolean> getShowAllTodos() {
        return showAllTodos;
    }

    public LiveData<Long> getCurrentFarm() {
        return currentFarm;
    }

    public void load() {
        if (denyStaff()) {
            return;
        }
        loadedFarmId = farm.currentFarmId();
        load(repository.getOverview(), this::apply);
    }

    /** 下拉刷新与待办变化：已有内容时只刷新，失败保留原数据并提示 */
    public void refresh() {
        load();
    }

    @Override
    protected void handleError(@NonNull Throwable t) {
        if (overview.getValue() != null && FinanceErrors.isNetworkError(t)) {
            showMessage(FinanceErrors.MSG_REFRESH_FAILED);
            return;
        }
        super.handleError(t);
    }

    @Override
    public void retry() {
        load();
    }

    /** 切换养殖场：清空后重新加载（骨架屏） */
    public void onFarmChanged(@Nullable Long farmId) {
        if (farmId == null || farmId == 0 || Objects.equals(farmId, loadedFarmId)) {
            return;
        }
        overview.setValue(null);
        todoItems.setValue(Collections.emptyList());
        resetContentLoaded();
        load();
    }

    void apply(@NonNull BusinessOverviewVO o) {
        dateNote.setValue(DateFormatter.formatDate(o.getToday() == null ? DateFormatter.todayString() : o.getToday())
                + " · 本月数据截至今天");
        BusinessOverviewVO.MonthBalance b = o.getMonthBalance();
        balanceMomText.setValue(b == null ? "" : FinanceFormat.mom("较上月同期", b.getBalanceMomRate(),
                b.isComparable()));
        int sales = o.getMonthSales() == null ? 0 : o.getMonthSales().getOrderCount();
        salesSubtitle.setValue(sales > 0 ? "本月出栏 " + sales + " 张" : "本月暂无出栏");
        recordSubtitle.setValue("本月 " + (o.getMonthRecords() == null ? 0 : o.getMonthRecords().getTotal()) + " 笔");
        ReceivableSummary r = o.getReceivable();
        if (r == null || r.getOrderCount() == 0) {
            receivableNote.setValue("没有待收款");
            receivableOverdue.setValue(false);
        } else if (r.getOverdueCount() > 0) {
            receivableNote.setValue(r.getOrderCount() + " 张 · " + r.getOverdueCount() + " 张已逾期");
            receivableOverdue.setValue(true);
        } else {
            receivableNote.setValue(r.getOrderCount() + " 张 · 最近 " + FinanceFormat.monthDay(r.getNearestDueDate())
                    + "到期");
            receivableOverdue.setValue(false);
        }
        List<BusinessOverviewVO.TodoItem> items = o.getTodos() == null ? Collections.emptyList()
                : o.getTodos().getItems();
        todoItems.setValue(items.size() > MAX_TODOS ? items.subList(0, MAX_TODOS) : items);
        showAllTodos.setValue(o.getTodos() != null && o.getTodos().getTotal() > MAX_TODOS);
        // 派生文字先写好，页面观察 overview 时读取
        overview.setValue(o);
    }

    public void onCardClick(int cardId) {
        switch (cardId) {
            case CARD_BALANCE:
            case TOOL_FINANCE:
                open(NavTarget.of(RouterPath.E02));
                break;
            case CARD_RECEIVABLE:
                open(NavTarget.of(RouterPath.E09));
                break;
            case CARD_PRICE:
            case TOOL_MARKET:
                open(NavTarget.of(RouterPath.E11));
                break;
            case TOOL_SALE:
                open(NavTarget.of(RouterPath.E14));
                break;
            case TOOL_REPORT:
                open(NavTarget.of(RouterPath.E13).put(FinanceExtras.REPORT_TYPE, FinanceConst.REPORT_TO_DATE));
                break;
            case TODO_ALL:
                open(NavTarget.of(RouterPath.T03).put(RouterPath.EXTRA_CATEGORY, TODO_CATEGORY_BUSINESS));
                break;
            default:
                break;
        }
    }

    /** 待收款 → E07；休药期到期等批次待办 → E06 带入批次 */
    public void onTodoClick(@NonNull BusinessOverviewVO.TodoItem item) {
        if (item.getBizId() == null) {
            return;
        }
        if (FinanceConst.BIZ_BATCH.equals(item.getBizType())) {
            open(NavTarget.of(RouterPath.E06).put(FinanceExtras.BATCH_ID, item.getBizId())
                    .put(FinanceExtras.TODO_ID, item.getTodoId()));
        } else {
            open(NavTarget.of(RouterPath.E07).put(FinanceExtras.SALE_ORDER_ID, item.getBizId())
                    .put(FinanceExtras.TODO_ID, item.getTodoId()));
        }
    }
}
