package com.zhimu.server.biz.ai.controller;

import com.zhimu.server.biz.ai.query.CourseQuery;
import com.zhimu.server.biz.ai.query.TopicQuery;
import com.zhimu.server.biz.ai.request.CourseProgressRequest;
import com.zhimu.server.biz.ai.service.CourseService;
import com.zhimu.server.biz.ai.vo.ContinueWatchingVO;
import com.zhimu.server.biz.ai.vo.CourseCardVO;
import com.zhimu.server.biz.ai.vo.CourseCategoryVO;
import com.zhimu.server.biz.ai.vo.CoursePlayVO;
import com.zhimu.server.biz.ai.vo.ProgressResultVO;
import com.zhimu.server.biz.ai.vo.TopicDetailVO;
import com.zhimu.server.biz.ai.vo.TopicVO;
import com.zhimu.server.biz.ai.vo.WatchHistoryVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API-K14–K21：课程分类、继续观看、专题、课程列表与播放、进度上报、观看记录。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/course-categories")
    public ApiResponse<List<CourseCategoryVO>> categories() {
        return ApiResponse.ok(courseService.categories());
    }

    @GetMapping("/courses/continue")
    public ApiResponse<ContinueWatchingVO> continueWatching() {
        return ApiResponse.ok(courseService.continueWatching(UserContext.requireUserId()));
    }

    @GetMapping("/course-topics")
    public ApiResponse<PageVO<TopicVO>> topics(TopicQuery query) {
        return ApiResponse.ok(courseService.topics(UserContext.requireUserId(), query));
    }

    @GetMapping("/course-topics/{id:\\d+}")
    public ApiResponse<TopicDetailVO> topic(@PathVariable("id") Long id) {
        return ApiResponse.ok(courseService.topicDetail(UserContext.requireUserId(), id));
    }

    @GetMapping("/courses")
    public ApiResponse<PageVO<CourseCardVO>> courses(CourseQuery query) {
        return ApiResponse.ok(courseService.courses(UserContext.requireUserId(), query));
    }

    @GetMapping("/courses/{id:\\d+}")
    public ApiResponse<CoursePlayVO> coursePlay(@PathVariable("id") Long id) {
        return ApiResponse.ok(courseService.coursePlay(UserContext.requireUserId(), id));
    }

    @PostMapping("/courses/{id:\\d+}/progress")
    public ApiResponse<ProgressResultVO> reportProgress(@PathVariable("id") Long id,
                                                        @RequestBody CourseProgressRequest request) {
        return ApiResponse.ok(courseService.reportProgress(UserContext.requireUserId(), id, request));
    }

    @GetMapping("/course-progress")
    public ApiResponse<PageVO<WatchHistoryVO>> history(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(courseService.history(UserContext.requireUserId(), page, size));
    }
}
