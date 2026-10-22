package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 注册信息（API-F06 完整；API-F09 为子集：name、地址各字段、qualification_files、qualification_count）。
 * contact_phone 完整号码只给所有者。经纬度为 GCJ-02。
 */
public class RegistrationVO {

    @Nullable
    private String name;
    private long mainCategoryId;
    @Nullable
    private String mainCategoryName;
    @Nullable
    private String countUnit;
    @Nullable
    private String countUnitText;
    private int scale;
    @Nullable
    private String contactName;
    @Nullable
    private String contactPhone;
    @Nullable
    private String contactPhoneMasked;
    @Nullable
    private String address;
    @Nullable
    private String province;
    @Nullable
    private String city;
    @Nullable
    private String district;
    @Nullable
    private String township;
    @Nullable
    private String adcode;
    @Nullable
    private BigDecimal longitude;
    @Nullable
    private BigDecimal latitude;
    @Nullable
    private Integer locAccuracy;
    @Nullable
    private Integer qualificationCount;
    @Nullable
    private List<QualFileVO> qualificationFiles;

    @Nullable
    public String getName() {
        return name;
    }

    public long getMainCategoryId() {
        return mainCategoryId;
    }

    @Nullable
    public String getMainCategoryName() {
        return mainCategoryName;
    }

    @Nullable
    public String getCountUnit() {
        return countUnit;
    }

    @Nullable
    public String getCountUnitText() {
        return countUnitText;
    }

    public int getScale() {
        return scale;
    }

    @Nullable
    public String getContactName() {
        return contactName;
    }

    @Nullable
    public String getContactPhone() {
        return contactPhone;
    }

    @Nullable
    public String getContactPhoneMasked() {
        return contactPhoneMasked;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    @Nullable
    public String getProvince() {
        return province;
    }

    @Nullable
    public String getCity() {
        return city;
    }

    @Nullable
    public String getDistrict() {
        return district;
    }

    @Nullable
    public String getTownship() {
        return township;
    }

    @Nullable
    public String getAdcode() {
        return adcode;
    }

    @Nullable
    public BigDecimal getLongitude() {
        return longitude;
    }

    @Nullable
    public BigDecimal getLatitude() {
        return latitude;
    }

    @Nullable
    public Integer getLocAccuracy() {
        return locAccuracy;
    }

    @Nullable
    public Integer getQualificationCount() {
        return qualificationCount;
    }

    @NonNull
    public List<QualFileVO> getQualificationFiles() {
        return qualificationFiles == null ? Collections.emptyList() : qualificationFiles;
    }

    /** 资质图张数：优先 qualification_count，否则按列表 */
    public int qualCount() {
        return qualificationCount != null ? qualificationCount : getQualificationFiles().size();
    }
}
