package com.zhimu.slmp.stock.ui.drugstock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.model.vo.DrugLotItemVO;
import com.zhimu.slmp.stock.ui.common.RowPagingAdapter;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.function.Consumer;

/**
 * M05 批号列表项：物资图标底按状态着色；“批号 2025047”与第 2、3 行；右侧数量“30 瓶（300 头份）”与状态标签
 * （已停用 / 已过期 / 临期 n 天 / 优先出库）。列表不显示单价和金额。
 */
public class LotPagingAdapter extends RowPagingAdapter<DrugLotItemVO> {

    public LotPagingAdapter(@Nullable Consumer<DrugLotItemVO> onClick) {
        super(onClick);
    }

    @Override
    protected void bindItem(@NonNull StockItemRowBinding b, @NonNull DrugLotItemVO lot) {
        bindLot(b, lot);
    }

    public static void bindLot(@NonNull StockItemRowBinding b, @NonNull DrugLotItemVO lot) {
        StockUi.bindRow(b, StockTexts.categoryIcon(lot.category), StockTexts.lotTone(lot.lotStatus),
                lot.drugName, StockTexts.lotSubtitle(lot), StockQtyFormatter.format(lot.stock),
                StockTexts.lotTag(lot), StockTexts.lotTagType(lot.lotStatus), StockTexts.lotTagIcon(lot.lotStatus));
    }
}
