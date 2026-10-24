package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.query.AdminCourseQuery;
import com.zhimu.server.admin.content.request.CourseSaveRequest;
import com.zhimu.server.admin.content.request.OffShelfRequest;
import com.zhimu.server.admin.content.vo.CourseDetailVO;
import com.zhimu.server.admin.content.vo.CourseRowVO;
import com.zhimu.server.admin.content.vo.CourseVO;
import com.zhimu.server.admin.content.vo.TranscodeStateVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/**
 * 养殖课堂课程管理（ADM-C22–C35，C24/C25 已取消，上传用 ADM-S35/S36）。
 */
public interface CourseAdminService {

    PageVO<CourseRowVO> page(AdminCourseQuery query);

    CourseDetailVO detail(Long id);

    CourseVO create(CourseSaveRequest request);

    CourseVO update(Long id, CourseSaveRequest request);

    List<TranscodeStateVO> transcodeStates(String ids);

    TranscodeStateVO retryTranscode(Long id);

    CourseVO onShelf(Long id, Integer version);

    CourseVO offShelf(Long id, OffShelfRequest request);

    void deleteDraft(Long id, Integer version);
}
