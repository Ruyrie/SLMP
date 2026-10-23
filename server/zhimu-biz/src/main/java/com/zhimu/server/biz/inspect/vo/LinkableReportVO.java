package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

/** API-I24 可关联的异常上报（模块08 D04）。 */
@Data
public class LinkableReportVO {

    private long reportId;
    private Long diagnosisId;
    private String reportedAt;
    private long penId;
    private String penName;
    private long batchId;
    private String batchNo;
    private String topCauseName;
    private String status;
    private String label;
}
