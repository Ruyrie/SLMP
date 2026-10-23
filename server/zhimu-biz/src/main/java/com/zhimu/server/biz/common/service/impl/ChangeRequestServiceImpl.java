package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.common.api.ChangeRequestService;
import com.zhimu.server.biz.common.mapper.ChangeRequestMapper;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 修改申请对其他模块提供的能力（模块01 注销、模块02 删除养殖场、API-A15 计数）。
 * 系统撤销把状态置为 CANCELED、pending_flag 置 NULL，并写 cancel_reason。
 */
@Service
@RequiredArgsConstructor
public class ChangeRequestServiceImpl implements ChangeRequestService {

    public static final String CANCEL_SUBMITTER_CANCELLED = "提交人已注销账号";
    public static final String CANCEL_FARM_DELETED = "养殖场已删除";

    private final ChangeRequestMapper changeRequestMapper;

    @Override
    public long countPendingSubmittedBy(Long userId, Long farmId) {
        if (userId == null) {
            return 0;
        }
        return TenantSkip.call(() -> changeRequestMapper.countPendingBySubmitter(userId, farmId));
    }

    @Override
    @Transactional
    public int cancelPendingBySubmitter(Long userId, String cancelReason) {
        if (userId == null) {
            return 0;
        }
        String reason = cancelReason == null || cancelReason.isBlank()
                ? CANCEL_SUBMITTER_CANCELLED : truncate(cancelReason, 100);
        return TenantSkip.call(() -> changeRequestMapper.cancelPendingBySubmitter(userId, reason, BeijingTime.now()));
    }

    @Override
    @Transactional
    public int cancelPendingByFarm(Long farmId, String cancelReason) {
        if (farmId == null) {
            return 0;
        }
        String reason = cancelReason == null || cancelReason.isBlank()
                ? CANCEL_FARM_DELETED : truncate(cancelReason, 100);
        return TenantSkip.call(() -> changeRequestMapper.cancelPendingByFarm(farmId, reason, BeijingTime.now()));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
