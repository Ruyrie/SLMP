package com.zhimu.slmp.common.sms;

/**
 * 号码认证服务赠送的系统短信模板（内容含 ${code} 与 ${min}），须与赠送签名搭配使用。
 */
public final class AliyunSmsTemplates {

    private AliyunSmsTemplates() {
    }

    /** 登录 / 注册 */
    public static final String LOGIN_REGISTER = "100001";
    /** 修改绑定手机号 */
    public static final String CHANGE_BOUND_PHONE = "100002";
    /** 重置密码 */
    public static final String RESET_PASSWORD = "100003";
    /** 绑定新手机号 */
    public static final String BIND_NEW_PHONE = "100004";
    /** 验证绑定手机号 */
    public static final String VERIFY_BOUND_PHONE = "100005";
}
