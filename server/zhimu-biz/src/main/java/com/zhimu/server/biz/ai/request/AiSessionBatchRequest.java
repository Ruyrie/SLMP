package com.zhimu.server.biz.ai.request;

import lombok.Getter;
import lombok.Setter;

/**
 * API-K04 修改关联批次：batch_id 为 null 表示取消关联。
 */
@Getter
@Setter
public class AiSessionBatchRequest {

    private Long batchId;
}
