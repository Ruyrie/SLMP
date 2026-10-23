package com.zhimu.server.biz.feeding.controller;

import com.zhimu.server.biz.feeding.request.FeedExecQuery;
import com.zhimu.server.biz.feeding.service.FeedExecutionService;
import com.zhimu.server.biz.feeding.vo.FeedExecVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-G14–G16：执行情况汇总、需要关注、单日各餐明细。 */
@RestController
@RequestMapping("/api/v1/feed-executions")
@RequireFarm
@RequiredArgsConstructor
public class FeedExecutionController {

    private final FeedExecutionService feedExecutionService;

    /** G14 执行情况汇总 */
    @GetMapping("/summary")
    public ApiResponse<FeedExecVO.Summary> summary(FeedExecQuery query) {
        return ApiResponse.ok(feedExecutionService.summary(query));
    }

    /** G15 需要关注 */
    @GetMapping("/attention")
    public ApiResponse<PageVO<FeedExecVO.AttentionItem>> attention(
            FeedExecQuery query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(feedExecutionService.attention(query, page, size));
    }

    /** G16 单日各餐明细 */
    @GetMapping("/daily")
    public ApiResponse<FeedExecVO.Day> daily(FeedExecQuery query) {
        return ApiResponse.ok(feedExecutionService.day(query));
    }
}
