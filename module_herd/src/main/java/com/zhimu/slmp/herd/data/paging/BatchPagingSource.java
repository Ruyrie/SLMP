package com.zhimu.slmp.herd.data.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.herd.data.HerdApi;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * API-L06 批次列表分页（L03 批次视图），每页 20 条。
 */
public class BatchPagingSource extends PagedSource<BatchListItemVO> {

    public BatchPagingSource(@NonNull HerdApi api, @Nullable String status, @Nullable String keyword,
                             @Nullable String sort, @Nullable Consumer<PageVO<BatchListItemVO>> onFirstPage) {
        super((page, size) -> api.getBatches(status, keyword, sort, page, size).compose(RxResult.unwrap()),
                onFirstPage);
    }
}
