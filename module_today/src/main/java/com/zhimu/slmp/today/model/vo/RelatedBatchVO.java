package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

/**
 * 待办详情中的关联批次。
 */
public class RelatedBatchVO {

    private long batchId;
    @Nullable
    private String batchNo;
    @Nullable
    private String penName;
    @Nullable
    private String desc;
    @Nullable
    private String tagText;
    @Nullable
    private LinkVO link;

    public long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getDesc() {
        return desc;
    }

    @Nullable
    public String getTagText() {
        return tagText;
    }

    @Nullable
    public LinkVO getLink() {
        return link;
    }
}
