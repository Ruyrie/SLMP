package com.zhimu.server.biz.feeding.controller;

import com.zhimu.server.biz.feeding.request.FeedTaskQuery;
import com.zhimu.server.biz.feeding.service.FeedTaskService;
import com.zhimu.server.biz.feeding.vo.FeedTaskVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-G01–G03：今日饲喂指标、今日任务列表、登记表单上下文。 */
@RestController
@RequestMapping("/api/v1/feed-tasks")
@RequireFarm
@RequiredArgsConstructor
public class FeedTaskController {

    private final FeedTaskService feedTaskService;

    /** G01 今日饲喂指标与横幅 */
    @GetMapping("/today/summary")
    public ApiResponse<FeedTaskVO.TodaySummary> todaySummary() {
        return ApiResponse.ok(feedTaskService.todaySummary());
    }

    /** G02 今日任务列表 */
    @GetMapping("/today")
    public ApiResponse<PageVO<FeedTaskVO.Item>> today(FeedTaskQuery query) {
        return ApiResponse.ok(feedTaskService.today(query));
    }

    /** G03 登记表单上下文 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<FeedTaskVO.Form> form(@PathVariable("id") Long taskId) {
        return ApiResponse.ok(feedTaskService.form(taskId));
    }
}
