package com.zhimu.server.integration.sms;

/**
 * 验证码短信模板（号码认证服务赠送的系统模板，模板号见配置 templates）。
 */
public enum SmsTemplate {
    /** 登录 / 注册，100001 */
    LOGIN_REGISTER,
    /** 修改绑定手机号，100002 */
    CHANGE_BOUND_PHONE,
    /** 重置密码，100003 */
    RESET_PASSWORD,
    /** 绑定新手机号，100004 */
    BIND_NEW_PHONE,
    /** 验证绑定手机号，100005 */
    VERIFY_BOUND_PHONE
}
