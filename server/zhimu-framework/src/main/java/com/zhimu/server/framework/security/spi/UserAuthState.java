package com.zhimu.server.framework.security.spi;

/**
 * 用户鉴权状态（缓存键 auth:state:{user_id}，10 分钟）。
 *
 * @param tokenVersion  t_user.token_version，与令牌中的 tv 不一致时返回 40104
 * @param status        账号状态编码（NORMAL / DISABLED / CANCELLED 等，由模块01 定义）
 * @param nextStep      待完成的强制步骤（NONE 或 null 表示没有），例如 SAVE_RECOVERY_CODE、RECOVERY_CHANGE_PASSWORD
 * @param invalidReason token_version 变化的原因，作为 40104 的 data.reason：PASSWORD_CHANGED、ACCOUNT_RECOVERED、
 *                      ACCOUNT_DISABLED、ACCOUNT_CANCELLED
 */
public record UserAuthState(int tokenVersion, String status, String nextStep, String invalidReason) {

    public boolean hasForcedStep() {
        return nextStep != null && !nextStep.isEmpty() && !"NONE".equals(nextStep);
    }
}