package com.zhimu.server.admin.audit.controller;

import com.zhimu.server.admin.audit.service.AuditPendingService;
import com.zhimu.server.admin.audit.vo.PendingCountVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.security.annotation.NoSessionRenew;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 四类待审核数（ADM-R01）：菜单角标与平台概览；轮询接口不续期会话。 */
@RestController
@RequestMapping("/admin/v1/audit")
@RequiredArgsConstructor
public class AuditPendingAdminController {

    private final AuditPendingService service;

    @NoSessionRenew
    @GetMapping("/pending-counts")
    public ApiResponse<PendingCountVO> pendingCounts() {
        return ApiResponse.ok(service.pendingCounts());
    }
}
