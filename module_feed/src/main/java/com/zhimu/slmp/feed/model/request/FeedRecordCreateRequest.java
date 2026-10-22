package com.zhimu.slmp.feed.model.request;

import java.math.BigDecimal;

/**
 * API-G04 确认投喂（请求头 Idempotency-Key）：实际投喂量大于 0、1 位小数、不超过 99999.9 且不超过所选饲料结存；
 * 养殖户必填执行人，员工不传（服务端取本人）；备注不超过 100 字。
 */
public class FeedRecordCreateRequest {

    public long taskId;
    public long feedId;
    public BigDecimal actualQty;
    public Long executorId;
    public String remark;
}
