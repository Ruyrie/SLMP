package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.vo.ContentExportVO;
import com.zhimu.server.admin.content.vo.CourseStatsVO;
import com.zhimu.server.admin.content.vo.CourseSummaryVO;

/**
 * 课程指标卡、课程数据与导出（ADM-C22、C34、C35）。
 */
public interface CourseStatService {

    CourseSummaryVO summary();

    CourseStatsVO stats(Long courseId);

    ContentExportVO export(Long courseId);
}
