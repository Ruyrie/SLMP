package com.zhimu.server.admin.content.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/** 会话内一轮问答（详情抽屉 context.rounds）。 */
@Data
public class QaRoundRow {
    private Long messageId;
    private Integer roundNo;
    private LocalDateTime askedAt;
    private String questionText;
    private String imageFileIds;
    private String status;
    private String answerType;
    private String answerText;
    private String rating;
    private LocalDateTime ratedAt;
}
