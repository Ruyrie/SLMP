package com.zhimu.server.biz.farm.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 注册信息（F06 完整；F09 为子集：名称、地址各字段、资质图与张数） */
@Data
public class RegistrationVO {
    private String name;
    private Long mainCategoryId;
    private String mainCategoryName;
    private String countUnit;
    private String countUnitText;
    private Integer scale;
    private String contactName;
    private String contactPhone;
    private String contactPhoneMasked;
    private String address;
    private String province;
    private String city;
    private String district;
    private String township;
    private String adcode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer locAccuracy;
    private Integer qualificationCount;
    private List<QualFileVO> qualificationFiles;
}
