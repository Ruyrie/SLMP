package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.query.AdminUserQuery;
import com.zhimu.server.admin.system.request.UserStatusRequest;
import com.zhimu.server.admin.system.service.UserAdminService;
import com.zhimu.server.admin.system.vo.AdminUserDetailVO;
import com.zhimu.server.admin.system.vo.AdminUserItemVO;
import com.zhimu.server.admin.system.vo.ExportVO;
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

/** 用户账号（ADM-S10–S14）。导出需要 WA-S03:HANDLE（超管专属）。 */
@RestController
@RequestMapping("/admin/v1/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    /** ADM-S10 用户账号列表：手机号打码，不返回密码哈希。 */
    @AdminPermission("WA-S03:VIEW")
    @GetMapping
    public ApiResponse<PageVO<AdminUserItemVO>> list(@Valid AdminUserQuery query) {
        return ApiResponse.ok(userAdminService.list(query));
    }

    /** ADM-S11 账号详情。 */
    @AdminPermission("WA-S03:VIEW")
    @GetMapping("/{id:\\d+}")
    public ApiResponse<AdminUserDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(userAdminService.detail(id));
    }

    /** ADM-S12 停用账号。 */
    @AdminPermission("WA-S03:HANDLE")
    @PostMapping("/{id:\\d+}/disable")
    public ApiResponse<Void> disable(@PathVariable("id") Long id,
                                     @Valid @RequestBody UserStatusRequest request) {
        String name = userAdminService.disable(id, request);
        return new ApiResponse<>(0, "已停用" + name + "的账号", null);
    }

    /** ADM-S13 启用账号。 */
    @AdminPermission("WA-S03:HANDLE")
    @PostMapping("/{id:\\d+}/enable")
    public ApiResponse<Void> enable(@PathVariable("id") Long id,
                                    @Valid @RequestBody UserStatusRequest request) {
        String name = userAdminService.enable(id, request);
        return new ApiResponse<>(0, "已启用" + name + "的账号", null);
    }

    /** ADM-S14 导出账号：当前筛选，最多 10,000 条。 */
    @AdminPermission("WA-S03:HANDLE")
    @PostMapping("/export")
    public ApiResponse<ExportVO> export(@Valid AdminUserQuery query) {
        ExportVO result = userAdminService.export(query);
        return new ApiResponse<>(0, "已导出 " + result.getRowCount() + " 条记录", result);
    }
}
