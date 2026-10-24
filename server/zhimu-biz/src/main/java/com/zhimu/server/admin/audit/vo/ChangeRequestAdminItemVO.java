package com.zhimu.server.admin.audit.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-R10 修改申请列表行。 */
@Data
public class ChangeRequestAdminItemVO {

    private Long id;
    private String requestNo;
    private String bizType;
    private String bizTypeText;
    private String title;
    private String recordLocator;
    private Long farmId;
    private String farmName;
    private String sourceModule;
    private String sourceModuleText;
    private String changeType;
    private String changeTypeText;
    private String beforeDisplay;
    private String afterDisplay;
    /** 查询用：由 Service 组装 title 与 impacts 后不输出 */
    @JsonIgnore
    private String fieldName;
    @JsonIgnore
    private Integer impactStock;
    @JsonIgnore
    private Integer impactCost;
    @JsonIgnore
    private Integer impactInventory;
    @JsonIgnore
    private Integer impactSellableDate;
    @JsonIgnore
    private Integer impactIncome;
    @JsonIgnore
    private Integer impactStats;
    private List<ImpactTag> impacts = new ArrayList<>();
    private String submitterName;
    private String submitterRole;
    private String submitterRoleText;
    private LocalDateTime submittedAt;
    private Integer waitMinutes;
    private String waitText;
    private Boolean overdue;
    private String status;
    private String statusText;
    private String reviewerName;
    private LocalDateTime reviewedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactTag {
        private String code;
        private String text;
        private Boolean warn;
    }
}
