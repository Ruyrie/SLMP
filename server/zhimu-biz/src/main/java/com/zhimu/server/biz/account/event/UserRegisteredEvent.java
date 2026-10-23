package com.zhimu.server.biz.account.event;

/** 账号注册成功（事务提交后发布）；模块02 据此给该手机号补发待接受的邀请消息 */
public record UserRegisteredEvent(Long userId, String phone) {
}