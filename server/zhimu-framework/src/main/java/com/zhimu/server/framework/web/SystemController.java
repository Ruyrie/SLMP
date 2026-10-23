package com.zhimu.server.framework.web;

import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.security.annotation.PublicApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 探活接口，用于冒烟测试。
 */
@PublicApi
@RestController
@RequestMapping("/api/v1")
public class SystemController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", BeijingTime.format(BeijingTime.now()));
        data.put("trace_id", TraceContext.get());
        return ApiResponse.ok(data);
    }
}
