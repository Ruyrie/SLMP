package com.zhimu.server.admin.system.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-S15 养殖场列表行；只返回汇总，不返回账目与凭证。 */
@Data
public class AdminFarmItemVO {

    private Long farmId;
    private String name;
    private String categoryName;
    private String ownerName;
    /** 查询时为完整号码，Service 输出前打码 */
    private String ownerPhone;
    @JsonIgnore
    private String auditStatus;
    @JsonIgnore
    private Boolean disabled;
    @JsonIgnore
    private Boolean frozen;
    private String regionText;
    private Integer memberCount;
    private Integer stockCount;
    private String stockUnit;
    private LocalDateTime approvedAt;
    private Integer records7d;
    private String farmStatus;
    private String farmStatusText;
    private Boolean changePending;
    private Long pendingAuditId;
    private Long pendingChangeId;
    private Integer version;
    private List<String> actions = new ArrayList<>();
}
