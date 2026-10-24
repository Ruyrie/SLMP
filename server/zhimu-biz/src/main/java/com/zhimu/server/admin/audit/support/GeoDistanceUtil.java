package com.zhimu.server.admin.audit.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 球面距离（地球半径 6371 千米），千米保留 1 位小数；坐标为空时返回 null。 */
public final class GeoDistanceUtil {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistanceUtil() {
    }

    public static BigDecimal distanceKm(BigDecimal latitude1, BigDecimal longitude1,
                                        BigDecimal latitude2, BigDecimal longitude2) {
        if (latitude1 == null || longitude1 == null || latitude2 == null || longitude2 == null) {
            return null;
        }
        double lat1 = Math.toRadians(latitude1.doubleValue());
        double lat2 = Math.toRadians(latitude2.doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(longitude2.doubleValue() - longitude1.doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
        return BigDecimal.valueOf(EARTH_RADIUS_KM * c).setScale(1, RoundingMode.HALF_UP);
    }
}
