package com.zhimu.slmp.inspect.ui.backfill;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.google.android.material.datepicker.CalendarConstraints;

import java.util.ArrayList;
import java.util.List;

/**
 * 日期选择器只允许选给定的日期（UTC 0 点毫秒，与 MaterialDatePicker 一致）：补录只开放 backfill_dates。
 */
public class AllowedDatesValidator implements CalendarConstraints.DateValidator {

    private final List<Long> allowed;

    public AllowedDatesValidator(@NonNull List<Long> allowed) {
        this.allowed = new ArrayList<>(allowed);
    }

    @Override
    public boolean isValid(long date) {
        return allowed.contains(date);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        long[] arr = new long[allowed.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = allowed.get(i);
        }
        dest.writeLongArray(arr);
    }

    public static final Creator<AllowedDatesValidator> CREATOR = new Creator<AllowedDatesValidator>() {
        @Override
        public AllowedDatesValidator createFromParcel(Parcel in) {
            long[] arr = in.createLongArray();
            List<Long> list = new ArrayList<>();
            if (arr != null) {
                for (long l : arr) {
                    list.add(l);
                }
            }
            return new AllowedDatesValidator(list);
        }

        @Override
        public AllowedDatesValidator[] newArray(int size) {
            return new AllowedDatesValidator[size];
        }
    };
}
