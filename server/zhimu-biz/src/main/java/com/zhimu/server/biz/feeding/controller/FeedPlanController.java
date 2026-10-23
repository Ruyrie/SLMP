package com.zhimu.server.biz.feeding.controller;

import com.zhimu.server.biz.feeding.request.FeedPlanSaveRequest;
import com.zhimu.server.biz.feeding.service.FeedPlanService;
import com.zhimu.server.biz.feeding.vo.FeedPlanVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-G09–G12：方案批次选择、饲喂方案、套用品种标准、保存方案。 */
@RestController
@RequireFarm
@RequiredArgsConstructor
public class FeedPlanController {

    private final FeedPlanService feedPlanService;

    /** G09 方案批次选择 */
    @GetMapping("/api/v1/feed-plans/batch-options")
    public ApiResponse<List<FeedPlanVO.BatchOption>> batchOptions() {
        return ApiResponse.ok(feedPlanService.batchOptions());
    }

    /** G10 饲喂方案 */
    @GetMapping("/api/v1/batches/{batchId:\\d+}/feed-plan")
    public ApiResponse<FeedPlanVO.Plan> plan(@PathVariable("batchId") Long batchId) {
        return ApiResponse.ok(feedPlanService.getPlan(batchId));
    }

    /** G11 套用品种标准（只读） */
    @GetMapping("/api/v1/batches/{batchId:\\d+}/feed-plan/standard")
    public ApiResponse<FeedPlanVO.StdApply> standard(@PathVariable("batchId") Long batchId) {
        return ApiResponse.ok(feedPlanService.getStandard(batchId));
    }

    /** G12 保存方案 */
    @PutMapping("/api/v1/batches/{batchId:\\d+}/feed-plan")
    public ApiResponse<FeedPlanVO.Plan> save(@PathVariable("batchId") Long batchId,
                                             @RequestBody FeedPlanSaveRequest request) {
        return new ApiResponse<>(0, "已保存饲喂方案", feedPlanService.save(batchId, request));
    }
}
