package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.common.entity.BizMedia;
import com.zhimu.server.biz.common.mapper.BizMediaMapper;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 记录媒体与修改记录的查看权限（API-U08、U14）：记录所属养殖场必须是本人可查看的养殖场，否则 40401。
 * 员工栏舍范围由各模块的 BizRecordAccessor 判断（本批没有记录访问器，先按成员校验）。
 */
@Component
@RequiredArgsConstructor
public class RecordAccessGuard {

    private final BizMediaMapper bizMediaMapper;
    private final FarmMemberCache farmMemberCache;
    private final List<com.zhimu.server.biz.common.api.BusinessMediaAccess> businessMediaAccess;

    public void checkMediaAccess(BizType bizType, Long bizId) {
        Long userId = UserContext.requireUserId();
        for (var access : businessMediaAccess) {
            if (access.bizType() == bizType) {
                if (!access.canViewRecord(bizId, userId)) throw new BizException(ErrorCode.NOT_FOUND);
                return;
            }
        }
        List<BizMedia> rows = TenantSkip.call(() -> bizMediaMapper.selectActiveByBiz(bizType.name(), bizId));
        checkFarms(rows == null ? List.of() : rows.stream().map(BizMedia::getFarmId).toList(), userId);
    }

    /** 修改记录的 farm_id 来自日志行；没有归属养殖场的日志不限制 */
    public void checkFarmAccess(Set<Long> farmIds, Long userId) {
        checkFarms(farmIds, userId);
    }

    private void checkFarms(java.util.Collection<Long> farmIds, Long userId) {
        Set<Long> distinct = new LinkedHashSet<>();
        if (farmIds != null) {
            for (Long farmId : farmIds) {
                if (farmId != null) {
                    distinct.add(farmId);
                }
            }
        }
        for (Long farmId : distinct) {
            if (farmMemberCache.get(farmId, userId) == null) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
        }
    }
}
