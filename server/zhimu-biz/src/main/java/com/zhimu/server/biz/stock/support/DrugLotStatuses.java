package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.common.util.BeijingTime;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 批号状态与临期口径（模块09 2.7 节）：
 * 优先级 RECALLED &gt; BANNED &gt; EXPIRED &gt; NEAR_EXPIRY &gt; NORMAL，每次查询按当天计算，不落库。
 */
public final class DrugLotStatuses {

    private DrugLotStatuses() {
    }

    /** 剩余天数：有效期至 − 今天；过期为负 */
    public static Integer daysToExpire(LocalDate expireDate) {
        return expireDate == null ? null : (int) ChronoUnit.DAYS.between(BeijingTime.today(), expireDate);
    }

    /** 是否为已过期（今天晚于有效期至） */
    public static boolean expired(LocalDate expireDate) {
        return expireDate != null && BeijingTime.today().isAfter(expireDate);
    }

    /** 是否临期（今天 ≥ 有效期至 − 临期天数，且今天 ≤ 有效期至） */
    public static boolean nearExpiry(LocalDate expireDate, int nearDays) {
        if (expireDate == null) {
            return false;
        }
        Integer days = daysToExpire(expireDate);
        return days != null && days >= 0 && days <= nearDays;
    }

    /** 批号状态（派生） */
    public static String status(DrugLot lot, int nearDays, boolean banned) {
        if (lot.getRecalledAt() != null) {
            return StockCodes.LOT_RECALLED;
        }
        if (banned) {
            return StockCodes.LOT_BANNED;
        }
        if (expired(lot.getExpireDate())) {
            return StockCodes.LOT_EXPIRED;
        }
        if (nearExpiry(lot.getExpireDate(), nearDays)) {
            return StockCodes.LOT_NEAR_EXPIRY;
        }
        return StockCodes.LOT_NORMAL;
    }

    /** 批号状态说明（App 的 status_note）：停用批号标注只能报废 */
    public static String statusNote(String status) {
        return StockCodes.LOT_RECALLED.equals(status) ? "平台停用，只能报废" : null;
    }

    /** 可出库、可退货、可被其他模块选择：未过期、未停用、非禁用药 */
    public static boolean usable(DrugLot lot, boolean banned) {
        return lot.getRecalledAt() == null && !banned && !expired(lot.getExpireDate());
    }
}
