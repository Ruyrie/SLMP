package com.zhimu.server.admin.system;

import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminPermission;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.enums.AdminStatus;
import com.zhimu.server.common.context.AdminContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 管理端测试数据：管理员、角色、权限点与已登录上下文。 */
public final class AdminTestFixtures {

    private AdminTestFixtures() {
    }

    public static Admin admin(long id, String username, String name, long roleId, String status) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setUsername(username);
        admin.setName(name);
        admin.setPhone("13900005874");
        admin.setRoleId(roleId);
        admin.setStatus(status);
        admin.setPasswordHash("$2a$10$hash");
        admin.setMustChangePassword(false);
        admin.setLoginFailCount(0);
        admin.setTokenVersion(0);
        admin.setVersion(0);
        return admin;
    }

    public static AdminRole role(long id, String code, String name, boolean editable) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(name);
        role.setBuiltin(true);
        role.setEditable(editable);
        role.setDefaultPermCodes("[]");
        role.setSortNo((int) id);
        role.setVersion(0);
        return role;
    }

    public static AdminPermission permission(long id, String code, String module, String page,
                                             String pageName, String action, boolean superOnly, int sortNo) {
        AdminPermission permission = new AdminPermission();
        permission.setId(id);
        permission.setPermCode(code);
        permission.setModuleCode(module);
        permission.setPageCode(page);
        permission.setPageName(pageName);
        permission.setPermAction(action);
        permission.setSuperOnly(superOnly);
        permission.setSortNo(sortNo);
        return permission;
    }

    /** 覆盖 5 个功能块、4 个页面（含超管专属与无处理动作的页面），供权限表与菜单测试 */
    public static List<AdminPermission> permissions() {
        List<AdminPermission> list = new ArrayList<>();
        list.add(permission(1, "WA-S02:VIEW", "OVERVIEW", "WA-S02", "平台概览", "VIEW", false, 10));
        list.add(permission(2, "WA-R01:VIEW", "AUDIT", "WA-R01", "养殖场注册审核", "VIEW", false, 20));
        list.add(permission(3, "WA-R01:HANDLE", "AUDIT", "WA-R01", "养殖场注册审核", "HANDLE", false, 21));
        list.add(permission(4, "WA-D01:VIEW", "BASE_DATA", "WA-D01", "品类品种与生长标准", "VIEW", false, 30));
        list.add(permission(5, "WA-D01:HANDLE", "BASE_DATA", "WA-D01", "品类品种与生长标准", "HANDLE", false, 31));
        list.add(permission(6, "WA-S03:VIEW", "USER_SYSTEM", "WA-S03", "用户账号", "VIEW", false, 40));
        list.add(permission(7, "WA-S03:HANDLE", "USER_SYSTEM", "WA-S03", "用户账号", "HANDLE", true, 41));
        list.add(permission(8, "WA-S05:VIEW", "USER_SYSTEM", "WA-S05", "管理员与权限", "VIEW", true, 42));
        list.add(permission(9, "WA-S05:HANDLE", "USER_SYSTEM", "WA-S05", "管理员与权限", "HANDLE", true, 43));
        return list;
    }

    public static Set<String> codes(String... codes) {
        return new LinkedHashSet<>(List.of(codes));
    }

    public static AdminContext.LoginAdmin login(Long adminId, String name, String role, Set<String> permissions) {
        return new AdminContext.LoginAdmin(adminId, name, Set.of(role), permissions, 0, "tok");
    }

    public static LocalDateTime at(String text) {
        return LocalDateTime.parse(text.replace(' ', 'T'));
    }

    public static boolean isNormal(Admin admin) {
        return AdminStatus.NORMAL.name().equals(admin.getStatus());
    }
}
