package com.zhimu.slmp.farm.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 提交资料变更（API-F14，要求 Idempotency-Key）：名称、地址各字段、变更后的完整资质图集合（未修改项传当前值）与变更说明。
 */
public class SubmitFarmChangeRequest {

    private String name;
    private String address;
    private String province;
    private String city;
    private String district;
    @Nullable
    private String township;
    private String adcode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    @Nullable
    private Integer locAccuracy;
    private List<Long> qualificationFileIds = new ArrayList<>();
    @Nullable
    private String remark;

    public String getName() {
        return name;
    }

    public SubmitFarmChangeRequest setName(String name) {
        this.name = name;
        return this;
    }

    public SubmitFarmChangeRequest setLocation(String address, String province, String city, String district,
                                               @Nullable String township, String adcode, BigDecimal longitude,
                                               BigDecimal latitude, @Nullable Integer locAccuracy) {
        this.address = address;
        this.province = province;
        this.city = city;
        this.district = district;
        this.township = township;
        this.adcode = adcode;
        this.longitude = longitude;
        this.latitude = latitude;
        this.locAccuracy = locAccuracy;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    @NonNull
    public List<Long> getQualificationFileIds() {
        return qualificationFileIds;
    }

    public SubmitFarmChangeRequest setQualificationFileIds(@NonNull List<Long> ids) {
        this.qualificationFileIds = new ArrayList<>(ids);
        return this;
    }

    @Nullable
    public String getRemark() {
        return remark;
    }

    public SubmitFarmChangeRequest setRemark(@Nullable String remark) {
        this.remark = remark;
        return this;
    }
}
