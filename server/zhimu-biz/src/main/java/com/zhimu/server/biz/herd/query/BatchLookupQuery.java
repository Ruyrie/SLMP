package com.zhimu.server.biz.herd.query;

import lombok.Getter;
import lombok.Setter;

/** API-L14 扫码 / 批次号解析参数（二选一）。 */
@Getter
@Setter
public class BatchLookupQuery {

    private String qrContent;
    private String batchNo;
}
