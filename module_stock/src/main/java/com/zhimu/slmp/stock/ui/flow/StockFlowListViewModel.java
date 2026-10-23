package com.zhimu.slmp.stock.ui.flow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;
import com.zhimu.slmp.stock.ui.common.GroupedPagedSource;
import com.zhimu.slmp.stock.ui.common.ListRow;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * 出入库流水（技术准备文档 2.4.2，M01 的下级列表页）：material_type FEED 用 API-M13（feed_id 可选），DRUG 用 API-M28（drug_id 可选）；
 * Chip 切换 flow_group 时重建分页；列表按 biz_date 分组“10月19日 周一 · 今天”。
 */
public class StockFlowListViewModel extends BaseListViewModel<String, ListRow<StockFlowItemVO>> {

    /** 饲料流水 Chip：全部 / 入库 / 饲喂领用 / 其他出库 / 报损 / 盘盈盘亏 */
    public static final List<String[]> FEED_GROUPS = Arrays.asList(
            new String[]{StockEnums.GROUP_ALL, "全部"}, new String[]{StockEnums.GROUP_INBOUND, "入库"},
            new String[]{StockEnums.GROUP_FEED_USE, "饲喂领用"}, new String[]{StockEnums.GROUP_OTHER_OUT, "其他出库"},
            new String[]{StockEnums.GROUP_LOSS, "报损"}, new String[]{StockEnums.GROUP_STOCKTAKE, "盘盈盘亏"});
    /** 药品流水 Chip：全部 / 入库 / 领用 / 退货 / 报废 */
    public static final List<String[]> DRUG_GROUPS = Arrays.asList(
            new String[]{StockEnums.GROUP_ALL, "全部"}, new String[]{StockEnums.GROUP_INBOUND, "入库"},
            new String[]{StockEnums.GROUP_USE, "领用"}, new String[]{StockEnums.GROUP_RETURN, "退货"},
            new String[]{StockEnums.GROUP_SCRAP, "报废"});

    private final StockRepository repository;
    private final SingleLiveEvent<StockFlowItemVO> openRecord = new SingleLiveEvent<>();
    @NonNull
    private String materialType = StockEnums.MATERIAL_FEED;
    @Nullable
    private Long filterId;

    @Inject
    public StockFlowListViewModel(@NonNull StockRepository repository) {
        super(StockEnums.GROUP_ALL);
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        String type = handle.get(RouterPath.EXTRA_MATERIAL_TYPE);
        materialType = StockEnums.MATERIAL_DRUG.equals(type) ? StockEnums.MATERIAL_DRUG : StockEnums.MATERIAL_FEED;
        filterId = StockExtras.readId(handle.get(isDrug() ? RouterPath.EXTRA_DRUG_ID : RouterPath.EXTRA_FEED_ID));
    }

    @NonNull
    @Override
    protected PagingSource<Integer, ListRow<StockFlowItemVO>> createPagingSource(@Nullable String group) {
        String g = group == null ? StockEnums.GROUP_ALL : group;
        if (isDrug()) {
            return new GroupedPagedSource<>((page, size) -> repository.pageDrugFlows(null, filterId, g, page, size),
                    StockFlowListViewModel::dateHeader, f -> String.valueOf(f.flowId));
        }
        return new GroupedPagedSource<>((page, size) -> repository.pageFeedFlows(filterId, g, page, size),
                StockFlowListViewModel::dateHeader, f -> String.valueOf(f.flowId));
    }

    @NonNull
    static String dateHeader(@NonNull StockFlowItemVO f) {
        String date = f.bizDate != null ? f.bizDate : f.occurAt;
        return StockTexts.flowDateHeader(date);
    }

    public boolean isDrug() {
        return StockEnums.MATERIAL_DRUG.equals(materialType);
    }

    @NonNull
    public List<String[]> groups() {
        return isDrug() ? DRUG_GROUPS : FEED_GROUPS;
    }

    public void setGroup(@NonNull String group) {
        setFilter(group);
    }

    public void onItemClick(@NonNull StockFlowItemVO f) {
        openRecord.setValue(f);
    }

    public SingleLiveEvent<StockFlowItemVO> getOpenRecord() {
        return openRecord;
    }
}
