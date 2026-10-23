package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

/** 处理结果（嵌入 DiagnosisDetailVO.handle）。ai_match：MATCH / NOT_MATCH / NONE。 */
@Data
public class HandleVO {

    private long handleId;
    private String diagnostician;
    private String treatment;
    private boolean usedDrug;
    private boolean medicationConverted;
    private int curedCount;
    private int culledCount;
    private int totalCount;
    private String aiMatch;
    private String aiMatchText;
    private String handledByName;
    private String handledAt;
    private String updatedByName;
    private String updatedAt;
    private boolean hasRevisions;
    private boolean closed;
    private int version;
}
