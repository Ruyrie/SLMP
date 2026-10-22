package com.zhimu.slmp.herd.data.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.herd.data.HerdApi;
import com.zhimu.slmp.herd.model.vo.PenStockItemVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * API-L04 按栏舍存栏分页（L03 栏舍视图），每页 20 条；第 1 页到达时回调 total（io 线程）。
 */
public class PenStockPagingSource extends PagedSource<PenStockItemVO> {

    public PenStockPagingSource(@NonNull HerdApi api, @Nullable String sort, @Nullable String keyword,
                                @Nullable Consumer<PageVO<PenStockItemVO>> onFirstPage) {
        super((page, size) -> api.getStockPens(sort, keyword, page, size).compose(RxResult.unwrap()), onFirstPage);
    }
}
