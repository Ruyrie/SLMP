package com.zhimu.server.biz.health.controller;

import com.zhimu.server.biz.health.service.HealthRequestService;
import com.zhimu.server.biz.health.vo.HealthRequestStatusVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-D41 按请求编号查询登记结果。 */
@RestController
@RequestMapping("/api/v1")
@RequireFarm
@RequiredArgsConstructor
public class HealthRequestController {

    private final HealthRequestService healthRequestService;

    @GetMapping("/health-records/by-request/{client_request_id}")
    public ApiResponse<HealthRequestStatusVO> findByRequest(
            @PathVariable("client_request_id") String clientRequestId,
            @RequestParam(value = "record_type", required = false) String recordType) {
        return ApiResponse.ok(healthRequestService.findByRequest(clientRequestId, recordType));
    }
}
