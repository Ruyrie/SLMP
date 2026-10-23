package com.zhimu.server.biz.account.event;

/** 账号换绑手机号（例如凭恢复码找回通过后，事务提交后发布）；模块02 据此给新手机号补发邀请消息 */
public record UserPhoneChangedEvent(Long userId, String oldPhone, String newPhone) {
}