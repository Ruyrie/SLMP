package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

/** API-G08 按请求编号（提交时的幂等键）查询投喂：saved 为真按成功处理，否则以同一幂等键重发。 */
public class FeedRecordLookupVO {

    private Boolean saved;
    private FeedRecordDetailVO record;

    public boolean isSaved() {
        return Boolean.TRUE.equals(saved);
    }

    @Nullable
    public FeedRecordDetailVO getRecord() {
        return record;
    }
}
