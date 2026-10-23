package com.zhimu.server.biz.inspect.query;

import lombok.Data;

import java.time.LocalDateTime;

/** API-I24 可关联上报的查询行。 */
@Data
public class LinkableRow {
    private Long reportId;
    private Long diagnosisId;
    private LocalDateTime reportedAt;
    private Long penId;
    private String penName;
    private Long batchId;
    private String batchNo;
    private String topCauseName;
    private String status;
}
