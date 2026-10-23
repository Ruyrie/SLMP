package com.zhimu.server.biz.account.support;

import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

import java.util.Locale;

/** 模块01 的 X-Client 解析：缺失或 ADMIN_WEB 返回 40101。 */
public final class Clients {

    private Clients() {
    }

    public static ClientType require(String header) {
        ClientType type = parse(header);
        if (type == null || type == ClientType.ADMIN_WEB) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
        return type;
    }

    /** 非认证接口按 App 处理，保证缺少请求头时也能联调 */
    public static ClientType orApp(String header) {
        ClientType type = parse(header);
        return type == ClientType.WEB ? ClientType.WEB : ClientType.APP_ANDROID;
    }

    public static ClientType parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return ClientType.valueOf(header.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
