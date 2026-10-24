package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 更正说明（t_ai_correction）的管理端写入视图。用户端 biz.ai.mapper 仍按原字段读取，
 * 本实体补充 session/farm/user、发布与撤回字段。
 */
@Getter
@Setter
@TableName("t_ai_correction")
public class AiCorrectionRow extends BaseEntity {

    private Long messageId;
    private Long sessionId;
    private Long farmId;
    private Long userId;
    private String content;
    /** PUBLISHED / WITHDRAWN */
    private String status;
    private Boolean publishedFlag;
    private LocalDateTime publishedAt;
    private LocalDateTime firstPublishedAt;
    private Long publishedBy;
    private LocalDateTime withdrawnAt;
    private Long withdrawnBy;
    private String withdrawReason;
    private Integer version;
    private Long createdBy;
}
