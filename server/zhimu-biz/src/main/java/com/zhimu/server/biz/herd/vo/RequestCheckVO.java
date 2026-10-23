package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** API-L26 按请求编号（Idempotency-Key）查询记录是否已保存；result 按 event_type 解析。 */
@Data
public class RequestCheckVO {

    private Boolean exists;
    /** ENTRY / TRANSFER / DEATH */
    private String eventType;
    private Long eventId;
    private Long batchId;
    private Object result;
}
