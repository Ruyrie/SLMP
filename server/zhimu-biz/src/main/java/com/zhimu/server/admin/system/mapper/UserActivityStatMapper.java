package com.zhimu.server.admin.system.mapper;

import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 平台活跃统计写入及真实日志、业务登记记录的汇总。 */
public interface UserActivityStatMapper {
    int touch(@Param("date") LocalDate date, @Param("userId") Long userId,
              @Param("farmId") Long farmId, @Param("now") LocalDateTime now);
    int backfillActivity(@Param("start") LocalDate start, @Param("end") LocalDate end);
    int resetProduction(@Param("start") LocalDate start, @Param("end") LocalDate end);
    int aggregateProduction(@Param("start") LocalDate start, @Param("end") LocalDate end);
    int aggregatePlatformDay(@Param("date") LocalDate date);
}
