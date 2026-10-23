package com.zhimu.server.biz.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.health.entity.DisinfectRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 消毒记录 Mapper。 */
public interface DisinfectRecordMapper extends BaseMapper<DisinfectRecord> {

    @Select("SELECT * FROM t_disinfect_record WHERE farm_id = #{farmId} AND client_request_id = #{clientRequestId} "
            + "AND deleted = 0 LIMIT 1")
    DisinfectRecord selectByClientRequestId(@Param("farmId") Long farmId,
                                            @Param("clientRequestId") String clientRequestId);
}
