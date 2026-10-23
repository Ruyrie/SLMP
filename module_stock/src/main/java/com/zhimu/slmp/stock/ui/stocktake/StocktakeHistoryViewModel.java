package com.zhimu.slmp.stock.ui.stocktake;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.vo.StocktakeVO;
import com.zhimu.slmp.stock.ui.common.GroupedPagedSource;
import com.zhimu.slmp.stock.ui.common.ListRow;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockTexts;

import javax.inject.Inject;

/**
 * 盘点记录（技术准备文档 2.4.6，M04 history，原型未绘制）：API-M16 按盘点日期倒序，显示盘点人与盘盈、盘亏汇总；点记录进入单据只读页。
 */
public class StocktakeHistoryViewModel extends BaseListViewModel<String, ListRow<StocktakeVO>> {

    private final StockRepository repository;
    private final SingleLiveEvent<Long> openRecord = new SingleLiveEvent<>();

    @Inject
    public StocktakeHistoryViewModel(@NonNull StockRepository repository) {
        super("ALL");
        this.repository = repository;
    }

    @NonNull
    @Override
    protected PagingSource<Integer, ListRow<StocktakeVO>> createPagingSource(@Nullable String filter) {
        return new GroupedPagedSource<>(repository::pageStocktakes, null, s -> String.valueOf(s.stocktakeId));
    }

    /** “10月1日 周三 盘点” */
    @NonNull
    public static String title(@NonNull StocktakeVO s) {
        return DateFormatter.formatDate(s.stocktakeDate) + " 盘点";
    }

    /** “王建国 · 盘盈 1 条 25.20 元 · 盘亏 1 条 38.40 元” */
    @NonNull
    public static String subtitle(@NonNull StocktakeVO s) {
        return StockTexts.join(StockTexts.DOT, s.operator == null ? null : s.operator.getName(),
                "盘盈 " + s.gainCount + " 条 " + StockCalc.money(s.gainAmount) + " 元",
                "盘亏 " + s.lossCount + " 条 " + StockCalc.money(s.lossAmount) + " 元");
    }

    public void onItemClick(@NonNull StocktakeVO s) {
        openRecord.setValue(s.stocktakeId);
    }

    public SingleLiveEvent<Long> getOpenRecord() {
        return openRecord;
    }
}
