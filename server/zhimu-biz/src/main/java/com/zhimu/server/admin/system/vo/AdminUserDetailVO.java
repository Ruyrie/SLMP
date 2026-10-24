package com.zhimu.server.admin.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-S11 账号详情：基本信息、所在养殖场与角色、近 90 天登录记录。 */
@Data
@Builder
public class AdminUserDetailVO {

    private Long userId;
    private String name;
    private String phone;
    private Boolean canRevealPhone;
    private String status;
    private String statusText;
    private Integer version;
    private String roleSummary;
    private LocalDateTime registeredAt;
    private String registerClientText;
    private LocalDateTime recoveryCodeCreatedAt;
    private LocalDateTime passwordUpdatedAt;
    private String recoveryText;
    private LocalDateTime lastLoginAt;
    private String lastLoginClientText;
    @Builder.Default
    private List<Farm_> farms = new ArrayList<>();
    @Builder.Default
    private List<LoginLog> loginLogs = new ArrayList<>();
    private String identityTip;

    @Data
    @Builder
    public static class Farm_ {
        private Long farmId;
        private String farmName;
        private String role;
        private String roleText;
        private String farmStatus;
        private String farmStatusText;
        private String scaleText;
        private Integer stockCount;
        private String countUnitText;
        private Integer penCount;
    }

    @Data
    @Builder
    public static class LoginLog {
        private LocalDateTime time;
        private String clientText;
        private String ipRegion;
        private String result;
        private String resultText;
    }
}
