package com.zhimu.server.biz.inspect.mapper;

import com.zhimu.server.biz.inspect.query.BatchPenStockRow;
import com.zhimu.server.biz.inspect.query.FarmGeoRow;
import com.zhimu.server.biz.inspect.query.FarmMemberRow;
import com.zhimu.server.biz.inspect.query.PenResponsibleRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 本模块对其他模块表的只读查询：
 * t_farm 坐标（模块02 的 FarmBriefVO 没有坐标）、t_farm_member/t_user 成员姓名与角色、
 * t_member_pen 负责栏舍、t_batch_pen 批次在栏数、t_breed 品种名。只读，不写别人的表。
 */
public interface InspectReferenceMapper {

    @Select("SELECT id AS farmId, name, latitude, longitude FROM t_farm WHERE id = #{farmId} AND deleted = 0")
    FarmGeoRow selectFarm(@Param("farmId") Long farmId);

    @Select("SELECT m.user_id AS userId, u.name, m.role FROM t_farm_member m "
            + "JOIN t_user u ON u.id = m.user_id "
            + "WHERE m.farm_id = #{farmId} AND m.active_flag = 1 "
            + "ORDER BY FIELD(m.role, 'OWNER', 'MANAGER', 'STAFF'), m.id")
    List<FarmMemberRow> selectMembers(@Param("farmId") Long farmId);

    @Select("SELECT mp.pen_id AS penId, mp.user_id AS userId, u.name FROM t_member_pen mp "
            + "JOIN t_farm_member m ON m.id = mp.member_id AND m.active_flag = 1 "
            + "JOIN t_user u ON u.id = mp.user_id "
            + "WHERE mp.farm_id = #{farmId} ORDER BY mp.pen_id, mp.user_id")
    List<PenResponsibleRow> selectPenResponsibles(@Param("farmId") Long farmId);

    @Select("SELECT bp.batch_id AS batchId, bp.pen_id AS penId, bp.stock_count AS stockCount "
            + "FROM t_batch_pen bp JOIN t_batch b ON b.id = bp.batch_id "
            + "WHERE bp.farm_id = #{farmId} AND b.status = 'ACTIVE' AND b.deleted = 0 AND bp.stock_count > 0 "
            + "ORDER BY bp.pen_id, bp.batch_id")
    List<BatchPenStockRow> selectActiveBatchPens(@Param("farmId") Long farmId);

    @Select("SELECT bp.batch_id AS batchId, bp.pen_id AS penId, bp.stock_count AS stockCount "
            + "FROM t_batch_pen bp WHERE bp.batch_id = #{batchId} AND bp.pen_id = #{penId} LIMIT 1")
    BatchPenStockRow selectBatchPenStock(@Param("batchId") Long batchId, @Param("penId") Long penId);

    @Select("SELECT name FROM t_breed WHERE id = #{breedId}")
    String selectBreedName(@Param("breedId") Long breedId);

    @Select("SELECT name FROM t_user WHERE id = #{userId}")
    String selectUserName(@Param("userId") Long userId);

    @Select("SELECT name FROM t_pen WHERE id = #{penId}")
    String selectPenName(@Param("penId") Long penId);
}
