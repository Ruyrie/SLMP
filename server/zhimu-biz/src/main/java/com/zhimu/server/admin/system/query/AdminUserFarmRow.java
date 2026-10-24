package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-S11 账号所在养殖场与角色。 */
@Getter
@Setter
public class AdminUserFarmRow {

    private Long farmId;
    private String farmName;
    private String role;
    private String auditStatus;
    private Boolean disabled;
    private Boolean frozen;
    private Integer stockCount;
    private String countUnit;
    private Integer penCount;
    private Integer scale;
    private String categoryName;
    private LocalDateTime joinedAt;
}
