package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 通知送达明细（t_notice_recipient）：一个账号一条，记录最高角色、标签养殖场与阅读/推送点开时间。 */
@Getter
@Setter
@TableName("t_notice_recipient")
public class NoticeRecipient extends BaseEntity {

    private Long noticeId;
    private Long userId;
    /** OWNER / MANAGER / STAFF / NONE */
    private String roleSnapshot;
    private Long farmId;
    private Long messageId;
    private Long pushRecordId;
    private LocalDateTime readAt;
    private LocalDateTime pushOpenedAt;
    private Boolean isHidden;
}
