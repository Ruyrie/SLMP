package com.zhimu.server.admin.audit.controller;

import com.zhimu.server.admin.audit.query.RecoveryAdminQuery;
import com.zhimu.server.admin.audit.request.RecoveryHandleRequest;
import com.zhimu.server.admin.audit.service.RecoveryReviewService;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.RecoveryDetailVO;
import com.zhimu.server.admin.audit.vo.RecoveryItemVO;
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

/** 账号找回人工核实（ADM-R14–R17）。没有恢复码的申请不进入此队列。 */
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

    /** ADM-R16 通过：换绑手机号并发临时密码短信。 */
    @AdminPermission("WA-R04:HANDLE")
    @PostMapping("/{id:\\d+}/approve")
    public ApiResponse<AuditResultVO> approve(@PathVariable("id") Long id,
                                               @Valid @RequestBody RecoveryHandleRequest request) {
        AuditResultVO result = service.approve(id, request);
        return new ApiResponse<>(0, "已通过" + result.getTargetName() + "的找回申请，临时密码已发到"
                + result.getSmsPhone(), result);
    }

    /** ADM-R17 驳回并短信通知。 */
    @AdminPermission("WA-R04:HANDLE")
    @PostMapping("/{id:\\d+}/reject")
    public ApiResponse<AuditResultVO> reject(@PathVariable("id") Long id,
                                              @Valid @RequestBody RecoveryHandleRequest request) {
        AuditResultVO result = service.reject(id, request);
        return new ApiResponse<>(0, "已驳回，原因已以短信发到" + result.getSmsPhone(), result);
    }
}
