package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 资料变更前后快照（FarmChangeVO.before / after；客户端暂定类名）。行政区与经纬度只在 after。
 */
public class ChangeSnapshotVO {

    @Nullable
    private String name;
    @Nullable
    private String address;
    @Nullable
    private List<QualFileVO> qualificationFiles;
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
    public String getName() {
        return name;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    @NonNull
    public List<QualFileVO> getQualificationFiles() {
        return qualificationFiles == null ? Collections.emptyList() : qualificationFiles;
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
}
