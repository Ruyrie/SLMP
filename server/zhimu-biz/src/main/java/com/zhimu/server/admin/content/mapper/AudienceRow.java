package com.zhimu.server.admin.content.mapper;

import lombok.Data;

/** 接收人解析的中间行：一个账号在一个养殖场的角色。 */
@Data
public class AudienceRow {
    private Long userId;
    private String userName;
    private Long farmId;
    private String farmName;
    private String role;
}
