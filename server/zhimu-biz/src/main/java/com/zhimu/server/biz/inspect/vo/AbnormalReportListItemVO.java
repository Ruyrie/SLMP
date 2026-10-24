package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** API-I27 异常上报列表项。 */
@Data
public class AbnormalReportListItemVO {

    private long reportId;
    private int version;
    private String reportedAt;
    private String reportDate;
    private long penId;
    private String penName;
    private long batchId;
    private String batchNo;
    private int sickCount;
    private int stockSnapshot;
    private BigDecimal sickRate;
    private List<String> symptomNames = new ArrayList<>();
    private String description;
    private String status;
    private String statusText;
    private String aiState;
    private String aiStateText;
    private String urgency;
    private String urgencyText;
    private boolean majorEpidemic;
    private String topCauseName;
    private long reporterId;
    private String reporterName;
    private Long inspectionId;
    private String source;
    private boolean canHandle;
    private boolean canRediagnose;
}
