package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台通知（t_notice，管理端-02 C01）。正文为受限 HTML，配图存 t_file。
 * version 手动做条件更新（与前端回传的 version 比较），不使用 MyBatis-Plus 的 updateById 自动乐观锁。
 */
@Getter
@Setter
@TableName("t_notice")
public class Notice extends BaseEntity {

    private String title;
    /** NOTICE_LEVEL：IMPORTANT / NORMAL */
    private String level;
    private String content;
    private Integer contentLength;
    private Long imageFileId;
    /** NOTICE_SCOPE：ALL / CONDITION / FARM */
    private String scopeType;
    /** SEND_MODE：NOW / SCHEDULED */
    private String sendMode;
    private LocalDateTime scheduledAt;
    /** NOTICE_STATUS */
    private String status;
    private Integer estAccountCount;
    private Integer estFarmCount;
    /** DISPATCH_STATUS：NONE / PENDING / RUNNING / DONE */
    private String dispatchStatus;
    private Integer deliveredCount;
    private Integer readCount;
    private Integer pushOpenCount;
    private Integer readBeforeRevoke;
    private LocalDateTime statAt;
    private LocalDateTime sentAt;
    private Long sentBy;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    private String revokeReason;
    private LocalDateTime canceledAt;
    private Long canceledBy;
    private String cancelReason;
    private Long copiedFromId;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    private Long updatedBy;

    @TableLogic
    private Boolean deleted;

    private Integer version;
}
