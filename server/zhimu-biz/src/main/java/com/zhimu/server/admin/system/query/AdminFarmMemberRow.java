package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-S16 养殖场成员。 */
@Getter
@Setter
public class AdminFarmMemberRow {

    private Long userId;
    private String name;
    private String phone;
    private String role;
    private String penText;
    private LocalDateTime joinedAt;
    private LocalDateTime lastLoginAt;
}
