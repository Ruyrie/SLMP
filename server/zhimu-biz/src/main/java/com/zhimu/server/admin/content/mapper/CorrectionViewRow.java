package com.zhimu.server.admin.content.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/** 更正说明管理端视图行。 */
@Data
public class CorrectionViewRow {
    private Long id;
    private Long messageId;
    private Long sessionId;
    private Long farmId;
    private Long userId;
    private String content;
    private String status;
    private Boolean publishedFlag;
    private LocalDateTime publishedAt;
    private LocalDateTime firstPublishedAt;
    private Long publishedBy;
    private String publishedByName;
    private LocalDateTime withdrawnAt;
    private String withdrawReason;
    private Integer version;
}
