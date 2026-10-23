package com.zhimu.server.framework.security.spi;

import java.util.Set;

/**
 * 管理员鉴权状态：每次请求按此校验 token_version 与权限点（权限变更下一次请求即生效）。
 *
 * @param tokenVersion  t_admin.token_version
 * @param enabled      是否启用
 * @param name         姓名
 * @param roleCodes    角色编码
 * @param permissions  权限点集合
 * @param nextStep     强制步骤（例如 CHANGE_PASSWORD 首次登录或临时密码登录必须改密），没有为 null
 * @param invalidReason 会话失效原因，作为 40106 的 data.reason：EXPIRED / ADMIN_DISABLED / PASSWORD_CHANGED
 */
public record AdminAuthState(int tokenVersion, boolean enabled, String name, Set<String> roleCodes,
                             Set<String> permissions, String nextStep, String invalidReason) {
}
