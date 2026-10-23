package com.zhimu.server.biz.farm.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 提交资料变更（API-F14） */
@Data
public class SubmitFarmChangeRequest {
    private String name;
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
    private String remark;
}
