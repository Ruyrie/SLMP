package com.zhimu.slmp.map.model.vo;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 地图上的一个点（GCJ-02）：单次定位结果（带精度，米）或相机目标。
 */
public class MapPoint implements Parcelable {

    @NonNull
    private final BigDecimal latitude;
    @NonNull
    private final BigDecimal longitude;
    @Nullable
    private final Integer accuracy;

    public MapPoint(@NonNull BigDecimal latitude, @NonNull BigDecimal longitude, @Nullable Integer accuracy) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
    }

    public MapPoint(double latitude, double longitude, @Nullable Integer accuracy) {
        this(LocationResult.coord(latitude), LocationResult.coord(longitude), accuracy);
    }

    @NonNull
    public BigDecimal getLatitude() {
        return latitude;
    }

    @NonNull
    public BigDecimal getLongitude() {
        return longitude;
    }

    @Nullable
    public Integer getAccuracy() {
        return accuracy;
    }

    protected MapPoint(Parcel in) {
        BigDecimal lat = LocationResult.readDecimal(in);
        BigDecimal lng = LocationResult.readDecimal(in);
        latitude = lat == null ? BigDecimal.ZERO : lat;
        longitude = lng == null ? BigDecimal.ZERO : lng;
        accuracy = in.readInt() == 1 ? in.readInt() : null;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        LocationResult.writeDecimal(dest, latitude);
        LocationResult.writeDecimal(dest, longitude);
        if (accuracy == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(1);
            dest.writeInt(accuracy);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MapPoint> CREATOR = new Creator<MapPoint>() {
        @Override
        public MapPoint createFromParcel(Parcel in) {
            return new MapPoint(in);
        }

        @Override
        public MapPoint[] newArray(int size) {
            return new MapPoint[size];
        }
    };
}
