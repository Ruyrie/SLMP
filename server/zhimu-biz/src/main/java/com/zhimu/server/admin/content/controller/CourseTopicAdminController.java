package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.query.ContentStatusQuery;
import com.zhimu.server.admin.content.request.CourseTopicSaveRequest;
import com.zhimu.server.admin.content.request.TopicStatusRequest;
import com.zhimu.server.admin.content.service.CourseTopicAdminService;
import com.zhimu.server.admin.content.vo.CourseTopicVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** WA-C03 专题管理：ADM-C36–C39。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class CourseTopicAdminController {

    private final CourseTopicAdminService topicService;

    /** ADM-C36 专题列表 */
    @GetMapping("/course-topics")
    @AdminPermission("WA-C03:VIEW")
    public ApiResponse<List<CourseTopicVO>> list(@Valid ContentStatusQuery query) {
        return ApiResponse.ok(topicService.list(query.getStatus()));
    }

    /** ADM-C37 新建专题 */
    @PostMapping("/course-topics")
    @Idempotent(required = true)
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<CourseTopicVO> create(@Valid @RequestBody CourseTopicSaveRequest request) {
        return new ApiResponse<>(0, "已保存专题", topicService.create(request));
    }

    /** ADM-C38 编辑专题与节次 */
    @PutMapping("/course-topics/{id:\\d+}")
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<CourseTopicVO> update(@PathVariable("id") Long id,
                                             @Valid @RequestBody CourseTopicSaveRequest request) {
        return new ApiResponse<>(0, "已保存专题", topicService.update(id, request));
    }

    /** ADM-C39 专题上架 / 下架 */
    @PostMapping("/course-topics/{id:\\d+}/status")
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<CourseTopicVO> changeStatus(@PathVariable("id") Long id,
                                                   @Valid @RequestBody TopicStatusRequest request) {
        CourseTopicVO vo = topicService.changeStatus(id, request);
        String message = "ON_SHELF".equals(vo.getStatus()) ? "已上架这个专题" : "已下架这个专题";
        return new ApiResponse<>(0, message, vo);
    }
}
