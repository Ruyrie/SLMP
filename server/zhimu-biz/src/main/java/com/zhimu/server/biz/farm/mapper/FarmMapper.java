package com.zhimu.server.biz.farm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.query.FarmListRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

import java.util.List;

/** 养殖场 Mapper：单表用通用方法，F01 跨养殖场列表用 XML。 */
public interface FarmMapper extends BaseMapper<Farm> {

    /** 本人在场、未删除的养殖场（含成员行信息），按 F01 需要的字段一次查出 */
    List<FarmListRow> selectRowsOfUser(@Param("userId") Long userId);

    /**
     * 按 ID 取养殖场（含已删除）：用于区分 40401 与 41001“养殖场已被删除”。
     * 自定义 SQL 不经过逻辑删除插件，调用方自行判断 deleted。
     */
    @Select("SELECT * FROM t_farm WHERE id = #{id} LIMIT 1")
    Farm selectIncludingDeleted(@Param("id") Long id);

    @Select("SELECT * FROM t_farm WHERE id = #{farmId} AND owner_user_id = #{userId} AND deleted = 0 FOR UPDATE")
    Farm lockOwnedForCancellation(@Param("farmId") Long farmId, @Param("userId") Long userId);

    /** 包含旧版本曾提前逻辑删除的场，但只允许已注销且仍持有所有权的账号。 */
    @Select("SELECT f.* FROM t_farm f JOIN t_user u ON u.id = f.owner_user_id "
            + "WHERE f.owner_user_id = #{userId} AND u.status = 'CANCELLED' "
            + "AND f.delete_type = 'ACCOUNT_CANCEL' ORDER BY f.id FOR UPDATE")
    List<Farm> lockCancelledOwnerFarms(@Param("userId") Long userId);

    /** 兼容旧版提前逻辑删除的记录，不受 MyBatis 逻辑删除过滤影响。 */
    @Update("UPDATE t_farm SET disabled_at = #{now}, updated_at = #{now}, version = version + 1 "
            + "WHERE id = #{farmId} AND owner_user_id = #{userId} AND delete_type = 'ACCOUNT_CANCEL' "
            + "AND disabled = 1 AND disabled_at IS NULL")
    int initializeMissingRetentionStart(@Param("farmId") Long farmId, @Param("userId") Long userId,
                                        @Param("now") LocalDateTime now);
}
