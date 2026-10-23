package com.zhimu.server.biz.inspect.support;

import java.math.BigDecimal;

/** 位置判定：球面距离（地球半径 6371000 米，GCJ-02 直接计算），≤ 1000 米为现场。 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_M = 6_371_000d;

    private GeoDistance() {
    }

    /** @param clientStatus OK / DENIED / FAILED */
    public static Result evaluate(String clientStatus, BigDecimal latitude, BigDecimal longitude,
                                  BigDecimal farmLatitude, BigDecimal farmLongitude) {
        if (!"OK".equals(clientStatus) || latitude == null || longitude == null
                || farmLatitude == null || farmLongitude == null) {
            return new Result("NO_LOCATION", null);
        }
        double meters = distanceMeters(latitude.doubleValue(), longitude.doubleValue(),
                farmLatitude.doubleValue(), farmLongitude.doubleValue());
        int rounded = (int) Math.round(meters);
        return new Result(rounded <= InspectRules.ON_SITE_METERS ? "ON_SITE" : "OFF_SITE", rounded);
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    public record Result(String locationStatus, Integer distanceM) {
    }
}
