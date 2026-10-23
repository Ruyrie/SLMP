package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.query.AdminListQuery;
import com.zhimu.server.admin.system.request.AdminCreateRequest;
import com.zhimu.server.admin.system.request.AdminResetPasswordRequest;
import com.zhimu.server.admin.system.request.AdminStatusRequest;
import com.zhimu.server.admin.system.request.AdminUpdateRequest;
import com.zhimu.server.admin.system.request.RolePermissionSaveRequest;
import com.zhimu.server.admin.system.service.AdminAccountService;
import com.zhimu.server.admin.system.service.AdminRoleService;
import com.zhimu.server.admin.system.support.AdminDevSupport;
import com.zhimu.server.admin.system.vo.AdminCreatedVO;
import com.zhimu.server.admin.system.vo.AdminItemVO;
import com.zhimu.server.admin.system.vo.PermissionSavedVO;
import com.zhimu.server.admin.system.vo.RoleItemVO;
import com.zhimu.server.admin.system.vo.RolePermissionVO;
import com.zhimu.server.admin.system.vo.TempPasswordVO;
import com.zhimu.server.admin.system.vo.UsernameCheckVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员与权限（ADM-S20–S29）。除 S21 外均要求 WA-S05 权限点（超管专属）；
 * 新增与重置密码只在 dev profile 的响应里带 temp_password，生产不返回。
 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class AdminRbacController {

    private final AdminAccountService adminAccountService;
    private final AdminRoleService adminRoleService;
    private final AdminDevSupport adminDevSupport;

    /** ADM-S20 管理员列表 */
    @AdminPermission("WA-S05:VIEW")
    @GetMapping("/admins")
    public ApiResponse<PageVO<AdminItemVO>> listAdmins(@Valid AdminListQuery query) {
        return ApiResponse.ok(adminAccountService.list(query));
    }

    /** ADM-S21 登录账号查重（包含已停用账号） */
    @AdminPermission("WA-S05:HANDLE")
    @GetMapping("/admins/username-check")
    public ApiResponse<UsernameCheckVO> usernameCheck(@RequestParam("username") String username) {
        return ApiResponse.ok(new UsernameCheckVO(adminAccountService.usernameAvailable(username)));
    }

    /** ADM-S22 新增管理员：初始密码 10 位、24 小时有效、首次登录强制修改 */
    @Idempotent
    @AdminPermission("WA-S05:HANDLE")
    @PostMapping("/admins")
    public ApiResponse<AdminCreatedVO> create(@Valid @RequestBody AdminCreateRequest request) {
        AdminAccountService.AdminCreateResult result = adminAccountService.create(request);
        AdminCreatedVO data = new AdminCreatedVO(result.adminId(),
                adminDevSupport.exposeTempPassword() ? result.tempPassword() : null);
        return new ApiResponse<>(0,
                "已创建" + result.name() + "的管理员账号，初始密码已发到 " + result.phone(), data);
    }

    /** ADM-S23 编辑管理员：username 不可改；本人只能改姓名和手机号 */
    @AdminPermission("WA-S05:HANDLE")
    @PutMapping("/admins/{id:\\d+}")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminUpdateRequest request) {
        adminAccountService.update(id, request);
        return ApiResponse.ok();
    }

    /** ADM-S24 重置密码：同时解锁，不能重置自己 */
    @AdminPermission("WA-S05:HANDLE")
    @PostMapping("/admins/{id:\\d+}/reset-password")
    public ApiResponse<TempPasswordVO> resetPassword(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) AdminResetPasswordRequest request) {
        AdminAccountService.AdminResetResult result = adminAccountService.resetPassword(id,
                request == null ? new AdminResetPasswordRequest(null) : request);
        TempPasswordVO data = adminDevSupport.exposeTempPassword()
                ? new TempPasswordVO(result.tempPassword()) : null;
        return new ApiResponse<>(0,
                "已重置" + result.name() + "的密码，新密码已发到 " + result.phone(), data);
    }

    /** ADM-S25 停用管理员：不能停用自己，停用后至少保留 1 个正常超管 */
    @AdminPermission("WA-S05:HANDLE")
    @PostMapping("/admins/{id:\\d+}/disable")
    public ApiResponse<Void> disable(@PathVariable("id") Long id,
                                     @Valid @RequestBody AdminStatusRequest request) {
        String name = adminAccountService.disable(id, request);
        return new ApiResponse<>(0, "已停用" + name + "的管理员账号", null);
    }

    /** ADM-S26 启用管理员 */
    @AdminPermission("WA-S05:HANDLE")
    @PostMapping("/admins/{id:\\d+}/enable")
    public ApiResponse<Void> enable(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminStatusRequest request) {
        String name = adminAccountService.enable(id, request);
        return new ApiResponse<>(0, "已启用" + name + "的管理员账号", null);
    }

    /** ADM-S27 角色列表 */
    @AdminPermission("WA-S05:VIEW")
    @GetMapping("/admin-roles")
    public ApiResponse<List<RoleItemVO>> listRoles() {
        return ApiResponse.ok(adminRoleService.list());
    }

    /** ADM-S28 角色权限表 */
    @AdminPermission("WA-S05:VIEW")
    @GetMapping("/admin-roles/{id:\\d+}/permissions")
    public ApiResponse<RolePermissionVO> rolePermissions(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminRoleService.permissions(id));
    }

    /** ADM-S29 保存角色权限：提交后缓存失效，该角色管理员下一次请求即生效 */
    @AdminPermission("WA-S05:HANDLE")
    @PutMapping("/admin-roles/{id:\\d+}/permissions")
    public ApiResponse<PermissionSavedVO> savePermissions(@PathVariable("id") Long id,
                                                          @Valid @RequestBody RolePermissionSaveRequest request) {
        AdminRoleService.SavedResult result = adminRoleService.savePermissions(id, request);
        return new ApiResponse<>(0,
                "已保存" + result.roleName() + "的权限，" + result.affectedAdminCount() + " 名管理员立即生效",
                new PermissionSavedVO(result.affectedAdminCount()));
    }
}
