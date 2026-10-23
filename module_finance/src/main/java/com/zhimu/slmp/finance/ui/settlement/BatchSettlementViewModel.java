package com.zhimu.slmp.finance.ui.settlement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.SettlementRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.SupplementConfirmRequest;
import com.zhimu.slmp.finance.model.vo.BatchSettlementVO;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.NavTarget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * E10 批次核算：盈亏、销售收入与总成本、单头指标、成本构成；已锁定且有补录时“去确认”重算（API-E27、E28）。
 */
public class BatchSettlementViewModel extends FinanceViewModel {

    private final SettlementRepository repository;

    private final MutableLiveData<BatchSettlementVO> settlement = new MutableLiveData<>();
    private final MutableLiveData<List<BatchSettlementVO.CostItem>> costs = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<BatchSettlementVO.Supplement>> supplements =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> confirming = new MutableLiveData<>(false);
    /** 对话框：标题、正文 */
    private final SingleLiveEvent<String[]> showConfirmDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> dismissConfirmDialog = new SingleLiveEvent<>();
    private long batchId;

    @Inject
    public BatchSettlementViewModel(@NonNull SettlementRepository repository, @NonNull FarmContext farm) {
        super(farm);
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(FinanceExtras.BATCH_ID);
        batchId = id == null ? 0 : id;
        load();
    }

    public void load() {
        if (denyStaff()) {
            return;
        }
        load(repository.getSettlement(batchId), this::apply);
    }

    public void refresh() {
        load();
    }

    @Override
    public void retry() {
        load();
    }

    void apply(@NonNull BatchSettlementVO s) {
        settlement.setValue(s);
        costs.setValue(s.getCosts());
        supplements.setValue(FinanceConst.SETTLEMENT_LOCKED.equals(s.getStatus()) ? s.getSupplements()
                : Collections.emptyList());
    }

    public LiveData<BatchSettlementVO> getSettlement() {
        return settlement;
    }

    public LiveData<List<BatchSettlementVO.CostItem>> getCosts() {
        return costs;
    }

    public LiveData<List<BatchSettlementVO.Supplement>> getSupplements() {
        return supplements;
    }

    public LiveData<Boolean> getConfirming() {
        return confirming;
    }

    public SingleLiveEvent<String[]> getShowConfirmDialog() {
        return showConfirmDialog;
    }

    public SingleLiveEvent<Boolean> getDismissConfirmDialog() {
        return dismissConfirmDialog;
    }

    public boolean isLocked() {
        BatchSettlementVO s = settlement.getValue();
        return s != null && FinanceConst.SETTLEMENT_LOCKED.equals(s.getStatus());
    }

    // ===== 文案 =====

    /** 补录横幅结论“有 1 笔补录费用待确认。”/“有 3 笔补录收支待确认。” */
    @NonNull
    public static String supplementTitle(@NonNull List<BatchSettlementVO.Supplement> list) {
        return list.size() == 1 ? "有 1 笔补录费用待确认。" : "有 " + list.size() + " 笔补录收支待确认。";
    }

    /** 横幅正文“10月17日 运输费 600 元。核算已锁定，你确认后才会重新计算。” */
    @NonNull
    public static String supplementText(@NonNull List<BatchSettlementVO.Supplement> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            BatchSettlementVO.Supplement s = list.get(i);
            sb.append(i == 0 ? "" : "；").append(FinanceFormat.monthDay(s.getBizDate())).append(' ')
                    .append(s.getTitle()).append(' ').append(FinanceFormat.yuan(s.getAmount()));
        }
        return sb.append("。核算已锁定，你确认后才会重新计算。").toString();
    }

    /** “入栏 100 头 · 死淘 2 头 · 出栏 98 头” */
    @NonNull
    public static String countLine(@NonNull BatchSettlementVO.SettlementBatch b) {
        String u = b.getCountUnitText() == null ? "头" : b.getCountUnitText();
        return "入栏 " + FinanceFormat.count(b.getEntryCount(), u) + " · 死淘 " + FinanceFormat.count(b.getDeathCount(), u)
                + " · 出栏 " + FinanceFormat.count(b.getSoldCount(), u);
    }

    /** 批次信息第一行：已结束“10月13日全部出栏”，在养“在养 96 日龄” */
    @NonNull
    public static String batchLine(@NonNull BatchSettlementVO s) {
        BatchSettlementVO.SettlementBatch b = s.getBatch();
        StringBuilder sb = new StringBuilder(b.getPenName()).append(" · ")
                .append(b.getBreedName() == null ? "" : b.getBreedName()).append(b.getCategoryName()).append(" · ");
        if (b.getEndDate() != null) {
            sb.append(FinanceFormat.monthDay(b.getEndDate())).append("全部出栏");
        } else {
            sb.append("在养 ").append(b.getDayAge()).append(" 日龄");
        }
        return sb.toString();
    }

    /** 单头指标说明“按出栏 98 头平摊；料肉比 = 饲料 25,930 千克 ÷ 净增重 9,260 千克” */
    @NonNull
    public static String perHeadNote(@NonNull BatchSettlementVO s) {
        String u = s.getBatch().getCountUnitText() == null ? "头" : s.getBatch().getCountUnitText();
        String basis = (FinanceConst.BASIS_SOLD.equals(s.getPerHeadBasis()) ? "按出栏 " : "按存栏 ")
                + FinanceFormat.count(s.getPerHeadCount(), u) + "平摊";
        if (s.getFeedKg() != null && s.getNetGainKg() != null) {
            basis += "；料肉比 = 饲料 " + FinanceFormat.kg(s.getFeedKg()) + " ÷ 净增重 " + FinanceFormat.kg(s.getNetGainKg());
        }
        return basis;
    }

    /** 单头数值：null 为“无数据” */
    @NonNull
    public static String perHeadValue(@Nullable BigDecimal v) {
        return v == null ? FinanceFormat.NO_DATA : FinanceFormat.price(v);
    }

    // ===== 操作 =====

    /** “去确认”：对话框逐笔列出补录收支与重算后的盈亏 */
    public void requestConfirm() {
        List<BatchSettlementVO.Supplement> list = supplements.getValue();
        BatchSettlementVO s = settlement.getValue();
        if (list == null || list.isEmpty() || s == null) {
            return;
        }
        String title = list.size() == 1 ? "确认补录的" + list.get(0).getTitle() + "？" : "确认 " + list.size() + " 笔补录收支？";
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            BatchSettlementVO.Supplement x = list.get(i);
            body.append(i == 0 ? "" : "、").append(FinanceFormat.monthDay(x.getBizDate())).append(' ')
                    .append(x.getTitle()).append(' ').append(FinanceFormat.yuan(x.getAmount()));
        }
        body.append(list.size() == 1 ? "将计入其他费用" : "将计入批次核算");
        BatchSettlementVO.PreviewAfter p = s.getPreviewAfter();
        if (p != null) {
            body.append("，批次盈亏变为 ").append(FinanceFormat.signed(p.getProfit())).append(FinanceFormat.NBSP)
                    .append(FinanceFormat.YUAN);
        }
        body.append("。");
        showConfirmDialog.setValue(new String[]{title, body.toString()});
    }

    /** 确认并重算（API-E28）：取全部补录 ID 与核算 version */
    public void confirmSupplements() {
        BatchSettlementVO s = settlement.getValue();
        List<BatchSettlementVO.Supplement> list = supplements.getValue();
        if (s == null || list == null || list.isEmpty() || Boolean.TRUE.equals(confirming.getValue())) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (BatchSettlementVO.Supplement x : list) {
            ids.add(x.getId());
        }
        confirming.setValue(true);
        launch(repository.confirmSupplements(batchId, new SupplementConfirmRequest(ids, s.getVersion())), r -> {
            confirming.setValue(false);
            dismissConfirmDialog.setValue(true);
            apply(r);
            showMessage("已重新计算批次核算");
        }, e -> {
            confirming.setValue(false);
            dismissConfirmDialog.setValue(true);
            handleError(e);
        });
    }

    /** 40903“补录已被处理，页面已刷新”：提示后刷新 */
    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.STATE_CHANGED || e.getCode() == ErrorCodes.VERSION_CONFLICT) {
            load();
        }
    }

    /** 顶部栏 receipt_long：E04 筛选关联该批次的收支 */
    public void openLinkedRecords() {
        BatchSettlementVO s = settlement.getValue();
        NavTarget t = NavTarget.of(RouterPath.E04).put(FinanceExtras.BATCH_ID, batchId);
        if (s != null) {
            t.put(FinanceExtras.BATCH_NO, s.getBatch().getBatchNo())
                    .put(FinanceExtras.MONTH, FinanceFormat.monthOf(s.getBatch().getEndDate() == null ? null
                            : s.getBatch().getEndDate()));
        }
        open(t);
    }

    /** “其他费用”行：E04 按该批次筛选手工支出 */
    public void openOtherCosts() {
        BatchSettlementVO s = settlement.getValue();
        NavTarget t = NavTarget.of(RouterPath.E04).put(FinanceExtras.BATCH_ID, batchId)
                .put(FinanceExtras.RECORD_FILTER_TYPE, FinanceConst.TYPE_MANUAL);
        if (s != null) {
            t.put(FinanceExtras.BATCH_NO, s.getBatch().getBatchNo())
                    .put(FinanceExtras.MONTH, FinanceFormat.monthOf(s.getBatch().getEndDate()));
        }
        open(t);
    }

    public void openSaleOrder(long id) {
        open(NavTarget.of(RouterPath.E07).put(FinanceExtras.SALE_ORDER_ID, id));
    }
}
