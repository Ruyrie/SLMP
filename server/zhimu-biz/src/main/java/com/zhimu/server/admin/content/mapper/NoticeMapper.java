package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.content.entity.Notice;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 平台通知（t_notice）。 */
public interface NoticeMapper extends BaseMapper<Notice> {

    /** 含已删除草稿，用于区分 40401 与 41001 */
    @Select("SELECT * FROM t_notice WHERE id = #{id}")
    Notice selectAnyById(@Param("id") Long id);
}
