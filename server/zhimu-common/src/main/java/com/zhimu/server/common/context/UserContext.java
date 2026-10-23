package com.zhimu.server.common.context;

import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

/**
 * 当前登录的用户端账号：由 AuthInterceptor 校验访问令牌后写入，请求结束时清除。
 */
public final class UserContext {

    /**
     * @param userId      t_user.id
     * @param clientType  令牌签发时的客户端（APP_ANDROID / WEB）
     * @param tokenVersion 令牌中的 token_version
     * @param jti         访问令牌编号
     */
    public record LoginUser(Long userId, ClientType clientType, int tokenVersion, String jti) {
    }

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前用户 ID；未登录时为 null */
    public static Long userId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    /** 当前用户 ID；未登录时抛 40101 */
    public static Long requireUserId() {
        Long id = userId();
        if (id == null) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
        return id;
    }
}
