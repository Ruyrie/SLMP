package com.zhimu.server.framework.security.session;

import java.time.LocalDateTime;

/**
 * Redis admin:session:{token} 的内容。
 *
 * @param adminId      t_admin.id
 * @param tokenVersion 创建会话时的 t_admin.token_version
 * @param createdAt    创建时间（北京时间）
 */
public record AdminSession(Long adminId, int tokenVersion, LocalDateTime createdAt) {
}
