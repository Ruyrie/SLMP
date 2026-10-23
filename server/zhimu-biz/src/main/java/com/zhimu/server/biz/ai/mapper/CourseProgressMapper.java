package com.zhimu.server.biz.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.ai.entity.CourseProgress;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 课程学习进度（t_course_progress）。
 */
public interface CourseProgressMapper extends BaseMapper<CourseProgress> {

    @Select("SELECT * FROM t_course_progress WHERE user_id = #{userId} AND course_id = #{courseId} LIMIT 1")
    CourseProgress selectByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select("<script>SELECT * FROM t_course_progress WHERE user_id = #{userId} AND course_id IN "
            + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<CourseProgress> selectByUserAndCourses(@Param("userId") Long userId,
                                                @Param("courseIds") List<Long> courseIds);

    @Select("SELECT * FROM t_course_progress WHERE user_id = #{userId} AND completed = 0 AND position_sec > 0 "
            + "ORDER BY last_watched_at DESC LIMIT #{limit}")
    List<CourseProgress> selectRecentIncomplete(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM t_course_progress WHERE user_id = #{userId} AND course_id IN "
            + "(SELECT id FROM t_course WHERE topic_id = #{topicId} AND status = 'ON_SHELF') "
            + "ORDER BY last_watched_at DESC")
    List<CourseProgress> selectByUserAndTopic(@Param("userId") Long userId, @Param("topicId") Long topicId);
}
