package com.zhimu.server.biz.farm.vo;

import lombok.Data;

import java.util.List;

/** 资料变更（F13 最近一次、F14 提交结果） */
@Data
public class FarmChangeVO {
    private Long changeId;
    private Integer changeNo;
    private String status;
    private String statusText;
    private String submittedAt;
    private String auditedAt;
    private String rejectReason;
    private String remark;
    /** NAME / ADDRESS / QUALIFICATION */
    private List<String> changedFields;
    private ChangeSnapshotVO before;
    private ChangeSnapshotVO after;
    private Integer addedCount;
    private Integer removedCount;
}
