package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ADM-S20 管理员行（AdminItemVO）。本人行只有 EDIT；已停用的行给 ENABLE、RESET_PASSWORD；
 * 正常行给 EDIT、RESET_PASSWORD、DISABLE。手机号打码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminItemVO {
    private Long adminId;
    private String name;
    private Boolean isSelf;
    private String username;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private String status;
    private String statusText;
    private Boolean locked;
    private LocalDateTime lockedUntil;
    private Integer version;
    @Builder.Default
    private List<String> actions = new ArrayList<>();
}
