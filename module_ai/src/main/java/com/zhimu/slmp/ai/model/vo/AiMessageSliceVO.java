package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-K03 按轮次切片
 */
public class AiMessageSliceVO {

    private List<AiMessageVO> list;
    private boolean hasMore;

    @NonNull
    public List<AiMessageVO> getList() {
        return list == null ? Collections.emptyList() : list;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setList(@Nullable List<AiMessageVO> list) {
        this.list = list;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
