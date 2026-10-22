package com.zhimu.slmp.map.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 地点输入提示（高德 Inputtips，已过滤无坐标项）。
 */
public class AddressTipVO {

    @NonNull
    private final String name;
    @Nullable
    private final String district;
    @NonNull
    private final BigDecimal latitude;
    @NonNull
    private final BigDecimal longitude;

    public AddressTipVO(@NonNull String name, @Nullable String district, @NonNull BigDecimal latitude,
                        @NonNull BigDecimal longitude) {
        this.name = name;
        this.district = district;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public String getDistrict() {
        return district;
    }

    @NonNull
    public BigDecimal getLatitude() {
        return latitude;
    }

    @NonNull
    public BigDecimal getLongitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AddressTipVO)) {
            return false;
        }
        AddressTipVO that = (AddressTipVO) o;
        return name.equals(that.name) && Objects.equals(district, that.district)
                && latitude.compareTo(that.latitude) == 0 && longitude.compareTo(that.longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, district, latitude.doubleValue(), longitude.doubleValue());
    }
}
