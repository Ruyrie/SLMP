package com.zhimu.server.admin.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.enums.AdminStatus;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.admin.system.query.AdminListQuery;
import com.zhimu.server.admin.system.request.AdminCreateRequest;
import com.zhimu.server.admin.system.request.AdminResetPasswordRequest;
import com.zhimu.server.admin.system.request.AdminStatusRequest;
import com.zhimu.server.admin.system.request.AdminUpdateRequest;
import com.zhimu.server.admin.system.service.AdminAccountService;
import com.zhimu.server.admin.system.support.AdminDiffs;
import com.zhimu.server.admin.system.support.AdminPasswords;
import com.zhimu.server.admin.system.support.AdminValidation;
import com.zhimu.server.admin.system.vo.AdminItemVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.AdminRoleCode;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import com.zhimu.server.framework.security.session.AdminSessionService;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理员维护（ADM-S20–S26）。
 * <p>
 * 保护规则：不能停用、重置自己；不能改自己的角色；停用或改角色后至少保留 1 个正常超级管理员
 * （事务内先锁超级管理员角色行再统计）；version 不一致返回 40902；账号/手机号重复返回 40905。
 * 初始/重置密码 10 位、24 小时有效、首次登录强制修改；本批只生成密码，不发送短信、不打印。
 */
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {

    public static final int TEMP_PASSWORD_HOURS = 24;

    private static final String MSG_USERNAME_TAKEN = "这个账号名已被使用";
    private static final String MSG_PHONE_TAKEN = "这个手机号已被其他管理员使用";
    private static final String MSG_VERSION_CONFLICT = "内容已更新，请刷新后再操作";
    private static final String MSG_KEEP_SUPER = "平台至少要保留 1 个正常的超级管理员";
    private static final String MSG_NOT_FOUND = "记录不存在或无权查看";

    private final AdminMapper adminMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final AdminSessionService adminSessionService;

    @Override
    public PageVO<AdminItemVO> list(AdminListQuery query) {
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        IPage<Admin> result = adminMapper.selectPage(new Page<>(page, size),
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Admin>lambdaQuery()
                        .orderByAsc(Admin::getId));
        Map<Long, AdminRole> roles = new HashMap<>();
        for (AdminRole role : adminRoleMapper.selectList(null)) {
            roles.put(role.getId(), role);
        }
        Long currentAdminId = AdminContext.adminId();
        LocalDateTime now = BeijingTime.now();
        List<AdminItemVO> list = new ArrayList<>(result.getRecords().size());
        for (Admin admin : result.getRecords()) {
            AdminRole role = roles.get(admin.getRoleId());
            boolean self = admin.getId().equals(currentAdminId);
            boolean locked = admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(now);
            String roleCode = role == null ? null : role.getRoleCode();
            list.add(AdminItemVO.builder()
                    .adminId(admin.getId())
                    .name(admin.getName())
                    .isSelf(self)
                    .username(admin.getUsername())
                    .roleId(admin.getRoleId())
                    .roleCode(roleCode)
                    .roleName(role == null ? null : role.getRoleName())
                    .phone(Masks.phone(admin.getPhone()))
                    .createdAt(admin.getCreatedAt())
                    .lastLoginAt(admin.getLastLoginAt())
                    .status(admin.getStatus())
                    .statusText(CodeEnum.textOf(AdminStatus.class, admin.getStatus()))
                    .locked(locked)
                    .lockedUntil(admin.getLockedUntil())
                    .version(admin.getVersion())
                    .actions(actions(self, admin.getStatus()))
                    .build());
        }
        return PageVO.of(list, result.getTotal(), page, size);
    }

    /** 本人行只有 EDIT；其他人按状态给停用/启用与重置密码 */
    private static List<String> actions(boolean self, String status) {
        List<String> actions = new ArrayList<>();
        actions.add("EDIT");
        if (self) {
            return actions;
        }
        actions.add("RESET_PASSWORD");
        actions.add(AdminStatus.DISABLED.name().equals(status) ? "ENABLE" : "DISABLE");
        return actions;
    }

    @Override
    public boolean usernameAvailable(String username) {
        if (username == null || !username.matches("[A-Za-z0-9]{4,20}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号需为 4–20 位字母和数字");
        }
        return adminMapper.selectByUsername(username.trim()) == null;
    }

    @Override
    @Transactional
    public AdminCreateResult create(AdminCreateRequest request) {
        String reason = AdminValidation.reason(request.reason(), "请填写新增原因");
        String name = AdminValidation.name(request.name());
        String username = request.username().trim();
        String phone = request.phone().trim();
        AdminRole role = requireRole(request.roleId());
        if (adminMapper.selectByUsername(username) != null) {
            throw new BizException(ErrorCode.NAME_DUPLICATE, MSG_USERNAME_TAKEN);
        }
        if (adminMapper.countByPhone(phone, null) > 0) {
            throw new BizException(ErrorCode.NAME_DUPLICATE, MSG_PHONE_TAKEN);
        }
        LocalDateTime now = BeijingTime.now();
        String tempPassword = AdminPasswords.tempPassword();
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setName(name);
        admin.setPhone(phone);
        admin.setPasswordHash(passwordEncoder.encode(tempPassword));
        admin.setRoleId(role.getId());
        admin.setStatus(AdminStatus.NORMAL.name());
        admin.setMustChangePassword(true);
        admin.setTempPasswordExpireAt(now.plusHours(TEMP_PASSWORD_HOURS));
        admin.setLoginFailCount(0);
        admin.setTokenVersion(0);
        admin.setVersion(0);
        adminMapper.insert(admin);

        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("ADMIN_RBAC")
                .opAction("CREATE")
                .bizType("ADMIN")
                .bizId(admin.getId())
                .targetName(name + "（" + username + "）")
                .reason(reason)
                .after(AdminDiffs.put(AdminDiffs.put(AdminDiffs.put(new LinkedHashMap<>(),
                                "name", "姓名", name),
                        "phone", "手机号", Masks.phone(phone)),
                        "role", "角色", role.getRoleName()))
                .build());

        // 平台没有通知类短信模板：初始密码只返回给操作人一次，由其线下告知；不打印、不写日志
        return new AdminCreateResult(admin.getId(), name, Masks.phone(phone), tempPassword,
                admin.getTempPasswordExpireAt());
    }

    @Override
    @Transactional
    public String update(Long adminId, AdminUpdateRequest request) {
        Admin target = requireAdmin(adminId);
        String reason = AdminValidation.reason(request.reason(), "请填写修改原因");
        AdminRole newRole = requireRole(request.roleId());
        String name = AdminValidation.name(request.name());
        String phone = request.phone() == null || request.phone().isBlank()
                ? target.getPhone() : request.phone().trim();
        Long currentAdminId = AdminContext.requireAdminId();
        boolean self = adminId.equals(currentAdminId);
        if (self && !target.getRoleId().equals(request.roleId())) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "不能修改自己的角色");
        }
        if (adminMapper.countByPhone(phone, adminId) > 0) {
            throw new BizException(ErrorCode.NAME_DUPLICATE, MSG_PHONE_TAKEN);
        }
        AdminRole oldRole = adminRoleMapper.selectById(target.getRoleId());
        if (!self && oldRole != null && AdminRoleCode.SUPER_ADMIN.name().equals(oldRole.getRoleCode())
                && !AdminRoleCode.SUPER_ADMIN.name().equals(newRole.getRoleCode())) {
            ensureAnotherNormalSuperAdmin(target);
        }

        String oldName = target.getName();
        String oldPhone = target.getPhone();
        String oldRoleName = oldRole == null ? null : oldRole.getRoleName();
        target.setName(name);
        target.setPhone(phone);
        target.setRoleId(newRole.getId());
        target.setVersion(request.version());
        if (adminMapper.updateById(target) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
        }

        // 只记有变化的字段；手机号按原值比较，打码后再写
        AdminDiffs.Diff diff = AdminDiffs.diff()
                .field("name", "姓名", oldName, name);
        if (!Objects.equals(oldPhone, phone)) {
            String oldMasked = Masks.phone(oldPhone);
            String newMasked = Masks.phone(phone);
            diff.field("phone", "手机号", oldMasked,
                    Objects.equals(oldMasked, newMasked) ? newMasked + "（已更换）" : newMasked);
        }
        diff.field("role", "角色", oldRoleName, newRole.getRoleName());
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("ADMIN_RBAC")
                .opAction("UPDATE")
                .bizType("ADMIN")
                .bizId(adminId)
                .targetName(name + "（" + target.getUsername() + "）")
                .reason(reason)
                .before(diff.before())
                .after(diff.after())
                .build());
        return name;
    }

    @Override
    @Transactional
    public AdminResetResult resetPassword(Long adminId, AdminResetPasswordRequest request) {
        Admin target = requireAdmin(adminId);
        if (adminId.equals(AdminContext.requireAdminId())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "不能重置自己的密码，请在个人设置中修改密码");
        }
        String reason = AdminValidation.reason(request.reason(), "请填写修改原因");
        LocalDateTime now = BeijingTime.now();
        String tempPassword = AdminPasswords.tempPassword();
        int tokenVersion = target.getTokenVersion() == null ? 0 : target.getTokenVersion();
        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        target.setMustChangePassword(true);
        target.setTempPasswordExpireAt(now.plusHours(TEMP_PASSWORD_HOURS));
        target.setPasswordUpdatedAt(now);
        target.setTokenVersion(tokenVersion + 1);
        target.setLoginFailCount(0);
        target.setLockedUntil(null);
        adminMapper.updateById(target);
        // updateById 跳过 null：锁定时间要显式清空，否则重置密码后仍被锁着
        adminMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Admin>lambdaUpdate()
                .eq(Admin::getId, target.getId())
                .set(Admin::getLockedUntil, null));

        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("ADMIN_RBAC")
                .opAction("RESET_PASSWORD")
                .bizType("ADMIN")
                .bizId(adminId)
                .targetName(target.getName() + "（" + target.getUsername() + "）")
                .reason(reason)
                .extraText("重置密码并解锁，临时密码 24 小时有效")
                .build());
        AfterCommit.run(() -> adminSessionService.invalidateAll(adminId));
        return new AdminResetResult(target.getName(), Masks.phone(target.getPhone()), tempPassword,
                target.getTempPasswordExpireAt());
    }

    @Override
    @Transactional
    public String disable(Long adminId, AdminStatusRequest request) {
        Admin target = requireAdmin(adminId);
        if (adminId.equals(AdminContext.requireAdminId())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "不能停用自己的账号");
        }
        String reason = AdminValidation.reason(request.reason(), "请填写停用原因");
        if (AdminStatus.DISABLED.name().equals(target.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个管理员已经停用");
        }
        AdminRole role = adminRoleMapper.selectById(target.getRoleId());
        if (role != null && AdminRoleCode.SUPER_ADMIN.name().equals(role.getRoleCode())) {
            ensureAnotherNormalSuperAdmin(target);
        }
        int tokenVersion = target.getTokenVersion() == null ? 0 : target.getTokenVersion();
        target.setStatus(AdminStatus.DISABLED.name());
        target.setDisabledAt(BeijingTime.now());
        target.setTokenVersion(tokenVersion + 1);
        target.setVersion(request.version());
        if (adminMapper.updateById(target) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
        }
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("ADMIN_RBAC")
                .opAction("DISABLE")
                .bizType("ADMIN")
                .bizId(adminId)
                .targetName(target.getName() + "（" + target.getUsername() + "）")
                .reason(reason)
                .before(AdminDiffs.of("status", "状态", AdminStatus.NORMAL.getText()))
                .after(AdminDiffs.of("status", "状态", AdminStatus.DISABLED.getText()))
                .build());
        AfterCommit.run(() -> adminSessionService.invalidateAll(adminId));
        return target.getName();
    }

    @Override
    @Transactional
    public String enable(Long adminId, AdminStatusRequest request) {
        Admin target = requireAdmin(adminId);
        String reason = AdminValidation.reason(request.reason(), "请填写启用原因");
        if (AdminStatus.NORMAL.name().equals(target.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个管理员已经是正常状态");
        }
        target.setStatus(AdminStatus.NORMAL.name());
        target.setDisabledAt(null);
        target.setVersion(request.version());
        if (adminMapper.updateById(target) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
        }
        adminMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Admin>lambdaUpdate()
                .eq(Admin::getId, adminId)
                .set(Admin::getDisabledAt, null));
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("ADMIN_RBAC")
                .opAction("ENABLE")
                .bizType("ADMIN")
                .bizId(adminId)
                .targetName(target.getName() + "（" + target.getUsername() + "）")
                .reason(reason)
                .before(AdminDiffs.of("status", "状态", AdminStatus.DISABLED.getText()))
                .after(AdminDiffs.of("status", "状态", AdminStatus.NORMAL.getText()))
                .build());
        return target.getName();
    }

    private Admin requireAdmin(Long adminId) {
        Admin admin = adminId == null ? null : adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        return admin;
    }

    private AdminRole requireRole(Long roleId) {
        AdminRole role = roleId == null ? null : adminRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择角色");
        }
        return role;
    }

    /**
     * 超管保护：目标当前是正常超级管理员且即将失去该身份时，先锁定超级管理员角色行，
     * 再统计正常超管数，保证操作后至少仍有一个。
     */
    private void ensureAnotherNormalSuperAdmin(Admin target) {
        if (AdminStatus.DISABLED.name().equals(target.getStatus())) {
            return;
        }
        AdminRole superRole = adminRoleMapper.selectByRoleCode(AdminRoleCode.SUPER_ADMIN.name());
        if (superRole != null) {
            adminRoleMapper.selectByIdForUpdate(superRole.getId());
        }
        if (adminMapper.countNormalSuperAdmins() <= 1) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, MSG_KEEP_SUPER);
        }
    }
}
