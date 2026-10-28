package com.zhimu.server.biz.herd.vo;

import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

/** 栏舍详情（API-L08，L11、L12、L13、L27 也返回本结构）。 */
@Data
public class PenDetailVO {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_IN_USE = "IN_USE";
    public static final String STATUS_EMPTY = "EMPTY";

    private Long penId;
    private Long categoryId;
    private String penCode;
    private String penName;
    private String penType;
    private String penTypeText;
    private String categoryName;
    private Integer capacity;
    private Integer stockCount;
    /** 还可转入（不小于 0） */
    private Integer remainingCapacity;
    /** 已超出容量的数量 */
    private Integer overCapacityCount;
    private Integer version;
    private BigDecimal occupancyRate;
    /** ENABLED / DISABLED */
    private String status;
    private String displayStatus;
    private String displayStatusText;
    private Boolean isBreedingPen;
    private Boolean canDisable;
    private Boolean canEnable;
    private String disableBlockReason;
    private String disabledAt;
    private List<PenStaff> staff;
    private List<PenBatchItem> batches;
    private String unitText;

    /** 负责人员：在 UserBriefVO 基础上带 member_id（t_farm_member.id），App 点负责人按它打开成员详情 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    public static class PenStaff extends UserBriefVO {
        private Long memberId;

        public static PenStaff of(UserBriefVO brief, Long memberId) {
            PenStaff staff = new PenStaff();
            staff.setUserId(brief.getUserId());
            staff.setName(brief.getName());
            staff.setAvatarUrl(brief.getAvatarUrl());
            staff.setRole(brief.getRole());
            staff.setRoleText(brief.getRoleText());
            staff.setMemberId(memberId);
            return staff;
        }
    }

    /** 栏舍内的在栏批次 */
    @Data
    public static class PenBatchItem {
        private Long batchId;
        private String batchNo;
        private String breedTitle;
        private Integer dayAge;
        private Integer qtyInPen;
        private Boolean inWithdrawal;
    }
}
