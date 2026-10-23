package com.zhimu.server.admin.system.support;

import com.zhimu.server.admin.system.entity.AdminPermission;
import com.zhimu.server.admin.system.enums.PermModule;
import com.zhimu.server.admin.system.vo.MenuGroupVO;
import com.zhimu.server.admin.system.vo.MenuPageVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 由权限点与当前管理员的权限码生成菜单：按功能块分组，只输出有 VIEW 权限的页面；
 * 审核中心四个页面带角标键（farm_audit、farm_change、change_request、account_recovery）。
 */
public final class AdminMenus {

    private AdminMenus() {
    }

    public static List<MenuGroupVO> build(List<AdminPermission> permissions, Set<String> codes) {
        List<MenuGroupVO> groups = new ArrayList<>();
        for (PermModule module : PermModule.values()) {
            List<MenuPageVO> pages = new ArrayList<>();
            for (AdminPermission permission : permissions) {
                if (!module.name().equals(permission.getModuleCode()) || !"VIEW".equals(permission.getPermAction())) {
                    continue;
                }
                if (!codes.contains(permission.getPermCode())) {
                    continue;
                }
                MenuPageVO page = MenuPageVO.builder()
                        .pageCode(permission.getPageCode())
                        .pageName(permission.getPageName())
                        .badgeKey(AdminLogTexts.badgeKeyOfPage(permission.getPageCode()))
                        .build();
                pages.add(page);
            }
            if (!pages.isEmpty()) {
                groups.add(MenuGroupVO.builder()
                        .groupCode(module.name())
                        .groupName(module.getText())
                        .pages(pages)
                        .build());
            }
        }
        return groups;
    }
}
