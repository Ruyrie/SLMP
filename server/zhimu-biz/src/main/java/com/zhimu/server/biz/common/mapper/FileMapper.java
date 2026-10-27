package com.zhimu.server.biz.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.common.entity.FileEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 文件（t_file）。逻辑删除由 @TableLogic 处理；绑定状态单独更新，避免整行覆盖。
 */
public interface FileMapper extends BaseMapper<FileEntity> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM t_file WHERE id=#{id} AND deleted=0 FOR UPDATE")
    FileEntity selectActiveForUpdate(@Param("id") Long id);

    @Update("UPDATE t_file SET bound = #{bound}, updated_at = #{now} WHERE id = #{id} AND deleted = 0")
    int updateBound(@Param("id") Long id, @Param("bound") int bound, @Param("now") LocalDateTime now);
}
