package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

/** API-I16 / I26 上报结果。 */
@Data
public class ReportCreateResultVO {

    private long reportId;
    private String status;
    private String aiState;
    private int pollIntervalSec;
    private int pollMaxSec;
}
