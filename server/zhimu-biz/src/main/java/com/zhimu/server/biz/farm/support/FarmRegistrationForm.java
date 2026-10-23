package com.zhimu.server.biz.farm.support;

import java.math.BigDecimal;
import java.util.List;

/** F05/F07 表单读取接口，CreateFarmRequest 与 UpdateRegistrationRequest 实现 */
public interface FarmRegistrationForm {

    String getName();

    Long getMainCategoryId();

    Integer getScale();

    String getContactName();

    String getContactPhone();

    String getAddress();

    String getProvince();

    String getCity();

    String getDistrict();

    String getTownship();

    String getAdcode();

    BigDecimal getLongitude();

    BigDecimal getLatitude();

    Integer getLocAccuracy();

    List<Long> getQualificationFileIds();
}
