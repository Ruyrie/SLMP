package com.zhimu.server.biz.farm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 养殖场（t_farm，当前生效信息） */
@Getter
@Setter
@TableName("t_farm")
public class Farm extends BaseEntity {

    private String name;
    private Long ownerUserId;
    private Long mainCategoryId;
    private Integer scale;
    private String contactName;
    private String contactPhone;
    private String intro;
    private String address;
    private String province;
    private String city;
    private String district;
    private String township;
    private String adcode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordType;
    private Integer locAccuracy;
    /** FARM_AUDIT_STATUS：PENDING / REJECTED / APPROVED */
    private String auditStatus;
    private Long latestAuditId;
    private LocalDateTime approvedAt;
    private Boolean disabled;
    private LocalDateTime disabledAt;
    private Long disabledBy;
    private Boolean frozen;
    private LocalDateTime frozenAt;
    private String frozenReason;
    private Long frozenBy;
    /** FARM_DELETE_TYPE：OWNER_DELETE / ACCOUNT_CANCEL */
    private String deleteType;
    private LocalDateTime deletedAt;

    @Version
    private Integer version;

    @TableLogic
    private Boolean deleted;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    private Long updatedBy;
}
