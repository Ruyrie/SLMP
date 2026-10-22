package com.zhimu.slmp.breed.data.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.data.BreedApi;
import com.zhimu.slmp.breed.model.request.BreedingDueQuery;
import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * API-B22 待产分页（B01 待产分段、B02），跨分组连续分页，每页 20 条；第 1 页回调 tab_counts（分组标题数量）。
 */
public class FarrowDuePagingSource extends PagedSource<FarrowDueItemVO> {

    public FarrowDuePagingSource(@NonNull BreedApi api, @NonNull BreedingDueQuery query,
                                 @Nullable Consumer<PageVO<FarrowDueItemVO>> onFirstPage) {
        super((page, size) -> api.getFarrowDue(query.getKeyword(), page, size).compose(RxResult.unwrap()),
                onFirstPage);
    }
}
