package com.zhimu.server.biz.farm.request;

import com.zhimu.server.biz.farm.support.FarmRegistrationForm;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 新建养殖场并提交审核（API-F05） */
@Data
public class CreateFarmRequest implements FarmRegistrationForm {
    private String name;
    private Long mainCategoryId;
    private Integer scale;
    private String contactName;
    private String contactPhone;
    private String address;
    private String province;
    private String city;
    private String district;
    private String township;
    private String adcode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer locAccuracy;
    private List<Long> qualificationFileIds = new ArrayList<>();
}
