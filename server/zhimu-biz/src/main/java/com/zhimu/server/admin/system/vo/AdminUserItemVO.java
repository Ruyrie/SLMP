package com.zhimu.server.admin.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-S10 用户账号列表行。手机号已打码，不返回密码哈希。 */
@Data
public class AdminUserItemVO {

    private Long userId;
    private String name;
    private String avatarUrl;
    private String phone;
    private String farmNamesText;
    private Integer farmCount;
    private RoleCounts roleCounts = new RoleCounts();
    private LocalDateTime registeredAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginClient;
    private String lastLoginClientText;
    private List<Flag> flags = new ArrayList<>();
    private String status;
    private String statusText;
    private Integer version;
    private List<String> actions = new ArrayList<>();

    @Data
    public static class RoleCounts {
        private Integer owner = 0;
        private Integer manager = 0;
        private Integer staff = 0;
    }

    @Data
    @Builder
    public static class Flag {
        private String code;
        private String text;
    }
}
