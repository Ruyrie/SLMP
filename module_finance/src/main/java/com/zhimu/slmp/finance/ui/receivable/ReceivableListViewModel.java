package com.zhimu.slmp.finance.ui.receivable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.SaleReceiptRequest;
import com.zhimu.slmp.finance.model.vo.ReceivableSummary;
import com.zhimu.slmp.finance.model.vo.ReceivableVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;
import com.zhimu.slmp.finance.ui.common.FinanceListViewModel;
import com.zhimu.slmp.finance.ui.common.ReceiptHost;
import com.zhimu.slmp.finance.util.FinanceErrors;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.NavTarget;
import com.zhimu.slmp.finance.util.ReceiptFormDelegate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

/**
 * E09 待收款：应收合计、全部 / 已逾期 / 7 天内到期筛选的未收齐出栏单，卡片内直接登记收款（API-E18、E22）。
 */
public class ReceivableListViewModel extends FinanceListViewModel<String, SaleOrderItemVO> implements ReceiptHost {

    private final SaleOrderRepository repository;
    private final BaseDataRepository baseData;
    private final ReceiptFormDelegate receipt = new ReceiptFormDelegate();

    private final MutableLiveData<String> dueFilter = new MutableLiveData<>(FinanceConst.DUE_FILTER_ALL);
    private final MutableLiveData<Map<String, Integer>> tabCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<ReceivableSummary> summary = new MutableLiveData<>();
    private final MutableLiveData<String> remindRuleText = new MutableLiveData<>("");

    @Inject
    public ReceivableListViewModel(@NonNull SaleOrderRepository repository, @NonNull BaseDataRepository baseData,
                                   @NonNull FarmContext farm) {
        super(farm, FinanceConst.DUE_FILTER_ALL);
        this.repository = repository;
        this.baseData = baseData;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        receipt.attach(handle);
        if (denyStaff()) {
            return;
        }
        launch(baseData.getDicts(FinanceConst.DICT_PAYMENT_METHOD, null), receipt::setPaymentMethods, e -> {
        });
    }

    @NonNull
    @Override
    protected PagingSource<Integer, SaleOrderItemVO> createPagingSource(@Nullable String filter) {
        return repository.pagingReceivables(filter == null ? FinanceConst.DUE_FILTER_ALL : filter, this::onFirstPage);
    }

    /** 第 1 页（io 线程）：Chip 数量、合计卡、提醒规则 */
    void onFirstPage(@NonNull ReceivableVO p) {
        tabCounts.postValue(p.getTabCounts() == null ? Collections.emptyMap() : p.getTabCounts());
        summary.postValue(p.getSummary());
        remindRuleText.postValue(remindText(p));
    }

    /** 提醒横幅：场长查看时“提醒你、所有者和其他场长” */
    @NonNull
    static String remindText(@NonNull ReceivableVO p) {
        if (p.getRemindRuleText() != null && !p.getRemindRuleText().isEmpty()) {
            return p.getRemindRuleText();
        }
        String who = FinanceConst.REMIND_YOU_OWNER_OTHER.equals(p.getReminderReceivers()) ? "你、所有者和其他场长"
                : "你和场长";
        return "约定收款日前 1 天提醒" + who + "，逾期后每 3 天提醒一次。没有填写约定收款日期的，按出栏后第 7 天计算。";
    }

    /** 合计卡第三行 */
    @NonNull
    public static String summaryNote(@Nullable ReceivableSummary s) {
        if (s == null || s.getOrderCount() == 0) {
            return "没有未收齐的出栏单";
        }
        if (s.getOverdueCount() > 0) {
            return s.getOrderCount() + " 张出栏单未收齐，其中 " + s.getOverdueCount() + " 张已逾期，未收 "
                    + FinanceFormat.yuan(s.getOverdueUnpaidAmount());
        }
        return s.getOrderCount() + " 张出栏单未收齐，最近一张 " + FinanceFormat.monthDay(s.getNearestDueDate()) + "到期";
    }

    public LiveData<String> getDueFilter() {
        return dueFilter;
    }

    public LiveData<Map<String, Integer>> getTabCounts() {
        return tabCounts;
    }

    public LiveData<ReceivableSummary> getSummary() {
        return summary;
    }

    public LiveData<String> getRemindRuleText() {
        return remindRuleText;
    }

    @NonNull
    @Override
    public ReceiptFormDelegate getReceipt() {
        return receipt;
    }

    public void selectFilter(@NonNull String f) {
        dueFilter.setValue(f);
        setFilter(f);
    }

    public void openDetail(long id) {
        open(NavTarget.of(RouterPath.E07).put(RouterPath.EXTRA_SALE_ORDER_ID, id).forResult());
    }

    public void openAllOrders() {
        open(NavTarget.of(RouterPath.E14));
    }

    public void openReceipt(@NonNull SaleOrderItemVO item) {
        receipt.open(ReceiptFormDelegate.Target.of(item), null);
        launch(repository.getPrefill(null, null), p -> receipt.setLastPaymentMethod(p.getLastPaymentMethod()),
                e -> {
                });
    }

    @Override
    public void confirmReceipt() {
        ReceiptFormDelegate.Target t = receipt.getTarget().getValue();
        if (t == null || Boolean.TRUE.equals(receipt.getSubmitting().getValue())) {
            return;
        }
        SaleReceiptRequest req = receipt.validate();
        if (req == null) {
            return;
        }
        String key = receipt.idem().keyFor(req);
        receipt.setSubmitting(true);
        launch(repository.addReceipt(t.saleOrderId, req, key), r -> onReceived(t, req.getAmount(), r),
                e -> onReceiptError(e, key, t, req.getAmount()));
    }

    private void onReceived(@NonNull ReceiptFormDelegate.Target t, @NonNull BigDecimal amount,
                            @Nullable SaleOrderDetailVO r) {
        receipt.close();
        notifyTodoChanged(FinanceConst.BIZ_SALE_ORDER, t.saleOrderId);
        showMessage(ReceiptFormDelegate.successText(amount, r));
        refresh();
    }

    private void onReceiptError(@NonNull ApiException e, @NonNull String key, @NonNull ReceiptFormDelegate.Target t,
                                @NonNull BigDecimal amount) {
        if (FinanceErrors.isTimeout(e)) {
            launch(repository.getSubmission(key), s -> {
                if (s.isSaved()) {
                    onReceived(t, amount, null);
                } else {
                    receipt.setSubmitting(false);
                    showMessage(FinanceErrors.MSG_TIMEOUT);
                }
            }, e2 -> {
                receipt.setSubmitting(false);
                showMessage(FinanceErrors.MSG_TIMEOUT);
            });
            return;
        }
        receipt.setSubmitting(false);
        if (e.isNetwork()) {
            showMessage(e.keptMessage(FinanceErrors.MSG_NETWORK_KEPT));
            return;
        }
        receipt.idem().clear();
        if (e.getCode() == ErrorCodes.STATE_CHANGED) {
            receipt.close();
            showMessage(e.getMessage());
            refresh();
            return;
        }
        if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
            receipt.setErrors(e.getFieldErrors());
            return;
        }
        if (ErrorCodes.isNoPermission(e.getCode())) {
            receipt.close();
        }
        handleError(e);
    }

    /** 从 E07 返回：刷新列表 */
    public void onChildResult() {
        refresh();
    }
}
