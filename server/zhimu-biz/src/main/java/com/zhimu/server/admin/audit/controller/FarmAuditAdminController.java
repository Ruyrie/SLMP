package com.zhimu.server.admin.audit.controller;

import com.zhimu.server.admin.audit.query.FarmAuditAdminQuery;
import com.zhimu.server.admin.audit.request.AuditApproveRequest;
import com.zhimu.server.admin.audit.request.FarmAuditRejectRequest;
import com.zhimu.server.admin.audit.request.SmsResendRequest;
import com.zhimu.server.admin.audit.service.FarmAuditReviewService;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.FarmAuditDetailVO;
import com.zhimu.server.admin.audit.vo.FarmAuditPageVO;
import com.zhimu.server.admin.audit.vo.NoticeResendVO;
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

/** 养殖场注册审核（ADM-R02–R05）。 */
@RestController
@RequestMapping("/admin/v1/farm-audits")
@RequiredArgsConstructor
public class FarmAuditAdminController {

    private final FarmAuditReviewService service;

    /** ADM-R02 注册审核列表：待审核按提交时间升序，已处理按审核时间倒序。 */
    @AdminPermission("WA-R01:VIEW")
    @GetMapping
    public ApiResponse<FarmAuditPageVO> list(@Valid FarmAuditAdminQuery query) {
        return ApiResponse.ok(service.list(query));
    }

    /** ADM-R03 注册审核详情。 */
    @AdminPermission("WA-R01:VIEW")
    @GetMapping("/{id:\\d+}")
    public ApiResponse<FarmAuditDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /** ADM-R04 通过注册。 */
    @AdminPermission("WA-R01:HANDLE")
    @PostMapping("/{id:\\d+}/approve")
    public ApiResponse<AuditResultVO> approve(@PathVariable("id") Long id,
                                               @Valid @RequestBody AuditApproveRequest request) {
        AuditResultVO result = service.approve(id, request);
        String owner = result.getNotifiedNames().isEmpty() ? "所有者" : result.getNotifiedNames().get(0);
        return new ApiResponse<>(0, "已通过" + result.getTargetName() + "的注册申请，结果已发到"
                + owner + "的消息中心", result);
    }

    /** ADM-R05 驳回注册，可标记需重拍的资质图。 */
    @AdminPermission("WA-R01:HANDLE")
    @PostMapping("/{id:\\d+}/reject")
    public ApiResponse<AuditResultVO> reject(@PathVariable("id") Long id,
                                              @Valid @RequestBody FarmAuditRejectRequest request) {
        AuditResultVO result = service.reject(id, request);
        String owner = result.getNotifiedNames().isEmpty() ? "所有者" : result.getNotifiedNames().get(0);
        return new ApiResponse<>(0, "已驳回" + result.getTargetName() + "的注册申请，原因已发到"
                + owner + "的消息中心", result);
    }

    /** ADM-R18 重发审核结果通知（站内消息，不发短信）；只对已处理申请。旧路径 resend-sms 保留兼容。 */
    @AdminPermission("WA-R01:HANDLE")
    @PostMapping({"/{id:\\d+}/resend-notice", "/{id:\\d+}/resend-sms"})
    public ApiResponse<NoticeResendVO> resendNotice(@PathVariable("id") Long id,
                                                    @Valid @RequestBody(required = false) SmsResendRequest request) {
        NoticeResendVO result = service.resendNotice(id, request);
        return new ApiResponse<>(0, result.getNote(), result);
    }
}
