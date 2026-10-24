package com.zhimu.server.admin.audit.controller;

import com.zhimu.server.admin.audit.query.ChangeRequestAdminQuery;
import com.zhimu.server.admin.audit.request.AuditRejectRequest;
import com.zhimu.server.admin.audit.request.ChangeRequestApproveRequest;
import com.zhimu.server.admin.audit.service.ChangeRequestReviewService;
import com.zhimu.server.admin.audit.vo.ApproveResultVO;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.ChangeRequestAdminDetailVO;
import com.zhimu.server.admin.audit.vo.ChangeRequestAdminPageVO;
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

/** 修改申请审核（ADM-R10–R13）。 */
@RestController
@RequestMapping("/admin/v1/change-requests")
@RequiredArgsConstructor
public class ChangeRequestAdminController {

    private final ChangeRequestReviewService service;

    /** ADM-R10 修改申请队列，待审核按提交时间升序。 */
    @AdminPermission("WA-R03:VIEW")
    @GetMapping
    public ApiResponse<ChangeRequestAdminPageVO> list(@Valid ChangeRequestAdminQuery query) {
        return ApiResponse.ok(service.list(query));
    }

    /** ADM-R11 原记录、修改前后、影响与系统校验。 */
    @AdminPermission("WA-R03:VIEW")
    @GetMapping("/{id:\\d+}")
    public ApiResponse<ChangeRequestAdminDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /** ADM-R12 通过并让来源 handler 回写与重算。 */
    @AdminPermission("WA-R03:HANDLE")
    @PostMapping("/{id:\\d+}/approve")
    public ApiResponse<ApproveResultVO> approve(@PathVariable("id") Long id,
                                                 @Valid @RequestBody ChangeRequestApproveRequest request) {
        ApproveResultVO result = service.approve(id, request);
        String count = result.getRecalcCount() == null ? "" : " " + result.getRecalcCount() + " 项数据";
        String target = result.getNotifiedNames().isEmpty() ? "提交人" : result.getNotifiedNames().get(0);
        return new ApiResponse<>(0, "已通过，系统已重新计算" + count + "，结果已发到" + target + "的消息中心",
                result);
    }

    /** ADM-R13 驳回修改申请。 */
    @AdminPermission("WA-R03:HANDLE")
    @PostMapping("/{id:\\d+}/reject")
    public ApiResponse<AuditResultVO> reject(@PathVariable("id") Long id,
                                              @Valid @RequestBody AuditRejectRequest request) {
        AuditResultVO result = service.reject(id, request);
        String target = result.getNotifiedNames().isEmpty() ? "提交人" : result.getNotifiedNames().get(0);
        return new ApiResponse<>(0, "已驳回，原因已发到" + target + "的消息中心", result);
    }
}
