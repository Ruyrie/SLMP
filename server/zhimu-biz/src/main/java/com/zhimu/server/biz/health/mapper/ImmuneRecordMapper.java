package com.zhimu.server.biz.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.health.entity.ImmuneRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 接种记录 Mapper。 */
public interface ImmuneRecordMapper extends BaseMapper<ImmuneRecord> {

    @Select("SELECT * FROM t_immune_record WHERE farm_id = #{farmId} AND client_request_id = #{clientRequestId} "
            + "AND deleted = 0 LIMIT 1")
    ImmuneRecord selectByClientRequestId(@Param("farmId") Long farmId,
                                         @Param("clientRequestId") String clientRequestId);

    @Select("SELECT * FROM t_immune_record WHERE plan_id = #{planId} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    ImmuneRecord selectEffectiveByPlanId(@Param("planId") Long planId);
}
