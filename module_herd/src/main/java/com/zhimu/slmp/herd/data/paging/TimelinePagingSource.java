package com.zhimu.slmp.herd.data.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.herd.data.HerdApi;
import com.zhimu.slmp.herd.model.vo.TimelineItemVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.paging.PagedSource;

/**
 * API-L16 生产记录时间线分页（L06），按 type 筛选，每页 20 条。
 */
public class TimelinePagingSource extends PagedSource<TimelineItemVO> {

    public TimelinePagingSource(@NonNull HerdApi api, long batchId, @Nullable String type) {
        super((page, size) -> api.getTimeline(batchId, type, page, size).compose(RxResult.unwrap()));
    }
}
