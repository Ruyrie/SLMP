package com.zhimu.server.common.context;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

import java.util.Set;

/**
 * 当前登录的平台管理员：由管理端会话拦截器写入，请求结束时清除。
 */
public final class AdminContext {

    /**
     * @param adminId      t_admin.id
     * @param name         管理员姓名（留痕快照用）
     * @param roleCodes    角色编码（SUPER_ADMIN / AUDITOR / OPERATOR 或自定义角色）
     * @param permissions  权限点，例如 WA-R01:VIEW、WA-R01:HANDLE
     * @param tokenVersion t_admin.token_version
     * @param sessionToken 会话令牌（只在服务端使用，不输出）
     */
    public record LoginAdmin(Long adminId, String name, Set<String> roleCodes, Set<String> permissions,
                             int tokenVersion, String sessionToken) {

        public boolean hasPermission(String permission) {
            return permissions != null && permissions.contains(permission);
        }

        public boolean isSuperAdmin() {
            return roleCodes != null && roleCodes.contains("SUPER_ADMIN");
        }
    }

    private static final ThreadLocal<LoginAdmin> HOLDER = new ThreadLocal<>();

    private AdminContext() {
    }

    public static LoginAdmin get() {
        return HOLDER.get();
    }

    public static void set(LoginAdmin admin) {
        HOLDER.set(admin);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long adminId() {
        LoginAdmin admin = HOLDER.get();
        return admin == null ? null : admin.adminId();
    }

    public static Long requireAdminId() {
        Long id = adminId();
        if (id == null) {
            throw new BizException(ErrorCode.ADMIN_SESSION_TIMEOUT);
        }
        return id;
    }
}
