package com.zhimu.slmp.stock.ui.flow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;
import com.zhimu.slmp.stock.ui.common.RowPagingAdapter;
import com.zhimu.slmp.stock.ui.feedstock.FeedStockActivity;
import com.zhimu.slmp.stock.util.FlowRouter;

import java.util.function.Consumer;

/** 出入库流水分页列表（流水页、M06 页内流水）；lotStyle 为批号详情页内的写法。 */
public class FlowPagingAdapter extends RowPagingAdapter<StockFlowItemVO> {

    private final boolean lotStyle;

    public FlowPagingAdapter(boolean lotStyle, @Nullable Consumer<StockFlowItemVO> onClick) {
        super(onClick);
        this.lotStyle = lotStyle;
    }

    @Override
    protected void bindItem(@NonNull StockItemRowBinding b, @NonNull StockFlowItemVO item) {
        FeedStockActivity.bindFlow(b, item, lotStyle);
    }

    /** 没有跳转目标的流水（如修改申请、缺批次的饲喂）不可点 */
    @Override
    protected boolean isItemClickable(@NonNull StockFlowItemVO item) {
        return FlowRouter.route(item) != null;
    }
}
