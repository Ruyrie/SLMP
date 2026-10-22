package com.zhimu.slmp.farm.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 新建养殖场并提交审核（API-F05，要求 Idempotency-Key）。地址各字段来自 F04 的 LocationResult；
 * township、loc_accuracy 可空（null 不输出）。
 */
public class CreateFarmRequest {

    private String name;
    private long mainCategoryId;
    private int scale;
    private String contactName;
    private String contactPhone;
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

    public String getName() {
        return name;
    }

    public CreateFarmRequest setName(String name) {
        this.name = name;
        return this;
    }

    public long getMainCategoryId() {
        return mainCategoryId;
    }

    public CreateFarmRequest setMainCategoryId(long mainCategoryId) {
        this.mainCategoryId = mainCategoryId;
        return this;
    }

    public int getScale() {
        return scale;
    }

    public CreateFarmRequest setScale(int scale) {
        this.scale = scale;
        return this;
    }

    public String getContactName() {
        return contactName;
    }

    public CreateFarmRequest setContactName(String contactName) {
        this.contactName = contactName;
        return this;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public CreateFarmRequest setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public String getProvince() {
        return province;
    }

    public String getCity() {
        return city;
    }

    public String getDistrict() {
        return district;
    }

    @Nullable
    public String getTownship() {
        return township;
    }

    public String getAdcode() {
        return adcode;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    @Nullable
    public Integer getLocAccuracy() {
        return locAccuracy;
    }

    /** 地址各字段（来自 LocationResult） */
    public CreateFarmRequest setLocation(String address, String province, String city, String district,
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

    @NonNull
    public List<Long> getQualificationFileIds() {
        return qualificationFileIds;
    }

    public CreateFarmRequest setQualificationFileIds(@NonNull List<Long> ids) {
        this.qualificationFileIds = new ArrayList<>(ids);
        return this;
    }
}
