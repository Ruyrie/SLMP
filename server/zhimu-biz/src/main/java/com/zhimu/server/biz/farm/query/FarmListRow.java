package com.zhimu.server.biz.farm.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * F01 列表行（t_farm_member + t_farm）：跨养殖场一次查出本人在场、未删除的养殖场与成员信息。
 * 由 FarmMapper.xml 的 selectRowsOfUser 填充。
 */
@Getter
@Setter
public class FarmListRow {

    private Long farmId;
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
    private String auditStatus;
    private LocalDateTime approvedAt;
    private Boolean disabled;
    private LocalDateTime disabledAt;
    private Boolean frozen;
    private LocalDateTime frozenAt;
    private Integer version;
    private LocalDateTime createdAt;

    private Long memberId;
    private Long userId;
    private String role;
    private String joinType;
    private LocalDateTime lastSwitchAt;
}
