package com.zhimu.server.biz.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.common.entity.BizMedia;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务记录与文件关联（t_biz_media）。唯一键不含 deleted，绑定前用 {@link #selectIncludingDeleted} 查已删除的行并复活。
 */
public interface BizMediaMapper extends BaseMapper<BizMedia> {

    @Select("SELECT * FROM t_biz_media WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "AND file_id = #{fileId} LIMIT 1")
    BizMedia selectIncludingDeleted(@Param("bizType") String bizType, @Param("bizId") Long bizId,
                                    @Param("fileId") Long fileId);

    @Select("SELECT * FROM t_biz_media WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND deleted = 0 "
            + "ORDER BY sort_no, id")
    List<BizMedia> selectActiveByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Select("SELECT * FROM t_biz_media WHERE file_id = #{fileId} AND deleted = 0 ORDER BY id")
    List<BizMedia> selectActiveByFile(@Param("fileId") Long fileId);

    @Select("<script>SELECT * FROM t_biz_media WHERE biz_type = #{bizType} AND deleted = 0 "
            + "AND biz_id IN <foreach collection='bizIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY biz_id, sort_no, id</script>")
    List<BizMedia> selectActiveByBizIds(@Param("bizType") String bizType, @Param("bizIds") List<Long> bizIds);

    @Select("SELECT COUNT(*) FROM t_biz_media WHERE file_id = #{fileId} AND deleted = 0")
    long countActiveByFile(@Param("fileId") Long fileId);

    @Update("UPDATE t_biz_media SET deleted = 0, sort_no = #{sortNo}, farm_id = #{farmId}, "
            + "updated_at = #{now} WHERE id = #{id}")
    int revive(@Param("id") Long id, @Param("sortNo") Integer sortNo, @Param("farmId") Long farmId,
               @Param("now") LocalDateTime now);

    @Update("<script>UPDATE t_biz_media SET deleted = 1, updated_at = #{now} WHERE biz_type = #{bizType} "
            + "AND biz_id = #{bizId} AND deleted = 0 AND file_id NOT IN "
            + "<foreach collection='keepFileIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteNotIn(@Param("bizType") String bizType, @Param("bizId") Long bizId,
                    @Param("keepFileIds") List<Long> keepFileIds, @Param("now") LocalDateTime now);

    @Update("UPDATE t_biz_media SET deleted = 1, updated_at = #{now} WHERE biz_type = #{bizType} "
            + "AND biz_id = #{bizId} AND deleted = 0")
    int deleteByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId,
                    @Param("now") LocalDateTime now);

    @Update("UPDATE t_biz_media SET source_biz_type = #{sourceBizType}, source_biz_id = #{sourceBizId}, "
            + "updated_at = #{now} WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND deleted = 0")
    int markSource(@Param("bizType") String bizType, @Param("bizId") Long bizId,
                   @Param("sourceBizType") String sourceBizType, @Param("sourceBizId") Long sourceBizId,
                   @Param("now") LocalDateTime now);
}
