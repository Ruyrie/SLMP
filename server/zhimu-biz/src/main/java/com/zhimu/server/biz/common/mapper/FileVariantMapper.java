package com.zhimu.server.biz.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.common.entity.FileVariant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 文件派生版本（t_file_variant）：图片缩略图 THUMB、课程 HLS 与压缩版。 */
public interface FileVariantMapper extends BaseMapper<FileVariant> {

    @Select("SELECT * FROM t_file_variant WHERE file_id = #{fileId} AND variant_code = #{variantCode} LIMIT 1")
    FileVariant selectOneByCode(@Param("fileId") Long fileId, @Param("variantCode") String variantCode);

    @Delete("DELETE FROM t_file_variant WHERE file_id = #{fileId}")
    int deleteByFile(@Param("fileId") Long fileId);
}
