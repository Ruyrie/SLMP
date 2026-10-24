package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.query.AdminCourseQuery;
import com.zhimu.server.admin.content.request.CourseSaveRequest;
import com.zhimu.server.admin.content.request.OffShelfRequest;
import com.zhimu.server.admin.content.request.VersionRequest;
import com.zhimu.server.admin.content.service.CourseAdminService;
import com.zhimu.server.admin.content.service.CourseStatService;
import com.zhimu.server.admin.content.vo.ContentExportVO;
import com.zhimu.server.admin.content.vo.CourseDetailVO;
import com.zhimu.server.admin.content.vo.CourseRowVO;
import com.zhimu.server.admin.content.vo.CourseStatsVO;
import com.zhimu.server.admin.content.vo.CourseSummaryVO;
import com.zhimu.server.admin.content.vo.CourseVO;
import com.zhimu.server.admin.content.vo.TranscodeStateVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** WA-C03 养殖课堂管理：ADM-C22–C35。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class CourseAdminController {

    private final CourseAdminService courseService;
    private final CourseStatService statService;

    /** ADM-C22 课程指标卡与导航计数 */
    @GetMapping("/courses/summary")
    @AdminPermission("WA-C03:VIEW")
    public ApiResponse<CourseSummaryVO> summary() {
        return ApiResponse.ok(statService.summary());
    }

    /** ADM-C23 课程列表与分段计数 */
    @GetMapping("/courses")
    @AdminPermission({"WA-C03:VIEW", "WA-C04:VIEW"})
    public ApiResponse<PageVO<CourseRowVO>> list(@Valid AdminCourseQuery query) {
        return ApiResponse.ok(courseService.page(query));
    }

    /** ADM-C26 新建课程（草稿 / 上架） */
    @PostMapping("/courses")
    @Idempotent(required = true)
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<CourseVO> create(@Valid @RequestBody CourseSaveRequest request) {
        CourseVO vo = courseService.create(request);
        String message = "ON_SHELF".equals(vo.getStatus()) ? "已上架，用户端立即可见" : "已保存草稿";
        return new ApiResponse<>(0, message, vo);
    }

    /** ADM-C27 编辑课程 */
    @PutMapping("/courses/{id:\\d+}")
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<CourseVO> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody CourseSaveRequest request) {
        CourseVO vo = courseService.update(id, request);
        String message = "ON_SHELF".equals(vo.getStatus()) ? "已上架，用户端立即可见" : "已保存";
        return new ApiResponse<>(0, message, vo);
    }

    /** ADM-C28 课程详情与候选封面 */
    @GetMapping("/courses/{id:\\d+}")
    @AdminPermission("WA-C03:VIEW")
    public ApiResponse<CourseDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(courseService.detail(id));
    }

    /** ADM-C29 批量查询转码进度 */
    @GetMapping("/courses/transcode-status")
    @AdminPermission("WA-C03:VIEW")
    public ApiResponse<List<TranscodeStateVO>> transcodeStates(@RequestParam("ids") String ids) {
        return ApiResponse.ok(courseService.transcodeStates(ids));
    }

    /** ADM-C30 重试转码 */
    @PostMapping("/courses/{id:\\d+}/transcode/retry")
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<TranscodeStateVO> retryTranscode(@PathVariable("id") Long id) {
        return new ApiResponse<>(0, "已重新开始转码", courseService.retryTranscode(id));
    }

    /** ADM-C31 上架 */
    @PostMapping("/courses/{id:\\d+}/on-shelf")
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<CourseVO> onShelf(@PathVariable("id") Long id,
                                         @Valid @RequestBody VersionRequest request) {
        return new ApiResponse<>(0, "已上架，用户端立即可见", courseService.onShelf(id, request.getVersion()));
    }

    /** ADM-C32 下架 */
    @PostMapping("/courses/{id:\\d+}/off-shelf")
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<CourseVO> offShelf(@PathVariable("id") Long id,
                                          @Valid @RequestBody OffShelfRequest request) {
        return new ApiResponse<>(0, "已下架这个课程", courseService.offShelf(id, request));
    }

    /** ADM-C33 删除草稿 */
    @DeleteMapping("/courses/{id:\\d+}")
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<Void> deleteDraft(@PathVariable("id") Long id,
                                         @RequestParam("version") Integer version) {
        courseService.deleteDraft(id, version);
        return ApiResponse.ok();
    }

    /** ADM-C34 课程数据 */
    @GetMapping("/courses/{id:\\d+}/stats")
    @AdminPermission("WA-C03:VIEW")
    public ApiResponse<CourseStatsVO> stats(@PathVariable("id") Long id) {
        return ApiResponse.ok(statService.stats(id));
    }

    /** ADM-C35 导出课程数据 */
    @PostMapping("/courses/{id:\\d+}/stats/export")
    @AdminPermission("WA-C03:HANDLE")
    public ApiResponse<ContentExportVO> exportStats(@PathVariable("id") Long id) {
        return new ApiResponse<>(0, "已开始导出，完成后在浏览器下载", statService.export(id));
    }
}
