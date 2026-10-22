package com.zhimu.slmp.map.model.vo;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * F04 地图选点结果（GCJ-02），由 F02 / F06 原样写入 CreateFarmRequest、SubmitFarmChangeRequest 的地址字段：
 * 经纬度为相机中心 HALF_UP 保留 7 位；行政区取逆地理编码，city 为空（直辖市）用 province；
 * address 为格式化地址加手动补充；loc_accuracy 只在 source 为 LOCATION 时填写。
 */
public class LocationResult implements Parcelable {

    /** 使用当前位置 */
    public static final String SOURCE_LOCATION = "LOCATION";
    /** 拖动地图 */
    public static final String SOURCE_DRAG = "DRAG";
    /** 选择搜索提示 */
    public static final String SOURCE_SEARCH = "SEARCH";

    /** 经纬度保留的小数位 */
    public static final int COORD_SCALE = 7;

    @Nullable
    private final BigDecimal longitude;
    @Nullable
    private final BigDecimal latitude;
    @Nullable
    private final String province;
    @Nullable
    private final String city;
    @Nullable
    private final String district;
    @Nullable
    private final String township;
    @Nullable
    private final String adcode;
    @Nullable
    private final String address;
    @Nullable
    private final String source;
    @Nullable
    private final Integer locAccuracy;

    public LocationResult(@Nullable BigDecimal longitude, @Nullable BigDecimal latitude, @Nullable String province,
                          @Nullable String city, @Nullable String district, @Nullable String township,
                          @Nullable String adcode, @Nullable String address, @Nullable String source,
                          @Nullable Integer locAccuracy) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.province = province;
        this.city = TextUtils.isEmpty(city) ? province : city;
        this.district = district;
        this.township = township;
        this.adcode = adcode;
        this.address = address;
        this.source = source;
        this.locAccuracy = locAccuracy;
    }

    /** 经纬度按 HALF_UP 保留 7 位 */
    @NonNull
    public static BigDecimal coord(double value) {
        return BigDecimal.valueOf(value).setScale(COORD_SCALE, RoundingMode.HALF_UP);
    }

    /** 替换详细地址（手动补充门牌），经纬度与行政区不变 */
    @NonNull
    public LocationResult withAddress(@Nullable String newAddress) {
        return new LocationResult(longitude, latitude, province, city, district, township, adcode, newAddress,
                source, locAccuracy);
    }

    /** 替换来源；不是 LOCATION 时去掉定位精度 */
    @NonNull
    public LocationResult withSource(@Nullable String newSource, @Nullable Integer accuracy) {
        Integer acc = SOURCE_LOCATION.equals(newSource) ? accuracy : null;
        return new LocationResult(longitude, latitude, province, city, district, township, adcode, address,
                newSource, acc);
    }

    /** 是否带有可提交的行政区编码（地图选点时确认的条件） */
    public boolean hasAdcode() {
        return !TextUtils.isEmpty(adcode);
    }

    public boolean hasCoordinate() {
        return longitude != null && latitude != null;
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
    public String getAddress() {
        return address;
    }

    @Nullable
    public String getSource() {
        return source;
    }

    @Nullable
    public Integer getLocAccuracy() {
        return locAccuracy;
    }

    /** 地址文字或经纬度任一不同即视为不同（F06 地址比较规则） */
    public boolean sameAddressAs(@Nullable LocationResult other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(trim(address), trim(other.address))
                && sameCoord(longitude, other.longitude) && sameCoord(latitude, other.latitude);
    }

    private static boolean sameCoord(@Nullable BigDecimal a, @Nullable BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    @Nullable
    private static String trim(@Nullable String s) {
        return s == null ? null : s.trim();
    }

    // ===== Parcelable =====

    protected LocationResult(Parcel in) {
        longitude = readDecimal(in);
        latitude = readDecimal(in);
        province = in.readString();
        city = in.readString();
        district = in.readString();
        township = in.readString();
        adcode = in.readString();
        address = in.readString();
        source = in.readString();
        locAccuracy = in.readInt() == 1 ? in.readInt() : null;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        writeDecimal(dest, longitude);
        writeDecimal(dest, latitude);
        dest.writeString(province);
        dest.writeString(city);
        dest.writeString(district);
        dest.writeString(township);
        dest.writeString(adcode);
        dest.writeString(address);
        dest.writeString(source);
        if (locAccuracy == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(1);
            dest.writeInt(locAccuracy);
        }
    }

    static void writeDecimal(@NonNull Parcel dest, @Nullable BigDecimal v) {
        dest.writeString(v == null ? null : v.toPlainString());
    }

    @Nullable
    static BigDecimal readDecimal(@NonNull Parcel in) {
        String s = in.readString();
        return s == null ? null : new BigDecimal(s);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<LocationResult> CREATOR = new Creator<LocationResult>() {
        @Override
        public LocationResult createFromParcel(Parcel in) {
            return new LocationResult(in);
        }

        @Override
        public LocationResult[] newArray(int size) {
            return new LocationResult[size];
        }
    };
}
