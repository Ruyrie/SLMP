package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** API-I24 可关联上报查询：batch_id 或 pen_id 二选一必填。 */
@Data
public class LinkableQuery {

    private Long batchId;
    private Long penId;
}
