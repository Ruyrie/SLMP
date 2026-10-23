package com.zhimu.server.biz.feeding.support;

import lombok.Data;

import java.time.LocalDateTime;

/** 模块02 在场成员只读行。 */
@Data
public class FeedMemberRow {
    private Long memberId;
    private Long userId;
    private String role;
    private LocalDateTime joinedAt;
}
