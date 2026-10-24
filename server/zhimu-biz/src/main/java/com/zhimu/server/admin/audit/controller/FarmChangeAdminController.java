package com.zhimu.server.admin.audit.controller;

import com.zhimu.server.admin.audit.query.FarmChangeAdminQuery;
import com.zhimu.server.admin.audit.request.AuditApproveRequest;
import com.zhimu.server.admin.audit.request.AuditRejectRequest;
import com.zhimu.server.admin.audit.service.FarmChangeReviewService;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.FarmChangeDetailVO;
import com.zhimu.server.admin.audit.vo.FarmChangePageVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 资料变更审核（ADM-R06–R09）。 */
@RestController
@RequestMapping("/admin/v1/farm-changes")
@RequiredArgsConstructor
public class FarmChangeAdminController {

    private final FarmChangeReviewService service;

    /** ADM-R06 资料变更列表与指标卡。 */
    @AdminPermission("WA-R02:VIEW")
    @GetMapping
    public ApiResponse<FarmChangePageVO> list(@Valid FarmChangeAdminQuery query) {
        return ApiResponse.ok(service.list(query));
    }

    /** ADM-R07 变更对比详情。 */
    @AdminPermission("WA-R02:VIEW")
    @GetMapping("/{id:\\d+}")
    public ApiResponse<FarmChangeDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /** ADM-R08 通过资料变更并生效。 */
    @AdminPermission("WA-R02:HANDLE")
    @PostMapping("/{id:\\d+}/approve")
    public ApiResponse<AuditResultVO> approve(@PathVariable("id") Long id,
                                               @Valid @RequestBody AuditApproveRequest request) {
        AuditResultVO result = service.approve(id, request);
        return new ApiResponse<>(0, "已通过" + result.getTargetName() + "的资料变更，新信息已生效", result);
    }

    /** ADM-R09 驳回资料变更。 */
    @AdminPermission("WA-R02:HANDLE")
    @PostMapping("/{id:\\d+}/reject")
    public ApiResponse<AuditResultVO> reject(@PathVariable("id") Long id,
                                              @Valid @RequestBody AuditRejectRequest request) {
        AuditResultVO result = service.reject(id, request);
        String owner = result.getNotifiedNames().isEmpty() ? "所有者" : result.getNotifiedNames().get(0);
        return new ApiResponse<>(0, "已驳回" + result.getTargetName() + "的资料变更，原因已发到"
                + owner + "的消息中心", result);
    }
}
