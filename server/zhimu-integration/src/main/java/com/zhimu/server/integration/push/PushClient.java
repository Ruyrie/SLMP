package com.zhimu.server.integration.push;

import java.util.Collection;

/**
 * 极光推送（REST API v3）。所有方法都不抛异常：失败记录日志并返回结果，不影响主流程。
 */
public interface PushClient {

    /** 用户别名：u{用户 ID} */
    static String alias(long userId) {
        return "u" + userId;
    }

    /** 是否已配置 AppKey 与 Master Secret */
    boolean isEnabled();

    /** 按别名 u{userId} 推送 Android 通知；超过 1000 个别名自动分批 */
    PushResult pushToUsers(Collection<Long> userIds, PushMessage message);

    /** 设置设备别名（登录后上报设备时调用） */
    PushResult bindAlias(String registrationId, String alias);

    /** 清除设备别名（退出登录时调用） */
    PushResult unbindAlias(String registrationId);
}
