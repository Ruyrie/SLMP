package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.query.OperationLogQuery;
import com.zhimu.server.admin.system.service.OperationLogAdminService;
import com.zhimu.server.admin.system.vo.OperationLogDetailVO;
import com.zhimu.server.admin.system.vo.OperationLogItemVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 操作日志（ADM-S30、S31）：查询与详情都需要 WA-S06:VIEW，按查看人权限过滤。 */
@RestController
@RequestMapping("/admin/v1/operation-logs")
@RequiredArgsConstructor
public class OperationLogAdminController {

    private final OperationLogAdminService operationLogAdminService;

    /** ADM-S30 操作日志列表：默认近 7 天含今天，跨度 ≤366 天，按时间倒序 */
    @AdminPermission("WA-S06:VIEW")
    @GetMapping
    public ApiResponse<PageVO<OperationLogItemVO>> list(@Valid OperationLogQuery query) {
        return ApiResponse.ok(operationLogAdminService.list(query));
    }

    /** ADM-S31 日志详情：不可见的日志按不存在处理（40401） */
    @AdminPermission("WA-S06:VIEW")
    @GetMapping("/{id:\\d+}")
    public ApiResponse<OperationLogDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(operationLogAdminService.detail(id));
    }
}
