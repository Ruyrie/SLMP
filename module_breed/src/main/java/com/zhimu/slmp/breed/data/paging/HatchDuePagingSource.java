package com.zhimu.slmp.breed.data.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.data.BreedApi;
import com.zhimu.slmp.breed.model.request.BreedingDueQuery;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * API-B23 待出雏分页（B02），每页 20 条；第 1 页回调 tab_counts。
 */
public class HatchDuePagingSource extends PagedSource<HatchDueItemVO> {

    public HatchDuePagingSource(@NonNull BreedApi api, @NonNull BreedingDueQuery query,
                                @Nullable Consumer<PageVO<HatchDueItemVO>> onFirstPage) {
        super((page, size) -> api.getHatchDue(query.getKeyword(), page, size).compose(RxResult.unwrap()),
                onFirstPage);
    }
}
