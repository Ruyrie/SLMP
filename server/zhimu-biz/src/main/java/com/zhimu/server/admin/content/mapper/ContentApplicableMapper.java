package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.content.entity.ContentApplicable;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 内容适用品类（t_content_applicable）：空表示全部品类。
 */
public interface ContentApplicableMapper extends BaseMapper<ContentApplicable> {

    @Select("SELECT * FROM t_content_applicable WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "ORDER BY category_id")
    List<ContentApplicable> selectByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Delete("DELETE FROM t_content_applicable WHERE biz_type = #{bizType} AND biz_id = #{bizId}")
    int deleteByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Select("SELECT COUNT(*) FROM t_content_applicable WHERE category_id = #{categoryId} AND biz_type = #{bizType}")
    long countByCategory(@Param("categoryId") Long categoryId, @Param("bizType") String bizType);
}
