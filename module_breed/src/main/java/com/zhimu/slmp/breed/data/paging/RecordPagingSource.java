package com.zhimu.slmp.breed.data.paging;

import androidx.annotation.NonNull;

import com.zhimu.slmp.breed.data.BreedApi;
import com.zhimu.slmp.breed.model.vo.BreedingRecordVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.paging.PagedSource;

/**
 * API-B06 繁育记录分页（B06），按日期倒序，每页 20 条。
 */
public class RecordPagingSource extends PagedSource<BreedingRecordVO> {

    public RecordPagingSource(@NonNull BreedApi api, long animalId) {
        super((page, size) -> api.getRecords(animalId, page, size).compose(RxResult.unwrap()));
    }
}
