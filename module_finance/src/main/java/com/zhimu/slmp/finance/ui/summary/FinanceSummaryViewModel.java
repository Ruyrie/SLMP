package com.zhimu.slmp.finance.ui.summary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.FinanceRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.CategoryShareVO;
import com.zhimu.slmp.finance.model.vo.FarmFinanceVO;
import com.zhimu.slmp.finance.model.vo.FinanceSummaryVO;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.NavTarget;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/**
 * E02 收支记账：按月查看结余、收入、支出及环比，分类占比，当前养殖场 / 全部养殖场合计两个视角（API-E02）。
 */
public class FinanceSummaryViewModel extends FinanceViewModel {

    private final FinanceRepository repository;

    private final MutableLiveData<String> month = new MutableLiveData<>(FinanceFormat.currentMonth());
    private final MutableLiveData<String> monthLabel = new MutableLiveData<>("");
    private final MutableLiveData<String> periodNote = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> scopeAll = new MutableLiveData<>(false);
    private final MutableLiveData<FinanceSummaryVO> summary = new MutableLiveData<>();
    private final MutableLiveData<String> shareDirection = new MutableLiveData<>(FinanceConst.EXPENSE);
    private final MutableLiveData<List<CategoryShareVO>> shares = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> selectedShareIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<Boolean> showScopeSwitch = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canNextMonth = new MutableLiveData<>(false);
    private final MutableLiveData<String> balanceNote = new MutableLiveData<>("");
    private final MutableLiveData<String> allFarmsNotice = new MutableLiveData<>("");
    private final MutableLiveData<String> recordsSubtitle = new MutableLiveData<>("");

    @Inject
    public FinanceSummaryViewModel(@NonNull FinanceRepository repository, @NonNull FarmContext farm) {
        super(farm);
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        String m = handle.get(RouterPath.EXTRA_MONTH);
        if (m != null && m.length() == 7) {
            month.setValue(m);
        }
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            disposables.add(todo.todoChanges().observeOn(AndroidSchedulers.mainThread())
                    .subscribe(c -> load(), e -> {
                    }));
        }
        load();
    }

    public LiveData<String> getMonth() {
        return month;
    }

    public LiveData<String> getMonthLabel() {
        return monthLabel;
    }

    public LiveData<String> getPeriodNote() {
        return periodNote;
    }

    public LiveData<Boolean> getScopeAll() {
        return scopeAll;
    }

    public LiveData<FinanceSummaryVO> getSummary() {
        return summary;
    }

    public LiveData<String> getShareDirection() {
        return shareDirection;
    }

    public LiveData<List<CategoryShareVO>> getShares() {
        return shares;
    }

    public LiveData<Integer> getSelectedShareIndex() {
        return selectedShareIndex;
    }

    public LiveData<Boolean> getShowScopeSwitch() {
        return showScopeSwitch;
    }

    public LiveData<Boolean> getCanNextMonth() {
        return canNextMonth;
    }

    public LiveData<String> getBalanceNote() {
        return balanceNote;
    }

    public LiveData<String> getAllFarmsNotice() {
        return allFarmsNotice;
    }

    public LiveData<String> getRecordsSubtitle() {
        return recordsSubtitle;
    }

    public void load() {
        if (denyStaff()) {
            return;
        }
        String m = month.getValue();
        monthLabel.setValue(FinanceFormat.monthLabel(m));
        canNextMonth.setValue(FinanceFormat.isBeforeCurrentMonth(m));
        load(repository.getMonthlySummary(m, Boolean.TRUE.equals(scopeAll.getValue()) ? FinanceConst.SCOPE_ALL
                : null), this::apply);
    }

    @Override
    public void retry() {
        load();
    }

    void apply(@NonNull FinanceSummaryVO s) {
        showScopeSwitch.setValue(s.getManageableFarmCount() >= 2);
        LocalDate end = DateFormatter.parseDate(s.getPeriodEnd());
        LocalDate start = DateFormatter.parseDate(s.getPeriodStart());
        if (s.isCurrentMonth() && end != null) {
            periodNote.setValue("截至 " + DateFormatter.monthDay(end) + " · 已作废的不计入");
        } else if (start != null && end != null) {
            periodNote.setValue(DateFormatter.monthDay(start) + "—" + end.getDayOfMonth() + "日 · 已作废的不计入");
        } else {
            periodNote.setValue("已作废的不计入");
        }
        String prefix = s.isCurrentMonth() ? "上月同期 " : "上月 ";
        String mom = FinanceFormat.mom("较上月同期", s.getBalanceMomRate(), s.isBalanceComparable());
        if (FinanceFormat.NOT_COMPARABLE.equals(mom)) {
            mom = "较上月同期无法比较";
        }
        balanceNote.setValue(mom + " · " + prefix + FinanceFormat.yuan(s.getPrevBalanceAmount()));
        boolean all = Boolean.TRUE.equals(scopeAll.getValue());
        List<String> staff = s.getStaffFarmNames();
        allFarmsNotice.setValue(staff.isEmpty() ? "只合计你担任所有者或场长的养殖场。"
                : "只合计你担任所有者或场长的养殖场。" + String.join("、", staff) + "你是员工，不计入。");
        recordsSubtitle.setValue(all ? "每条记录带养殖场标签"
                : (s.isCurrentMonth() ? "本月 " : FinanceFormat.monthLabel(month.getValue()) + " ")
                + s.getRecordCount() + " 笔，其中自动生成 " + s.getAutoCount() + " 笔");
        summary.setValue(s);
        applyShares();
    }

    private void applyShares() {
        FinanceSummaryVO s = summary.getValue();
        List<CategoryShareVO> list = s == null ? Collections.emptyList()
                : FinanceConst.INCOME.equals(shareDirection.getValue()) ? s.getIncomeShares() : s.getExpenseShares();
        shares.setValue(list);
        selectedShareIndex.setValue(-1);
    }

    public void prevMonth() {
        month.setValue(FinanceFormat.shiftMonth(month.getValue(), -1));
        load();
    }

    public void nextMonth() {
        if (!Boolean.TRUE.equals(canNextMonth.getValue())) {
            return;
        }
        month.setValue(FinanceFormat.shiftMonth(month.getValue(), 1));
        load();
    }

    public void pickMonth(@NonNull String ym) {
        if (ym.equals(month.getValue()) || ym.compareTo(FinanceFormat.currentMonth()) > 0) {
            return;
        }
        month.setValue(ym);
        load();
    }

    public void switchScope(boolean all) {
        if (all == Boolean.TRUE.equals(scopeAll.getValue())) {
            return;
        }
        scopeAll.setValue(all);
        load();
    }

    public void switchShareDirection(@NonNull String direction) {
        if (direction.equals(shareDirection.getValue())) {
            return;
        }
        shareDirection.setValue(direction);
        applyShares();
    }

    /** 点扇区：高亮图例行，再次点按取消 */
    public void selectShare(int index) {
        Integer cur = selectedShareIndex.getValue();
        selectedShareIndex.setValue(cur != null && cur == index ? -1 : index);
    }

    /** 点图例行：进入 E04，带入当前月份和该分类 */
    public void openShare(int index) {
        List<CategoryShareVO> list = shares.getValue();
        if (list == null || index < 0 || index >= list.size()) {
            return;
        }
        CategoryShareVO c = list.get(index);
        open(NavTarget.of(RouterPath.E04).put(RouterPath.EXTRA_MONTH, month.getValue())
                .put(RouterPath.EXTRA_CATEGORY_IDS, String.valueOf(c.getCategoryId()))
                .put(RouterPath.EXTRA_CATEGORY_NAME, c.getName()).forResult());
    }

    public void openRecords() {
        NavTarget t = NavTarget.of(RouterPath.E04).put(RouterPath.EXTRA_MONTH, month.getValue()).forResult();
        if (Boolean.TRUE.equals(scopeAll.getValue())) {
            t.put(RouterPath.EXTRA_SCOPE, FinanceConst.SCOPE_ALL);
        }
        open(t);
    }

    public void openCategories() {
        open(NavTarget.of(RouterPath.E05));
    }

    public void openRecordEdit() {
        open(NavTarget.of(RouterPath.E03).put(RouterPath.EXTRA_RECORD_TYPE, FinanceConst.EXPENSE).forResult());
    }

    /** 合计视角点养殖场卡：切换当前养殖场，视角回到“当前养殖场” */
    public void switchToFarm(@NonNull FarmFinanceVO f) {
        IFarmContextService svc = ServiceHub.get(IFarmContextService.class);
        if (f.getFarmId() == farm.farmIdOrZero() || svc == null) {
            scopeAll.setValue(false);
            load();
            return;
        }
        launch(svc.switchTo(f.getFarmId()), ctx -> {
            scopeAll.setValue(false);
            resetContentLoaded();
            load();
            showMessage(UiMessage.of("已切换到" + f.getFarmName()));
        });
    }

    /** 记一笔、修改、作废后返回：刷新汇总并显示来源页提示 */
    public void onChildResult(@Nullable String text) {
        load();
        if (text != null) {
            showMessage(text);
        }
    }
}
