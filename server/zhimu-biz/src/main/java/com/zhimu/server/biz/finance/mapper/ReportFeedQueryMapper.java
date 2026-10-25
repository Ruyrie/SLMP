package com.zhimu.server.biz.finance.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 经营报表只读聚合：有效投喂记录中的实际千克数。养殖场范围由报表服务显式传入。 */
public interface ReportFeedQueryMapper {

    @Select("""
            <script>
            SELECT COALESCE(SUM(actual_qty), 0) FROM t_feed_record
            WHERE status = 'NORMAL' AND feed_date BETWEEN #{start} AND #{end}
              AND farm_id IN
              <foreach collection="farmIds" item="farmId" open="(" separator="," close=")">
                #{farmId}
              </foreach>
            </script>
            """)
    BigDecimal sumActualKg(@Param("farmIds") List<Long> farmIds,
                           @Param("start") LocalDate start, @Param("end") LocalDate end);
}
