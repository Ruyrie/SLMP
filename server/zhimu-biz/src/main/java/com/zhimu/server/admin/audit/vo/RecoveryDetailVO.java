package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-R15 找回核实与处理详情。 */
@Data
@Builder
public class RecoveryDetailVO {

    private Long id;
    private Long userId;
    private String userName;
    private String status;
    private String statusText;
    private Integer version;
    private String oldPhone;
    private String newPhone;
    private Boolean newPhoneVerified;
    private Integer codeAttemptNo;
    private Integer codeFailCount;
    private LocalDateTime submittedAt;
    @Builder.Default
    private List<CheckItem> checks = new ArrayList<>();
    private String description;
    private Account account;
    @Builder.Default
    private List<FarmRoleInfo> farms = new ArrayList<>();
    private Channel channel;
    private AfterActions afterActions;
    @Builder.Default
    private List<Option> verifyMethods = new ArrayList<>();
    private String verifyMethod;
    private String verifyMethodText;
    private String handleNote;
    private String handledByName;
    private LocalDateTime handledAt;
    private Sms sms;
    private Boolean canRevealPhone;
    @Builder.Default
    private List<VerifyLog> verifyLogs = new ArrayList<>();

    @Data
    @Builder
    public static class CheckItem {
        private String code;
        private String level;
        private String text;
        private String extra;
    }

    @Data
    @Builder
    public static class Account {
        private LocalDateTime registeredAt;
        private LocalDateTime recoveryCodeCreatedAt;
        private LocalDateTime recoveredAt;
        private LocalDateTime lastLoginAt;
        private String lastLoginClientText;
    }

    @Data
    @Builder
    public static class FarmRoleInfo {
        private Long farmId;
        private String farmName;
        private String role;
        private String roleText;
        private String penText;
    }

    @Data
    @Builder
    public static class Channel {
        private String submitChannel;
        private String submitChannelText;
        private String submitDevice;
        private String submitIpRegion;
    }

    @Data
    @Builder
    public static class AfterActions {
        private String newPhone;
        private LocalDate cancelForbiddenUntil;
    }

    @Data
    @Builder
    public static class Option {
        private String value;
        private String label;
    }

    @Data
    @Builder
    public static class Sms {
        private String status;
        private String statusText;
        private Integer retryCount;
        private String warning;
    }

    @Data
    @Builder
    public static class VerifyLog {
        private LocalDateTime checkedAt;
        private String result;
        private String resultText;
        private String resultDesc;
        private String submitChannelText;
        private Integer remainingAttempts;
        private LocalDateTime lockUntil;
    }
}
