package com.zhimu.server.admin.content.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/** 问答记录列表行（ADM-C58）。 */
@Data
public class QaRow {
    private Long messageId;
    private Long sessionId;
    private Integer roundNo;
    private String questionText;
    private String imageFileIds;
    private Long farmId;
    private String farmName;
    private Long askerId;
    private String askerName;
    private String batchNo;
    private String answerType;
    private String rating;
    private String handleStatus;
    private Boolean isSpotCheck;
    private LocalDateTime askedAt;
}
