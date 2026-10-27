package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.service.BatchTraceService;
import com.zhimu.server.biz.herd.vo.BatchTraceVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.PublicApi;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BatchTraceController {
    private final BatchTraceService service;

    @PublicApi
    @GetMapping("/api/v1/public/batch-traces/{token}")
    public ApiResponse<BatchTraceVO> read(@PathVariable String token, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.read(token));
    }

    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @PutMapping("/api/v1/batches/{id:\\d+}/trace-settings")
    public ApiResponse<BatchTraceService.Settings> settings(@PathVariable Long id, @RequestBody BatchTraceService.Settings request) {
        return ApiResponse.ok(service.settings(id, request));
    }
}
