package com.zhimu.server.admin.content.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/** 问答详情主体行（ADM-C59）。 */
@Data
public class QaDetailRow {
    private Long messageId;
    private Long sessionId;
    private Integer roundNo;
    private String questionText;
    private String imageFileIds;
    private Long farmId;
    private String farmName;
    private Long userId;
    private String askerName;
    private String askerRole;
    private Long batchId;
    private String batchNo;
    private String batchSnapshotJson;
    private Long contextCourseId;
    private String contextCourseTitle;
    private Long contextKnowledgeId;
    private String status;
    private String answerType;
    private String answerText;
    private String rating;
    private String handleStatus;
    private LocalDateTime askedAt;
    private LocalDateTime answeredAt;
    private LocalDateTime sessionStartedAt;
    private Integer roundCount;
}
