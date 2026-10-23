package com.zhimu.server.biz.feeding.support;

import lombok.Data;

/** 模块04 批次栏舍存栏只读行。 */
@Data
public class FeedBatchPenRow {
    private Long penId;
    private Integer stockCount;
}
