package com.zhimu.slmp.map.model.vo;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

/**
 * 打开 F04 的参数：mode 为 NEW（F02 新建、修改注册信息）或 CHANGE（F06 资料变更）；farmName 为地址卡第一行；
 * initial 为原位置（有时以它为中心、16 级，不自动定位）。
 */
public class PickLocationInput implements Parcelable {

    public static final String MODE_NEW = "NEW";
    public static final String MODE_CHANGE = "CHANGE";

    @NonNull
    private final String mode;
    @Nullable
    private final String farmName;
    @Nullable
    private final LocationResult initial;

    public PickLocationInput(@NonNull String mode, @Nullable String farmName, @Nullable LocationResult initial) {
        this.mode = mode;
        this.farmName = farmName;
        this.initial = initial;
    }

    @NonNull
    public String getMode() {
        return mode;
    }

    public boolean isChange() {
        return MODE_CHANGE.equals(mode);
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @Nullable
    public LocationResult getInitial() {
        return initial;
    }

    protected PickLocationInput(Parcel in) {
        String m = in.readString();
        mode = m == null ? MODE_NEW : m;
        farmName = in.readString();
        initial = ParcelCompat.readParcelable(in, LocationResult.class.getClassLoader(), LocationResult.class);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mode);
        dest.writeString(farmName);
        dest.writeParcelable(initial, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PickLocationInput> CREATOR = new Creator<PickLocationInput>() {
        @Override
        public PickLocationInput createFromParcel(Parcel in) {
            return new PickLocationInput(in);
        }

        @Override
        public PickLocationInput[] newArray(int size) {
            return new PickLocationInput[size];
        }
    };
}
