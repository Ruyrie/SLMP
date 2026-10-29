package com.zhimu.server.biz.health.api;

import java.time.LocalDate;
import java.util.Map;

/** 有效用药记录的休药状态，供存栏、栏舍、首页和出栏使用。 */
public interface WithdrawalQueryService {

    /** 养殖场仍在休药期的批次 ID 与最晚可出栏日期。 */
    Map<Long, LocalDate> releaseDatesOfFarm(Long farmId);

    /**
     * 养殖场各批次在 {@code date} 当天的休药状态（只含当天仍在休药期的批次）。
     * 口径与 {@link #releaseDatesOfFarm} 一致：有效用药记录中最晚的休药结束日（即可出栏日期）晚于该日；
     * 首次用药日晚于该日的记录不算（那天还没用药）。
     */
    Map<Long, WithdrawalStatus> statusesOfFarm(Long farmId, LocalDate date);

    /** 某批次在 {@code date} 当天的休药状态，不在休药期返回 null。 */
    default WithdrawalStatus statusOf(Long farmId, Long batchId, LocalDate date) {
        return batchId == null ? null : statusesOfFarm(farmId, date).get(batchId);
    }

    /**
     * 休药状态。
     *
     * @param batchId     批次 ID
     * @param releaseDate 可出栏日期（当天起可以出栏）
     * @param drugName    决定可出栏日期的药品
     * @param lastMedDate 该药品的最后用药日
     * @param withdrawalDays 该药品的休药天数
     */
    record WithdrawalStatus(Long batchId, LocalDate releaseDate, String drugName, LocalDate lastMedDate,
                            Integer withdrawalDays) {
    }
}
