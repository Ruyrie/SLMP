package com.zhimu.server.framework.security.spi;

import com.zhimu.server.common.context.FarmContext;

/**
 * 读取用户在某养殖场的成员信息（角色、负责栏舍、派生状态），由模块02（biz.farm）实现。
 * 框架负责 Redis 缓存 farm:member:{farm_id}:{user_id}（10 分钟）；成员变化时模块02 调用 FarmMemberCache 删除缓存。
 */
public interface FarmMemberLoader {

    /** 不是成员（或已被移出、养殖场已删除）时返回 null */
    FarmContext.FarmMember load(Long farmId, Long userId);
}