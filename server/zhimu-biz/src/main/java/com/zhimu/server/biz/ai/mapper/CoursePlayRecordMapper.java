package com.zhimu.server.biz.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.ai.entity.CoursePlayRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 课程播放记录（t_course_play_record）。
 */
public interface CoursePlayRecordMapper extends BaseMapper<CoursePlayRecord> {

    @Select("SELECT * FROM t_course_play_record WHERE user_id = #{userId} AND play_token = #{playToken} LIMIT 1")
    CoursePlayRecord selectByToken(@Param("userId") Long userId, @Param("playToken") String playToken);
}
