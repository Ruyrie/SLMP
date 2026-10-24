package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.content.entity.CourseStatDaily;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 课程每日统计（t_course_stat_daily）。
 */
public interface CourseStatDailyMapper extends BaseMapper<CourseStatDaily> {

    @Select("SELECT * FROM t_course_stat_daily WHERE course_id = #{courseId} AND stat_date = #{date} LIMIT 1")
    CourseStatDaily selectByCourseAndDate(@Param("courseId") Long courseId, @Param("date") LocalDate date);
}
