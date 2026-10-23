package com.zhimu.server.biz.farm.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 名称唯一性：同一 owner_user_id 下未删除的养殖场不重名（去首尾空格后比较），
 * 待审核资料变更的新名称也参与；可以排除本场。
 */
@Component
@RequiredArgsConstructor
public class FarmNameGuard {

    private final FarmMapper farmMapper;
    private final FarmChangeMapper changeMapper;

    public boolean exists(Long ownerUserId, String name, Long excludeFarmId) {
        if (ownerUserId == null || name == null || name.isBlank()) {
            return false;
        }
        String target = name.trim();
        return TenantSkip.call(() -> {
            Long farmCount = farmMapper.selectCount(Wrappers.<Farm>lambdaQuery()
                    .eq(Farm::getOwnerUserId, ownerUserId)
                    .eq(Farm::getName, target)
                    .ne(excludeFarmId != null, Farm::getId, excludeFarmId));
            if (farmCount != null && farmCount > 0) {
                return true;
            }
            Long changeCount = changeMapper.selectCount(Wrappers.<FarmChange>lambdaQuery()
                    .eq(FarmChange::getStatus, "PENDING")
                    .eq(FarmChange::getNewName, target)
                    .ne(excludeFarmId != null, FarmChange::getFarmId, excludeFarmId));
            return changeCount != null && changeCount > 0;
        });
    }

    /** 新建、修改注册信息、资料变更：重名返回 40905，文案带名称 */
    public void assertUnique(Long ownerUserId, String name, Long excludeFarmId) {
        String target = name == null ? "" : name.trim();
        if (exists(ownerUserId, target, excludeFarmId)) {
            throw new BizException(ErrorCode.NAME_DUPLICATE,
                    FarmValidations.MSG_NAME_DUP.formatted(target));
        }
    }
}
