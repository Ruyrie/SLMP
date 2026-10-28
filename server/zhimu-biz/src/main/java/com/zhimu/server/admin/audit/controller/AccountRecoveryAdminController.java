package com.zhimu.server.admin.audit.controller;

import com.zhimu.server.admin.audit.query.RecoveryAdminQuery;
import com.zhimu.server.admin.audit.request.RecoveryHandleRequest;
import com.zhimu.server.admin.audit.request.RecoveryProxyRequest;
import com.zhimu.server.admin.audit.request.SmsResendRequest;
import com.zhimu.server.admin.audit.service.RecoveryReviewService;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.ProxyRecoveryVO;
import com.zhimu.server.admin.audit.vo.RecoveryDetailVO;
import com.zhimu.server.admin.audit.vo.RecoveryItemVO;
import com.zhimu.server.admin.audit.vo.RecoveryVerificationRecordsVO;
import com.zhimu.server.admin.audit.vo.RecoveryTempPasswordVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 账号找回人工核实（ADM-R14–R18、R20–R21）。没有恢复码的申请不进入此队列。 */
@RestController
@RequestMapping("/admin/v1/account-recoveries")
@RequiredArgsConstructor
public class AccountRecoveryAdminController {

    private final RecoveryReviewService service;

    /** ADM-R14 找回核实队列：只含恢复码已核对正确的申请。 */
    @AdminPermission("WA-R04:VIEW")
    @GetMapping
    public ApiResponse<PageVO<RecoveryItemVO>> list(@Valid RecoveryAdminQuery query) {
        return ApiResponse.ok(service.list(query));
    }

    /** ADM-R15 核实与处理详情。 */
    @AdminPermission("WA-R04:VIEW")
    @GetMapping("/{id:\\d+}")
    public ApiResponse<RecoveryDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /**
     * ADM-R16 通过：换绑手机号并生成临时密码。平台没有通知类短信模板，临时密码只在本次响应里返回一次，
     * 由管理员线下告知用户。
     */
    @AdminPermission("WA-R04:HANDLE")
    @PostMapping("/{id:\\d+}/approve")
    public ApiResponse<AuditResultVO> approve(@PathVariable("id") Long id,
                                               @Valid @RequestBody RecoveryHandleRequest request) {
        AuditResultVO result = service.approve(id, request);
        return new ApiResponse<>(0, "已通过" + result.getTargetName() + "的找回申请，请把临时密码线下告知用户", result);
    }

    /** ADM-R17 驳回：原因写入账号的消息中心，不发短信。 */
    @AdminPermission("WA-R04:HANDLE")
    @PostMapping("/{id:\\d+}/reject")
    public ApiResponse<AuditResultVO> reject(@PathVariable("id") Long id,
                                              @Valid @RequestBody RecoveryHandleRequest request) {
        AuditResultVO result = service.reject(id, request);
        return new ApiResponse<>(0, "已驳回" + result.getTargetName() + "的找回申请，原因已发到账号的消息中心", result);
    }

    /**
     * ADM-R18 重新生成临时密码：只对已通过申请；旧临时密码立即失效，新密码只在响应里返回一次。
     * 旧路径 resend-sms 保留兼容。
     */
    @AdminPermission("WA-R04:HANDLE")
    @PostMapping({"/{id:\\d+}/temp-password", "/{id:\\d+}/resend-sms"})
    public ApiResponse<RecoveryTempPasswordVO> regenerateTempPassword(
            @PathVariable("id") Long id, @Valid @RequestBody(required = false) SmsResendRequest request) {
        RecoveryTempPasswordVO result = service.regenerateTempPassword(id, request);
        return new ApiResponse<>(0, "已重新生成临时密码，请线下告知用户", result);
    }

    /** ADM-R21 恢复码校验记录（次数、时间、结果、IP/设备）；无记录返回空数组。 */
    @AdminPermission("WA-R04:VIEW")
    @GetMapping("/{id:\\d+}/verification-records")
    public ApiResponse<RecoveryVerificationRecordsVO> verificationRecords(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.verificationRecords(id));
    }

    /** ADM-R20 管理员代用户登记找回申请。 */
    @AdminPermission("WA-R04:HANDLE")
    @PostMapping("/proxy")
    public ApiResponse<ProxyRecoveryVO> proxy(@Valid @RequestBody RecoveryProxyRequest request) {
        ProxyRecoveryVO result = service.proxy(request);
        return new ApiResponse<>(0, "已代登记" + result.getUserName() + "的找回申请，等待核实", result);
    }
}
