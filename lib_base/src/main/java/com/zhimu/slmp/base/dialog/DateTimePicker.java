package com.zhimu.slmp.base.dialog;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 日期、时间选择（02 号《UI设计规范》7.9 节）：封装 MaterialDatePicker / MaterialTimePicker，结果经 FragmentResult 回传。
 * <p>
 * 选择器挂在一个无界面的宿主 Fragment（{@link DateTimePickerHostFragment}）下，旋转与进程回收后自动恢复并重新挂上监听，
 * 不在页面里保存回调对象。日期字段默认值为服务器日期的今天（由调用方传入），时间为 24 小时制。
 * 这是唯一允许叠在底部面板之上的弹窗（8.1 节），只检查同一 requestKey 的选择器是否已打开。
 * <pre>
 * DateTimePicker.date(REQ_DATE).selection(LocalDate.parse(serverToday)).show(getSupportFragmentManager());
 * getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this, (key, result) -&gt;
 *         viewModel.setDate(DateTimePicker.getDate(result)));   // "2026-10-19"
 *
 * DateTimePicker.time(REQ_TIME).time(18, 0).show(getSupportFragmentManager());
 * // DateTimePicker.getTime(result) = "18:00"；getHour / getMinute 取数值
 * </pre>
 * 只有点“确定”才回传结果；取消不回传。
 */
public final class DateTimePicker {

    /** 选中日期 UTC 0 点的毫秒数（MaterialDatePicker 原始值） */
    public static final String RESULT_DATE_MILLIS = "date_millis";
    /** 选中日期，ISO 格式 yyyy-MM-dd */
    public static final String RESULT_DATE = "date";
    public static final String RESULT_HOUR = "hour";
    public static final String RESULT_MINUTE = "minute";
    /** 选中时间，HH:mm */
    public static final String RESULT_TIME = "time";
    public static final String RESULT_EXTRAS = "extras";

    private DateTimePicker() {
    }

    @NonNull
    public static DateBuilder date(@NonNull String requestKey) {
        return new DateBuilder(requestKey);
    }

    @NonNull
    public static TimeBuilder time(@NonNull String requestKey) {
        return new TimeBuilder(requestKey);
    }

    @Nullable
    public static String getDate(@NonNull Bundle result) {
        return result.getString(RESULT_DATE);
    }

    @Nullable
    public static String getTime(@NonNull Bundle result) {
        return result.getString(RESULT_TIME);
    }

    public static int getHour(@NonNull Bundle result) {
        return result.getInt(RESULT_HOUR);
    }

    public static int getMinute(@NonNull Bundle result) {
        return result.getInt(RESULT_MINUTE);
    }

    @Nullable
    public static Bundle getExtras(@NonNull Bundle result) {
        return result.getBundle(RESULT_EXTRAS);
    }

    /** LocalDate 转为 MaterialDatePicker 使用的 UTC 0 点毫秒数 */
    public static long toUtcMillis(@NonNull LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    @NonNull
    static LocalDate fromUtcMillis(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** 日期选择构建器 */
    public static final class DateBuilder {
        private final Bundle args = new Bundle();

        DateBuilder(@NonNull String requestKey) {
            args.putInt(DateTimePickerHostFragment.ARG_MODE, DateTimePickerHostFragment.MODE_DATE);
            args.putString(DateTimePickerHostFragment.ARG_REQUEST_KEY, requestKey);
        }

        /** 选择器标题，默认“选择日期” */
        @NonNull
        public DateBuilder title(@Nullable CharSequence title) {
            args.putCharSequence(DateTimePickerHostFragment.ARG_TITLE, title);
            return this;
        }

        /** 默认选中的日期（应取服务器日期的今天或字段当前值） */
        @NonNull
        public DateBuilder selection(@Nullable LocalDate date) {
            if (date != null) {
                args.putLong(DateTimePickerHostFragment.ARG_SELECTION, toUtcMillis(date));
            }
            return this;
        }

        /** 可选范围（含首尾），任一端为 null 表示不限 */
        @NonNull
        public DateBuilder bounds(@Nullable LocalDate start, @Nullable LocalDate end) {
            if (start != null) {
                args.putLong(DateTimePickerHostFragment.ARG_START, toUtcMillis(start));
            }
            if (end != null) {
                args.putLong(DateTimePickerHostFragment.ARG_END, toUtcMillis(end));
            }
            return this;
        }

        @NonNull
        public DateBuilder extras(@Nullable Bundle extras) {
            args.putBundle(DateTimePickerHostFragment.ARG_EXTRAS, extras);
            return this;
        }

        /** 显示；同一 requestKey 的选择器已打开时返回 false */
        public boolean show(@NonNull FragmentManager fm) {
            return DateTimePickerHostFragment.show(fm, args);
        }
    }

    /** 时间选择构建器（24 小时制） */
    public static final class TimeBuilder {
        private final Bundle args = new Bundle();

        TimeBuilder(@NonNull String requestKey) {
            args.putInt(DateTimePickerHostFragment.ARG_MODE, DateTimePickerHostFragment.MODE_TIME);
            args.putString(DateTimePickerHostFragment.ARG_REQUEST_KEY, requestKey);
        }

        /** 选择器标题，默认“选择时间” */
        @NonNull
        public TimeBuilder title(@Nullable CharSequence title) {
            args.putCharSequence(DateTimePickerHostFragment.ARG_TITLE, title);
            return this;
        }

        @NonNull
        public TimeBuilder time(int hour, int minute) {
            args.putInt(DateTimePickerHostFragment.ARG_HOUR, hour);
            args.putInt(DateTimePickerHostFragment.ARG_MINUTE, minute);
            return this;
        }

        /** 默认时间，"HH:mm" */
        @NonNull
        public TimeBuilder time(@Nullable String hhmm) {
            if (hhmm != null && hhmm.matches("\\d{1,2}:\\d{2}")) {
                String[] parts = hhmm.split(":");
                time(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            return this;
        }

        @NonNull
        public TimeBuilder extras(@Nullable Bundle extras) {
            args.putBundle(DateTimePickerHostFragment.ARG_EXTRAS, extras);
            return this;
        }

        public boolean show(@NonNull FragmentManager fm) {
            return DateTimePickerHostFragment.show(fm, args);
        }
    }
}
