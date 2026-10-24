package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-S10 用户账号列表查询行（含派生标记的原始字段）。 */
@Getter
@Setter
public class AdminUserListRow {

    private Long userId;
    private String name;
    private String phone;
    private String farmNamesText;
    private Integer farmCount;
    private Integer ownerCount;
    private Integer managerCount;
    private Integer staffCount;
    private LocalDateTime registeredAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginClient;
    private String status;
    private Integer version;
    private Integer farmPending;
    private Integer recoveryPending;
    private LocalDateTime recoveryLockUntil;
    private LocalDateTime recoveredAt;
}
