package com.zhimu.server.framework.security.spi;

/**
 * 读取用户鉴权状态，由模块01（biz.account）实现。框架负责 Redis 缓存 auth:state:{user_id}；
 * 状态变化（改密、停用、注销、找回）时模块01 调用 AuthStateCache 删除缓存。
 * 没有实现时框架只校验令牌签名与有效期。
 */
public interface UserAuthStateLoader {

    /** 用户不存在时返回 null（按 40104 EXPIRED 处理） */
    UserAuthState load(Long userId);
}