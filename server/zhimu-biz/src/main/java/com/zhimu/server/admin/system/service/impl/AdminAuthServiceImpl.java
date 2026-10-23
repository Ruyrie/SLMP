package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.enums.AdminStatus;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.admin.system.request.AdminLoginRequest;
import com.zhimu.server.admin.system.request.AdminPasswordRequest;
import com.zhimu.server.admin.system.service.AdminAuthService;
import com.zhimu.server.admin.system.service.CaptchaService;
import com.zhimu.server.admin.system.support.AdminLogTexts;
import com.zhimu.server.admin.system.support.AdminMenus;
import com.zhimu.server.admin.system.support.AdminPermissionCache;
import com.zhimu.server.admin.system.support.AdminValidation;
import com.zhimu.server.admin.system.vo.AdminLoginVO;
import com.zhimu.server.admin.system.vo.AdminMeVO;
import com.zhimu.server.admin.system.vo.AdminProfileVO;
import com.zhimu.server.biz.account.api.AccountRecoveryAdminService;
import com.zhimu.server.biz.common.api.ChangeRequestAdminService;
import com.zhimu.server.biz.farm.api.FarmAuditAdminService;
import com.zhimu.server.biz.farm.api.FarmChangeAdminService;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.AdminRoleCode;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.session.AdminSessionService;
import com.zhimu.server.framework.tx.AfterCommit;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端登录与改密。
 * <p>
 * 登录链：验证码 → 锁定判断 → 密码（失败 5 次锁 30 分钟）→ 停用/临时密码过期 → 建立 30 分钟滑动会话。
 * 失败计数以 Redis 为准并同步 t_admin；失败日志用独立事务，成功时清零并写 last_login。
 * 会话令牌只通过 Cookie 下发，响应体不含令牌。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String MSG_CAPTCHA_WRONG = "验证码不正确，已换一张";
    private static final String MSG_DISABLED = "这个管理员账号已停用，请联系超级管理员。";
    private static final String MSG_TEMP_EXPIRED = "初始密码已过期，请联系超级管理员重置密码";
    private static final String MSG_REASON_UNAVAILABLE = "登录已超时，请重新登录";

    private final AdminMapper adminMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionCache permissionCache;
    private final PasswordEncoder passwordEncoder;
    private final AdminSessionService sessionService;
    private final CaptchaService captchaService;
    private final AdminLoginAttemptService attemptService;
    private final OperationLogService operationLogService;
    private final AdminMessageService adminMessageService;
    private final SecurityProperties securityProperties;
    private final ObjectProvider<IpRegionResolver> ipRegionResolverProvider;
    private final FarmAuditAdminService farmAuditAdminService;
    private final FarmChangeAdminService farmChangeAdminService;
    private final ChangeRequestAdminService changeRequestAdminService;
    private final AccountRecoveryAdminService accountRecoveryAdminService;

    @Override
    public AdminLoginResult login(AdminLoginRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        if (!captchaService.verify(request.captchaId(), request.captchaCode())) {
            throw new BizException(ErrorCode.SMS_CODE_WRONG, MSG_CAPTCHA_WRONG);
        }
        LocalDateTime now = BeijingTime.now();
        Admin admin = adminMapper.selectByUsername(username);

        // 锁定中不校验密码，直接返回解锁时间
        if (admin != null && admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(now)) {
            throw locked(admin.getLockedUntil());
        }
        if (admin == null) {
            throw unknownAccountFailure(username, now);
        }
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw passwordFailure(admin, username, now);
        }
        if (AdminStatus.DISABLED.name().equals(admin.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, MSG_DISABLED);
        }
        if (Boolean.TRUE.equals(admin.getMustChangePassword())
                && admin.getTempPasswordExpireAt() != null
                && !now.isBefore(admin.getTempPasswordExpireAt())) {
            throw new BizException(ErrorCode.TEMP_PASSWORD_EXPIRED, MSG_TEMP_EXPIRED);
        }

        attemptService.clear(username);
        String ip = RequestContext.ip();
        String region = resolveRegion(ip);
        int tokenVersion = admin.getTokenVersion() == null ? 0 : admin.getTokenVersion();
        attemptService.persistSuccess(admin.getId(), ip, region, loginLog(admin, username, "SUCCESS", null));

        String token = sessionService.create(admin.getId(), tokenVersion);
        AdminRole role = adminRoleMapper.selectById(admin.getRoleId());
        return new AdminLoginResult(toLoginVO(admin, role), token);
    }

    @Override
    public void logout() {
        Long adminId = AdminContext.requireAdminId();
        Admin admin = adminMapper.selectById(adminId);
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("LOGIN")
                .opAction("LOGOUT")
                .bizType("ADMIN")
                .bizId(adminId)
                .targetName(admin == null ? null : admin.getName())
                .build());
        AdminContext.LoginAdmin context = AdminContext.get();
        if (context != null) {
            sessionService.invalidate(context.sessionToken());
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void changePassword(AdminPasswordRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.ADMIN_SESSION_TIMEOUT, MSG_REASON_UNAVAILABLE).with("reason", "EXPIRED");
        }
        LocalDateTime now = BeijingTime.now();
        if (Boolean.TRUE.equals(admin.getMustChangePassword())
                && admin.getTempPasswordExpireAt() != null
                && !now.isBefore(admin.getTempPasswordExpireAt())) {
            throw new BizException(ErrorCode.TEMP_PASSWORD_EXPIRED, MSG_TEMP_EXPIRED);
        }
        if (!passwordEncoder.matches(request.oldPassword(), admin.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_WRONG, AdminValidation.MSG_OLD_PASSWORD);
        }
        String problem = AdminValidation.passwordProblem(request.newPassword());
        if (problem != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, problem);
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, AdminValidation.MSG_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), admin.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, AdminValidation.MSG_PASSWORD_SAME);
        }

        int tokenVersion = admin.getTokenVersion() == null ? 0 : admin.getTokenVersion();
        admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        admin.setMustChangePassword(false);
        admin.setTempPasswordExpireAt(null);
        admin.setPasswordUpdatedAt(now);
        admin.setTokenVersion(tokenVersion + 1);
        adminMapper.updateById(admin);

        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("LOGIN")
                .opAction("CHANGE_PASSWORD")
                .bizType("ADMIN")
                .bizId(adminId)
                .targetName(admin.getName())
                .build());
        // 改密后让全部旧会话失效；放在提交后，避免事务回滚却已把用户踢下线
        AfterCommit.run(() -> sessionService.invalidateAll(adminId));
    }

    @Override
    public AdminMeVO me() {
        Long adminId = AdminContext.requireAdminId();
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.ADMIN_SESSION_TIMEOUT, MSG_REASON_UNAVAILABLE).with("reason", "EXPIRED");
        }
        AdminRole role = adminRoleMapper.selectById(admin.getRoleId());
        AdminMeVO me = AdminMeVO.from(toLoginVO(admin, role));
        me.setBadges(badges());
        me.setUnreadMessageCount(adminMessageService.unreadCount(adminId));
        return me;
    }

    private BizException unknownAccountFailure(String username, LocalDateTime now) {
        int count = attemptService.recordFailure(username);
        if (count >= AdminLoginAttemptService.MAX_FAILURES) {
            attemptService.clear(username);
            LocalDateTime lockedUntil = now.plus(AdminLoginAttemptService.LOCK_DURATION);
            attemptService.persistFailure(null, 0, null, loginLog(null, username, "FAIL", "账号不存在，第 5 次错误锁定"));
            throw locked(lockedUntil);
        }
        attemptService.persistFailure(null, count, null,
                loginLog(null, username, "FAIL", "账号不存在或密码错误，还可以再试 " + (AdminLoginAttemptService.MAX_FAILURES - count) + " 次"));
        throw passwordWrong(AdminLoginAttemptService.MAX_FAILURES - count);
    }

    private BizException passwordFailure(Admin admin, String username, LocalDateTime now) {
        int count = attemptService.recordFailure(username);
        if (count >= AdminLoginAttemptService.MAX_FAILURES) {
            attemptService.clear(username);
            LocalDateTime lockedUntil = now.plus(AdminLoginAttemptService.LOCK_DURATION);
            attemptService.persistFailure(admin.getId(), 0, lockedUntil,
                    loginLog(admin, username, "FAIL", "密码连续错误 5 次，锁定至 " + BeijingTime.format(lockedUntil)));
            throw locked(lockedUntil);
        }
        attemptService.persistFailure(admin.getId(), count, null,
                loginLog(admin, username, "FAIL", "密码错误，还可以再试 " + (AdminLoginAttemptService.MAX_FAILURES - count) + " 次"));
        throw passwordWrong(AdminLoginAttemptService.MAX_FAILURES - count);
    }

    private static BizException passwordWrong(int remaining) {
        return new BizException(ErrorCode.PASSWORD_WRONG, "账号或密码不正确，还可以再试 " + remaining + " 次")
                .with("remaining", remaining);
    }

    private static BizException locked(LocalDateTime lockedUntil) {
        return new BizException(ErrorCode.LOGIN_LOCKED,
                "账号已锁定。密码连续错误 5 次，" + lockedUntil.format(BeijingTime.TIME)
                        + " 后可以再次登录，或联系超级管理员解锁。")
                .with("locked_until", lockedUntil);
    }

    private static OperationLogEntry loginLog(Admin admin, String username, String result, String extraText) {
        return OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(admin == null ? null : admin.getId())
                .operatorName(admin == null ? username : admin.getName())
                .opType("LOGIN")
                .opAction("LOGIN")
                .result(result)
                .bizType("ADMIN")
                .bizId(admin == null ? null : admin.getId())
                .targetName(admin == null ? username : admin.getName() + "（" + admin.getUsername() + "）")
                .extraText(extraText)
                .build();
    }

    private AdminLoginVO toLoginVO(Admin admin, AdminRole role) {
        String roleCode = role == null ? null : role.getRoleCode();
        Set<String> permissions = role == null ? Set.of() : permissionCache.permissionsOfRole(role.getId());
        List<String> permissionList = List.copyOf(permissions);
        AdminProfileVO profile = AdminProfileVO.builder()
                .adminId(admin.getId())
                .name(admin.getName())
                .username(admin.getUsername())
                .roleCode(roleCode)
                .roleCodeText(CodeEnum.textOf(AdminRoleCode.class, roleCode))
                .build();
        return AdminLoginVO.builder()
                .admin(profile)
                .mustChangePassword(Boolean.TRUE.equals(admin.getMustChangePassword()))
                .tempPasswordExpireAt(admin.getTempPasswordExpireAt())
                .idleMinutes((int) securityProperties.getAdminSessionTtl().toMinutes())
                .permissions(permissionList)
                .menus(AdminMenus.build(permissionCache.allPermissions(), permissions))
                .build();
    }

    /** 四个待审核队列；任一服务异常不影响外壳返回，角标按 0 处理 */
    private Map<String, Integer> badges() {
        Map<String, Integer> badges = new LinkedHashMap<>();
        badges.put("farm_audit", safeCount(() -> farmAuditAdminService.countPending()));
        badges.put("farm_change", safeCount(() -> farmChangeAdminService.countPending()));
        badges.put("change_request", safeCount(() -> changeRequestAdminService.countPending()));
        badges.put("account_recovery", safeCount(() -> accountRecoveryAdminService.countPending()));
        return badges;
    }

    private int safeCount(CountSupplier supplier) {
        try {
            return (int) supplier.get();
        } catch (RuntimeException e) {
            log.warn("管理端角标统计失败，按 0 返回", e);
            return 0;
        }
    }

    private String resolveRegion(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        IpRegionResolver resolver = ipRegionResolverProvider.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.resolve(ip);
        } catch (RuntimeException e) {
            log.debug("登录 IP 属地解析失败 ip={}", ip, e);
            return null;
        }
    }

    @FunctionalInterface
    private interface CountSupplier {
        long get();
    }
}
