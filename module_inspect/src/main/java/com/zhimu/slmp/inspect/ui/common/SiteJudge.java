package com.zhimu.slmp.inspect.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 登记位置的现场判定（模块07 文档 2.7.5，服务端文档 2.7 节）：距养殖场 ≤ 1000 米为“现场”，&gt; 1000 米为“非现场”，
 * 未授权、定位失败或养殖场没有坐标为“无位置”；只标注、不拦截，以服务端重算为准。
 * 距离用球面距离（地球半径 6371000 米，GCJ-02 坐标直接计算，与高德 AMapUtils.calculateLineDistance 结果一致到米级）。
 */
public final class SiteJudge {

    public static final int SITE_RADIUS_M = 1000;
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /** 状态：定位中、现场、非现场、无位置 */
    public enum State {
        LOCATING, ON_SITE, OFF_SITE, NO_LOCATION
    }

    private SiteJudge() {
    }

    /** 球面距离（米，取整） */
    public static int distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_M * c);
    }

    /** 按距离判定；distance 为 null（没有定位或养殖场没有坐标）时为“无位置” */
    @NonNull
    public static State judge(@Nullable Integer distanceM) {
        if (distanceM == null || distanceM < 0) {
            return State.NO_LOCATION;
        }
        return distanceM <= SITE_RADIUS_M ? State.ON_SITE : State.OFF_SITE;
    }

    /**
     * 定位结果与养殖场坐标 → 距离；任一为空返回 null。
     */
    @Nullable
    public static Integer distance(@Nullable Double lat, @Nullable Double lng, @Nullable Double farmLat,
                                   @Nullable Double farmLng) {
        if (lat == null || lng == null || farmLat == null || farmLng == null) {
            return null;
        }
        return distanceMeters(lat, lng, farmLat, farmLng);
    }

    /** 位置行文字：“现场 · 距养殖场 120 米”“距养殖场 3.2 千米”（非现场标签另显示）“未获得位置，不影响登记” */
    @NonNull
    public static String text(@NonNull State state, @Nullable Integer distanceM) {
        switch (state) {
            case LOCATING:
                return "正在定位";
            case ON_SITE:
                return "现场 · 距养殖场 " + InspectFormat.distance(distanceM == null ? 0 : distanceM);
            case OFF_SITE:
                return "距养殖场 " + InspectFormat.distance(distanceM == null ? 0 : distanceM);
            case NO_LOCATION:
            default:
                return "未获得位置，不影响登记";
        }
    }
}
