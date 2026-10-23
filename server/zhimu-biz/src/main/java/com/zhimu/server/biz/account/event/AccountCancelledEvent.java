package com.zhimu.server.biz.account.event;

/**
 * 账号已注销（事务提交后发布）。模块03 解绑推送设备、删除本人消息与提醒设置；模块11 删除 AI 会话与消息。
 * 养殖场侧的处置在注销事务内由模块01 同步调用 FarmAccountService.handleAccountCancelled 完成。
 */
public record AccountCancelledEvent(Long userId, String phone) {
}