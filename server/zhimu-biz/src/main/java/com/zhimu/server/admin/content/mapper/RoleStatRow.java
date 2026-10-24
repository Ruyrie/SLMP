package com.zhimu.server.admin.content.mapper;

import lombok.Data;

/** 按角色汇总的送达与已读。 */
@Data
public class RoleStatRow {
    private Long noticeId;
    private String roleSnapshot;
    private Integer delivered;
    private Integer readCount;
}
