package com.zhimu.server.biz.farm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资料变更申请（t_farm_change） */
@Getter
@Setter
@FarmScoped
@TableName("t_farm_change")
public class FarmChange extends AuditEntity {

    private Long farmId;
    private Integer changeNo;
    /** AUDIT_STATUS：PENDING / APPROVED / REJECTED / CANCELED */
    private String status;
    private Boolean pendingFlag;
    private Boolean nameChanged;
    private Boolean addressChanged;
    private Boolean qualChanged;
    private String newName;
    private String newAddress;
    private String newProvince;
    private String newCity;
    private String newDistrict;
    private String newTownship;
    private String newAdcode;
    private BigDecimal newLongitude;
    private BigDecimal newLatitude;
    private Integer newLocAccuracy;
    private String remark;
    private String beforeJson;
    private String auditOpinion;
    private String rejectReason;
    private Long auditedBy;
    private LocalDateTime auditedAt;
    private String cancelReason;
    private String requestId;

    @Version
    private Integer version;
}
