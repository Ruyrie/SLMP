package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D41 按请求编号查询登记结果：status 为 DONE / NOT_FOUND。
 */
public class HealthRequestStatusVO {

    @Nullable
    private String status;
    @Nullable
    private String bizType;
    @Nullable
    private String groupNo;
    @Nullable
    private List<Long> bizIds;

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getBizType() {
        return bizType;
    }

    @Nullable
    public String getGroupNo() {
        return groupNo;
    }

    @NonNull
    public List<Long> getBizIds() {
        return bizIds == null ? Collections.emptyList() : bizIds;
    }
}
