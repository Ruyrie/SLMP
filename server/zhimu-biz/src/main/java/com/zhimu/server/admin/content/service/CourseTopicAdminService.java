package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.request.CourseTopicSaveRequest;
import com.zhimu.server.admin.content.request.TopicStatusRequest;
import com.zhimu.server.admin.content.vo.CourseTopicVO;

import java.util.List;

/** 课程专题（ADM-C36–C39）。 */
public interface CourseTopicAdminService {

    List<CourseTopicVO> list(String status);

    CourseTopicVO create(CourseTopicSaveRequest request);

    CourseTopicVO update(Long id, CourseTopicSaveRequest request);

    CourseTopicVO changeStatus(Long id, TopicStatusRequest request);
}
