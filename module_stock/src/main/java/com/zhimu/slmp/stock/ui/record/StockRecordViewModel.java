package com.zhimu.slmp.stock.ui.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;
import com.zhimu.slmp.stock.model.vo.StockLossVO;
import com.zhimu.slmp.stock.model.vo.StocktakeVO;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * 单据只读页（技术准备文档 2.4.6，原型未绘制，按只读分组列表补充）：record_kind 为 LOSS（报损、报废，API-M19）或
 * STOCKTAKE（盘点，API-M17）。菜单由 actions 决定：REQUEST_ONLY“提交修改申请 · 需审核”（U01），PENDING“查看修改申请”（U02），
 * 始终有“查看修改记录”（API-U14）。
 */
public class StockRecordViewModel extends BaseViewModel {

    private final StockRepository repository;

    private final MutableLiveData<StockLossVO> loss = new MutableLiveData<>();
    private final MutableLiveData<StocktakeVO> stocktake = new MutableLiveData<>();
    private final MutableLiveData<StockActionsVO> actions = new MutableLiveData<>();
    private final MutableLiveData<String> title = new MutableLiveData<>("");
    private final MutableLiveData<List<String[]>> rows = new MutableLiveData<>(new ArrayList<>());

    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeLog = new SingleLiveEvent<>();

    @Nullable
    private String recordKind;
    private long recordId;

    @Inject
    public StockRecordViewModel(@NonNull StockRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        recordKind = handle.get(RouterPath.EXTRA_RECORD_KIND);
        Long id = StockExtras.readId(handle.get(RouterPath.EXTRA_RECORD_ID));
        recordId = id == null ? 0 : id;
        load(recordKind, recordId);
    }

    public void load(@Nullable String kind, long id) {
        recordKind = kind;
        recordId = id;
        if (StockExtras.KIND_STOCKTAKE.equals(kind)) {
            title.setValue("盘点记录");
            load(repository.getStocktake(id), this::applyStocktake);
        } else {
            title.setValue("报损记录");
            load(repository.getLoss(id), this::applyLoss);
        }
    }

    @Override
    public void retry() {
        load(recordKind, recordId);
    }

    private void applyLoss(@NonNull StockLossVO l) {
        loss.setValue(l);
        actions.setValue(l.actions);
        boolean scrap = "DRUG_SCRAP".equals(l.lossType);
        title.setValue(scrap ? "报废记录" : "报损记录");
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{scrap ? "药品" : "饲料", StockTexts.nz(l.materialName)});
        r.add(new String[]{"批号", l.lotNo});
        r.add(new String[]{scrap ? "报废数量" : "报损数量", StockQtyFormatter.format(l.qty)});
        r.add(new String[]{scrap ? "报废原因" : "报损原因", l.reasonText});
        r.add(new String[]{"说明", l.remark});
        r.add(new String[]{scrap ? "报废日期" : "报损日期", DateFormatter.formatDate(l.lossDate)});
        r.add(new String[]{scrap ? "报废损失" : "损失金额", l.amount == null ? null : StockCalc.money(l.amount) + " 元"});
        r.add(new String[]{"登记人", creator(l.createdBy == null ? null : l.createdBy.getName(), l.createdAt)});
        rows.setValue(r);
    }

    private void applyStocktake(@NonNull StocktakeVO s) {
        stocktake.setValue(s);
        actions.setValue(s.actions);
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"盘点日期", DateFormatter.formatDate(s.stocktakeDate)});
        r.add(new String[]{"盘点人", s.operator == null ? null : s.operator.getName()});
        r.add(new String[]{"盘点饲料", s.itemCount + " 种"});
        r.add(new String[]{"盘盈", s.gainCount + " 条" + StockTexts.DOT + StockCalc.money(s.gainAmount) + " 元"});
        r.add(new String[]{"盘亏", s.lossCount + " 条" + StockTexts.DOT + StockCalc.money(s.lossAmount) + " 元"});
        r.add(new String[]{"登记人", creator(s.operator == null ? null : s.operator.getName(), s.createdAt)});
        rows.setValue(r);
    }

    /** 盘点明细行：标题、副文字“账面 500 千克 · 实盘 488 千克”、右侧“盘亏 12 千克” */
    @NonNull
    public static String[] itemTexts(@NonNull StocktakeVO.Item i) {
        String unit = i.unitText == null ? "千克" : i.unitText;
        String sub = "账面 " + StockQtyFormatter.qty(i.bookQty, unit) + StockTexts.DOT + "实盘 "
                + StockQtyFormatter.qty(i.actualQty, unit);
        String result;
        if (StockEnums.RESULT_MATCH.equals(i.result) || (i.diffQty != null && i.diffQty.signum() == 0)) {
            result = "账实相符";
        } else {
            boolean gain = StockEnums.RESULT_GAIN.equals(i.result);
            result = (gain ? "盘盈 " : "盘亏 ") + StockQtyFormatter.qty(i.diffQty == null ? null : i.diffQty.abs(), unit);
            if (i.diffAmount != null) {
                sub = sub + "\n按均价折合 " + StockCalc.money(i.diffAmount.abs()) + " 元";
            }
        }
        return new String[]{StockTexts.nz(i.feedName), sub, result};
    }

    @Nullable
    private static String creator(@Nullable String name, @Nullable String at) {
        String s = StockTexts.join(StockTexts.DOT, name, at == null ? null : DateFormatter.formatDateTime(at));
        return s.isEmpty() ? null : s;
    }

    // ===================== 菜单 =====================

    public void onRequestChange() {
        if (recordId > 0) {
            openChangeRequest.setValue(recordId);
        }
    }

    public void onViewRequest() {
        StockActionsVO a = actions.getValue();
        openRequest.setValue(a == null ? null : a.pendingRequestId);
    }

    public void onChangeLog() {
        openChangeLog.setValue(recordId);
    }

    /** U01 / U14 的 biz_type */
    @NonNull
    public String bizType() {
        return StockExtras.KIND_STOCKTAKE.equals(recordKind) ? StockEnums.BIZ_STOCKTAKE : StockEnums.BIZ_LOSS;
    }

    public LiveData<StockLossVO> getLoss() {
        return loss;
    }

    public LiveData<StocktakeVO> getStocktake() {
        return stocktake;
    }

    public LiveData<StockActionsVO> getActions() {
        return actions;
    }

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<List<String[]>> getRows() {
        return rows;
    }

    public SingleLiveEvent<Long> getOpenChangeRequest() {
        return openChangeRequest;
    }

    public SingleLiveEvent<Long> getOpenRequest() {
        return openRequest;
    }

    public SingleLiveEvent<Long> getOpenChangeLog() {
        return openChangeLog;
    }
}
