package com.zhimu.server.admin.audit.query;

import lombok.Getter;
import lombok.Setter;

/** 找回账号所在的养殖场与角色。 */
@Getter
@Setter
public class RecoveryFarmRow {

    private Long farmId;
    private String farmName;
    private String role;
    private String penText;
}
