package com.zhimu.server.admin.system.mapper.readonly;

import com.zhimu.server.admin.system.query.ActiveStatRow;
import com.zhimu.server.admin.system.query.CategoryDistRow;
import com.zhimu.server.admin.system.query.DailyStatRow;
import com.zhimu.server.admin.system.query.FarmMapRow;
import com.zhimu.server.admin.system.query.FarmStatRow;
import com.zhimu.server.admin.system.query.MonthAuditRow;
import com.zhimu.server.admin.system.query.PendingFarmRow;
import com.zhimu.server.admin.system.query.RegionCountRow;
import com.zhimu.server.admin.system.query.UserStatRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** ADM-S06–S09 只读统计 Mapper：跨表聚合 t_farm、t_user、t_user_active_day、审核表与日志。 */
public interface AdminOverviewQueryMapper {

    FarmStatRow selectFarmStats();

    UserStatRow selectUserStats(@Param("monthStart") LocalDate monthStart);

    ActiveStatRow selectActiveStats(@Param("start") LocalDate start,
                                    @Param("end") LocalDate end,
                                    @Param("today") LocalDate today);

    List<CategoryDistRow> selectCategoryDistribution();

    List<MonthAuditRow> selectMonthAudit(@Param("monthStart") LocalDateTime monthStart,
                                        @Param("now") LocalDateTime now);

    List<FarmMapRow> selectFarmMap(@Param("level") String level,
                                   @Param("parentCode") String parentCode,
                                   @Param("species") String species,
                                   @Param("categoryId") Long categoryId);

    RegionCountRow selectRegionCounts(@Param("adcodePrefix") String adcodePrefix,
                                      @Param("categoryId") Long categoryId);

    String selectRegionName(@Param("adcodePrefix") String adcodePrefix);

    List<PendingFarmRow> selectPendingFarms(@Param("adcodePrefix") String adcodePrefix,
                                            @Param("categoryId") Long categoryId);

    List<DailyStatRow> selectPlatformDailyStats(@Param("metric") String metric,
                                                @Param("start") LocalDate start,
                                                @Param("end") LocalDate end);

    List<DailyStatRow> selectActiveDailyStats(@Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    List<DailyStatRow> selectNewUserDailyStats(@Param("start") LocalDate start,
                                               @Param("end") LocalDate end);

    List<DailyStatRow> selectProductionDailyStats(@Param("start") LocalDate start,
                                                   @Param("end") LocalDate end);
}
