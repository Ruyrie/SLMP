package com.zhimu.server.biz.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.ai.entity.CourseMedia;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 课程媒体（t_course_media）。
 */
public interface CourseMediaMapper extends BaseMapper<CourseMedia> {

    @Select("SELECT * FROM t_course_media WHERE course_id = #{courseId} AND status = 'SUCCESS' ORDER BY sort_no, id")
    List<CourseMedia> selectPlayable(@Param("courseId") Long courseId);

    @Select("<script>SELECT * FROM t_course_media WHERE status = 'SUCCESS' AND course_id IN "
            + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY course_id, sort_no, id</script>")
    List<CourseMedia> selectPlayableByCourseIds(@Param("courseIds") List<Long> courseIds);
}
