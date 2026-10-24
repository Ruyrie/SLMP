package com.zhimu.server.biz.ai.request;

import lombok.Getter;

/**
 * API-K04 修改会话：title 与 batch_id 均可单独传，也可同时传。
 * <p>
 * batch_id 的“未传”与“显式传 null”语义不同：未传保留原关联，显式 null 表示取消关联。
 * 因此这里用手写 setter 记录字段是否出现在请求体里，而不是只判断值是否为 null。
 */
@Getter
public class AiSessionBatchRequest {

    private String title;
    private Long batchId;

    /** 请求体是否出现 title */
    private boolean titlePresent;
    /** 请求体是否出现 batch_id */
    private boolean batchIdPresent;

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
        this.batchIdPresent = true;
    }
}
