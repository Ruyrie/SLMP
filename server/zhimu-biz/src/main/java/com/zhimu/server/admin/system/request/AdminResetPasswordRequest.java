package com.zhimu.server.admin.system.request;

/** ADM-S24 重置密码（同时解锁）；reason 为空按 40004 提示“请填写修改原因”。 */
public record AdminResetPasswordRequest(String reason) {
}
