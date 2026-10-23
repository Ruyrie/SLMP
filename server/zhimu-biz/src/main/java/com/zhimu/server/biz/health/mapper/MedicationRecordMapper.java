package com.zhimu.server.biz.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 用药记录 Mapper。 */
public interface MedicationRecordMapper extends BaseMapper<MedicationRecord> {

    /** 修改申请按 ID 读取原记录：已删除的记录也要返回，由公共能力给出 41001 */
    @Select("SELECT * FROM t_medication_record WHERE id = #{id}")
    MedicationRecord selectByIdIncludingDeleted(@Param("id") Long id);

    @Select("SELECT * FROM t_medication_record WHERE farm_id = #{farmId} AND client_request_id = #{clientRequestId} "
            + "AND batch_id = #{batchId} AND deleted = 0 LIMIT 1")
    MedicationRecord selectByClientRequestId(@Param("farmId") Long farmId,
                                             @Param("clientRequestId") String clientRequestId,
                                             @Param("batchId") Long batchId);

    @Select("SELECT * FROM t_medication_record WHERE farm_id = #{farmId} AND group_no = #{groupNo} AND deleted = 0 "
            + "ORDER BY id")
    List<MedicationRecord> selectByGroupNo(@Param("farmId") Long farmId, @Param("groupNo") String groupNo);

    @Select("SELECT * FROM t_medication_record WHERE farm_id = #{farmId} AND client_request_id = #{clientRequestId} "
            + "AND deleted = 0 ORDER BY id")
    List<MedicationRecord> selectByClientRequest(@Param("farmId") Long farmId,
                                                 @Param("clientRequestId") String clientRequestId);

    /** 某批次当时有效的全部用药记录（决定可出栏日期） */
    @Select("SELECT * FROM t_medication_record WHERE farm_id = #{farmId} AND batch_id = #{batchId} AND deleted = 0")
    List<MedicationRecord> selectEffective(@Param("farmId") Long farmId, @Param("batchId") Long batchId);
}
