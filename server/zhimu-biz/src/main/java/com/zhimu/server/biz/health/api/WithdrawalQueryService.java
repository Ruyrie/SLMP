package com.zhimu.server.biz.health.api;

import java.time.LocalDate;
import java.util.Map;

/** 有效用药记录的休药状态，供存栏、栏舍和首页使用。 */
public interface WithdrawalQueryService {

    /** 养殖场仍在休药期的批次 ID 与最晚可出栏日期。 */
    Map<Long, LocalDate> releaseDatesOfFarm(Long farmId);
}
