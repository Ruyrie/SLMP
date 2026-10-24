package com.zhimu.server.admin.system.vo;

import com.zhimu.server.common.vo.MediaItemVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-S16 养殖场详情：只含存栏、成员、活跃度等汇总值。 */
@Data
@Builder
public class AdminFarmDetailVO {

    private Long farmId;
    private String name;
    private String categoryName;
    private Owner owner;
    private String address;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private Summary summary;
    private Info info;
    @Builder.Default
    private List<Member> members = new ArrayList<>();
    private Invites invites;
    @Builder.Default
    private List<Timeline> timeline = new ArrayList<>();
    private Frozen frozen;
    private Long pendingChangeId;
    private Integer version;

    @Data
    @Builder
    public static class Owner {
        private String name;
        private String phone;
    }

    @Data
    @Builder
    public static class Summary {
        private Integer stockCount;
        private String stockUnit;
        private Integer activeBatchCount;
        private Integer records7d;
        private Integer activeMembers7d;
        private Integer memberTotal;
    }

    @Data
    @Builder
    public static class Info {
        private String scaleText;
        @Builder.Default
        private List<MediaItemVO> qualImages = new ArrayList<>();
        private String contactName;
        private String contactPhone;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class Member {
        private Long userId;
        private String name;
        private String avatarUrl;
        private String phone;
        private String role;
        private String roleText;
        private String penText;
        private LocalDateTime joinedAt;
        private LocalDateTime lastLoginAt;
    }

    @Data
    @Builder
    public static class Invites {
        private Integer pending;
        private Integer expired;
    }

    @Data
    @Builder
    public static class Timeline {
        private String event;
        private LocalDateTime time;
        private String actor;
        private String tag;
        private String desc;
    }

    @Data
    @Builder
    public static class Frozen {
        private LocalDateTime frozenAt;
        private String frozenReason;
        private String frozenByName;
    }
}
