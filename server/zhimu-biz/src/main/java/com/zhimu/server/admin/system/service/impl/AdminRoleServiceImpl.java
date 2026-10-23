package com.zhimu.server.admin.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.AdminPermissionService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminPermission;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.entity.AdminRolePermission;
import com.zhimu.server.admin.system.enums.AdminStatus;
import com.zhimu.server.admin.system.enums.PermModule;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminPermissionMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.admin.system.mapper.AdminRolePermissionMapper;
import com.zhimu.server.admin.system.request.RolePermissionSaveRequest;
import com.zhimu.server.admin.system.service.AdminRoleService;
import com.zhimu.server.admin.system.support.AdminDiffs;
import com.zhimu.server.admin.system.support.AdminValidation;
import com.zhimu.server.admin.system.vo.RoleItemVO;
import com.zhimu.server.admin.system.vo.RolePermissionVO;
import com.zhimu.server.common.enums.AdminRoleCode;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色与权限（ADM-S27–S29）。超级管理员角色 editable = 0，权限固定为全部且不可修改；
 * 其他角色不得授予超管专属权限点；勾选 HANDLE 必须同时勾选同页 VIEW；保存按角色整体替换，
 * version 条件更新 t_admin_role，提交后删除 admin:perm:{role_id}，下一次请求立即生效。
 */
@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl implements AdminRoleService {

    private static final String MSG_ROLE_NOT_FOUND = "记录不存在或无权查看";
    private static final String MSG_SUPER_IMMUTABLE = "超级管理员的权限不能修改";
    private static final String MSG_VERSION_CONFLICT = "内容已更新，请刷新后再操作";
    private static final String MSG_PERM_NOT_FOUND = "权限码不存在";
    private static final String MSG_SUPER_ONLY = "管理员与权限只限超级管理员";
    private static final String MSG_HANDLE_WITHOUT_VIEW = "勾选“处理”时必须同时勾选“查看”";
    private static final TypeReference<List<String>> CODE_LIST = new TypeReference<>() {
    };

    private final AdminMapper adminMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionMapper adminPermissionMapper;
    private final AdminRolePermissionMapper adminRolePermissionMapper;
    private final AdminPermissionService adminPermissionService;
    private final OperationLogService operationLogService;

    @Override
    public List<RoleItemVO> list() {
        List<AdminRole> roles = adminRoleMapper.selectList(Wrappers.<AdminRole>lambdaQuery()
                .orderByAsc(AdminRole::getSortNo)
                .orderByAsc(AdminRole::getId));
        List<RoleItemVO> result = new ArrayList<>(roles.size());
        for (AdminRole role : roles) {
            result.add(toRoleItem(role, adminMapper.countByRoleId(role.getId())));
        }
        return result;
    }

    @Override
    public RolePermissionVO permissions(Long roleId) {
        AdminRole role = roleId == null ? null : adminRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_ROLE_NOT_FOUND);
        }
        List<AdminPermission> permissions = allPermissions();
        boolean superRole = AdminRoleCode.SUPER_ADMIN.name().equals(role.getRoleCode());
        Set<String> roleCodes = superRole
                ? allCodes(permissions)
                : new LinkedHashSet<>(adminRolePermissionMapper.selectPermCodesByRoleId(roleId));

        List<RolePermissionVO.Member> members = new ArrayList<>();
        for (Admin admin : adminMapper.selectByRoleId(roleId)) {
            members.add(RolePermissionVO.Member.builder()
                    .adminId(admin.getId())
                    .name(admin.getName())
                    .status(admin.getStatus())
                    .statusText(CodeEnum.textOf(AdminStatus.class, admin.getStatus()))
                    .build());
        }

        List<RolePermissionVO.Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<AdminPermission>> entry : pages(permissions).entrySet()) {
            AdminPermission view = null;
            AdminPermission handle = null;
            for (AdminPermission permission : entry.getValue()) {
                if ("VIEW".equals(permission.getPermAction())) {
                    view = permission;
                } else if ("HANDLE".equals(permission.getPermAction())) {
                    handle = permission;
                }
            }
            if (view == null) {
                continue;
            }
            rows.add(RolePermissionVO.Row.builder()
                    .moduleCode(view.getModuleCode())
                    .moduleName(moduleName(view.getModuleCode()))
                    .pageCode(view.getPageCode())
                    .pageName(view.getPageName())
                    .view(cell(view, roleCodes, superRole))
                    .handle(handle == null ? null : cell(handle, roleCodes, superRole))
                    .handleDesc(handle == null ? null : handle.getHandleDesc())
                    .build());
        }

        return RolePermissionVO.builder()
                .role(toRoleItem(role, adminMapper.countByRoleId(roleId)))
                .members(members)
                .editable(role.getEditable())
                .version(role.getVersion())
                .rows(rows)
                .defaultCodes(defaultCodes(role))
                .build();
    }

    @Override
    @Transactional
    public SavedResult savePermissions(Long roleId, RolePermissionSaveRequest request) {
        AdminRole role = roleId == null ? null : adminRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_ROLE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(role.getEditable())) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, MSG_SUPER_IMMUTABLE);
        }
        String reason = AdminValidation.reason(request.reason(), "请填写修改原因");
        List<AdminPermission> permissions = allPermissions();
        Map<String, AdminPermission> byCode = new LinkedHashMap<>();
        for (AdminPermission permission : permissions) {
            byCode.put(permission.getPermCode(), permission);
        }
        Set<String> oldCodes = new LinkedHashSet<>(adminRolePermissionMapper.selectPermCodesByRoleId(roleId));
        Set<String> newCodes = new LinkedHashSet<>();
        if (request.permCodes() != null) {
            for (String code : request.permCodes()) {
                if (code != null && !code.isBlank()) {
                    newCodes.add(code.trim());
                }
            }
        }
        validateCodes(newCodes, byCode);
        if (adminRoleMapper.bumpVersion(roleId, request.version()) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
        }

        adminRolePermissionMapper.delete(Wrappers.<AdminRolePermission>lambdaQuery()
                .eq(AdminRolePermission::getRoleId, roleId));
        for (String code : newCodes) {
            AdminRolePermission row = new AdminRolePermission();
            row.setRoleId(roleId);
            row.setPermissionId(byCode.get(code).getId());
            adminRolePermissionMapper.insert(row);
        }

        long affected = adminMapper.countByRoleId(roleId);
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("ADMIN_RBAC")
                .opAction("SAVE_PERMISSION")
                .bizType("ADMIN_ROLE")
                .bizId(roleId)
                .targetName(role.getRoleName())
                .reason(reason)
                .before(AdminDiffs.of("perm_codes", "权限", describe(removed(oldCodes, newCodes), byCode)))
                .after(AdminDiffs.of("perm_codes", "权限", describe(added(oldCodes, newCodes), byCode)))
                .build());
        AfterCommit.run(() -> adminPermissionService.evictRole(roleId));
        return new SavedResult(role.getRoleName(), affected);
    }

    /** 权限组合校验：必须存在、不得含超管专属、HANDLE 必须伴随同页 VIEW */
    private static void validateCodes(Set<String> codes, Map<String, AdminPermission> byCode) {
        for (String code : codes) {
            AdminPermission permission = byCode.get(code);
            if (permission == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, MSG_PERM_NOT_FOUND);
            }
            if (Boolean.TRUE.equals(permission.getSuperOnly())) {
                throw new BizException(ErrorCode.PARAM_INVALID, MSG_SUPER_ONLY);
            }
        }
        for (String code : codes) {
            AdminPermission permission = byCode.get(code);
            if (permission != null && "HANDLE".equals(permission.getPermAction())) {
                String viewCode = permission.getPageCode() + ":VIEW";
                if (!codes.contains(viewCode)) {
                    throw new BizException(ErrorCode.PARAM_INVALID, MSG_HANDLE_WITHOUT_VIEW);
                }
            }
        }
    }

    private static Set<String> added(Set<String> before, Set<String> after) {
        Set<String> result = new LinkedHashSet<>(after);
        result.removeAll(before);
        return result;
    }

    private static Set<String> removed(Set<String> before, Set<String> after) {
        Set<String> result = new LinkedHashSet<>(before);
        result.removeAll(after);
        return result;
    }

    private static String describe(Set<String> codes, Map<String, AdminPermission> byCode) {
        if (codes.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (String code : codes) {
            AdminPermission permission = byCode.get(code);
            parts.add(permission == null ? code : permission.getPageName() + " " + permission.getPermAction());
        }
        return String.join("、", parts);
    }

    private List<AdminPermission> allPermissions() {
        return adminPermissionMapper.selectList(Wrappers.<AdminPermission>lambdaQuery()
                .orderByAsc(AdminPermission::getSortNo)
                .orderByAsc(AdminPermission::getId));
    }

    private static Set<String> allCodes(List<AdminPermission> permissions) {
        Set<String> codes = new LinkedHashSet<>();
        for (AdminPermission permission : permissions) {
            codes.add(permission.getPermCode());
        }
        return codes;
    }

    /** 按页面分组，保持权限点 sort_no 顺序 */
    private static Map<String, List<AdminPermission>> pages(List<AdminPermission> permissions) {
        Map<String, List<AdminPermission>> pages = new LinkedHashMap<>();
        for (AdminPermission permission : permissions) {
            pages.computeIfAbsent(permission.getPageCode(), key -> new ArrayList<>()).add(permission);
        }
        return pages;
    }

    private RolePermissionVO.Cell cell(AdminPermission permission, Set<String> roleCodes, boolean superRole) {
        boolean checked = superRole || roleCodes.contains(permission.getPermCode());
        boolean locked = superRole || Boolean.TRUE.equals(permission.getSuperOnly());
        return RolePermissionVO.Cell.builder()
                .permCode(permission.getPermCode())
                .checked(checked)
                .locked(locked)
                .build();
    }

    private static RoleItemVO toRoleItem(AdminRole role, long memberCount) {
        return RoleItemVO.builder()
                .roleId(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .memberCount(memberCount)
                .editable(role.getEditable())
                .build();
    }

    private static List<String> defaultCodes(AdminRole role) {
        if (role.getDefaultPermCodes() == null || role.getDefaultPermCodes().isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> codes = CacheJson.read(role.getDefaultPermCodes(), CODE_LIST);
            return codes == null ? new ArrayList<>() : codes;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private static String moduleName(String moduleCode) {
        String text = CodeEnum.textOf(PermModule.class, moduleCode);
        return text != null ? text : moduleCode;
    }
}
