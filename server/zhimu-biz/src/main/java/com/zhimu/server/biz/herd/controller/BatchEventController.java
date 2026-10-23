package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.service.BatchService;
import com.zhimu.server.biz.herd.vo.BatchEventDetailVO;
import com.zhimu.server.biz.herd.vo.RequestCheckVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-L19 存栏变动记录详情、API-L26 按请求编号查询。 */
@RestController
@RequestMapping("/api/v1/batch-events")
@RequiredArgsConstructor
@RequireFarm
public class BatchEventController {

    private final BatchService batchService;

    /** L26 按请求编号查询（只查本人提交） */
    @GetMapping("/by-request/{request_key}")
    public ApiResponse<RequestCheckVO> checkByRequest(@PathVariable("request_key") String requestKey) {
        return ApiResponse.ok(batchService.checkByRequest(requestKey));
    }

    /** L19 存栏变动记录详情 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<BatchEventDetailVO> detail(@PathVariable("id") Long eventId) {
        return ApiResponse.ok(batchService.eventDetail(eventId));
    }
}
