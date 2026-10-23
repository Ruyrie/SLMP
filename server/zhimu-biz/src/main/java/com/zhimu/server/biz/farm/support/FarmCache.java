package com.zhimu.server.biz.farm.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 成员权限缓存失效：全部在事务提交后执行。养殖场状态变化（停用、启用、冻结、解冻、删除、审核）时
 * 按 t_farm_member 在场成员逐个 evict；成员特别多时直接 evictFarm。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FarmCache {

    static final int EVICT_FARM_THRESHOLD = 200;

    private final FarmMemberCache memberCache;
    private final FarmMemberMapper memberMapper;

    /** 单个成员（改角色、调栏舍、移出、退出、同意邀请、审核等） */
    public void evict(Long farmId, Long userId) {
        if (farmId == null || userId == null) {
            return;
        }
        AfterCommit.run(() -> memberCache.evict(farmId, userId));
    }

    /** 养殖场状态变化：现在查出在场成员，事务提交后逐个删除 */
    public void evictFarmMembers(Long farmId) {
        if (farmId == null) {
            return;
        }
        List<Long> userIds = TenantSkip.call(() -> memberMapper.selectList(
                        Wrappers.<FarmMember>lambdaQuery()
                                .eq(FarmMember::getFarmId, farmId)
                                .eq(FarmMember::getActiveFlag, true))
                .stream().map(FarmMember::getUserId).toList());
        evictUsers(farmId, userIds);
    }

    /** 指定的成员集合（转让双方、审核时的所有者等） */
    public void evictUsers(Long farmId, Collection<Long> userIds) {
        if (farmId == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> unique = new LinkedHashSet<>(userIds);
        if (unique.size() > EVICT_FARM_THRESHOLD) {
            AfterCommit.run(() -> memberCache.evictFarm(farmId));
            return;
        }
        List<Long> list = new ArrayList<>(unique);
        AfterCommit.run(() -> list.forEach(userId -> memberCache.evict(farmId, userId)));
    }

    /** 无法确定成员时兜底 */
    public void evictFarm(Long farmId) {
        if (farmId == null) {
            return;
        }
        AfterCommit.run(() -> memberCache.evictFarm(farmId));
    }
}
