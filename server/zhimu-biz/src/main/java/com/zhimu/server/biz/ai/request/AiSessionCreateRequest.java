package com.zhimu.server.biz.ai.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建空会话：title 与 batch_id 都可选；不传 title 时显示“新对话”。
 */
@Getter
@Setter
public class AiSessionCreateRequest {

    private String title;
    private Long batchId;
}
