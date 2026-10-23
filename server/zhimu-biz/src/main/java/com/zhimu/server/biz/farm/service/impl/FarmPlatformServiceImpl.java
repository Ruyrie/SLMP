package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.farm.api.FarmPlatformService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 管理端冻结、解冻（ADM-S17/S18）：暂停待处理邀请与转让，解冻时顺延有效期。 */
@Service
@RequiredArgsConstructor
public class FarmPlatformServiceImpl implements FarmPlatformService {

    private final FarmMapper farmMapper;
    private final FarmMemberMapper memberMapper;
    private final FarmInviteMapper inviteMapper;
    private final FarmTransferMapper transferMapper;
    private final FarmCache cache;
    private final FarmLogs logs;
    private final FarmNotify notify;

    @Override
    @Transactional
    public void freeze(Long farmId, Long adminId, String reason, boolean smsOwner, int version) {
        Farm farm = requireFarm(farmId);
        if (!Objects.equals(version, farm.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        if (Boolean.TRUE.equals(farm.getFrozen())) {
            throw FarmErrors.stateChanged(farm.getName() + "已冻结");
        }
        FarmStatus status = FarmStatuses.of(farm);
        if (status != FarmStatus.NORMAL && status != FarmStatus.DISABLED) {
            throw FarmErrors.stateChanged(farm.getName() + "未通过审核，不能冻结");
        }
        LocalDateTime now = BeijingTime.now();
        int rows = TenantSkip.call(() -> farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                .eq(Farm::getId, farmId)
                .eq(Farm::getVersion, farm.getVersion())
                .set(Farm::getFrozen, true)
                .set(Farm::getFrozenAt, now)
                .set(Farm::getFrozenReason, reason)
                .set(Farm::getFrozenBy, adminId)
                .set(Farm::getUpdatedBy, adminId)
                .setSql("version = version + 1")));
        if (rows == 0) {
            throw FarmErrors.versionConflict();
        }
        farm.setFrozen(true);
        farm.setFrozenAt(now);
        farm.setFrozenReason(reason);
        farm.setFrozenBy(adminId);
        farm.setVersion(farm.getVersion() + 1);
        List<Long> receivers = activeUserIds(farmId);
        cache.evictUsers(farmId, receivers);
        notify.frozen(farm, receivers, reason);
        logs.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("FARM")
                .opAction("FREEZE")
                .bizType(BizType.FARM.name())
                .bizId(farmId)
                .farmId(farmId)
                .targetName(farm.getName())
                .searchText(farm.getName())
                .reason(reason)
                .before(Map.of("frozen", false))
                .after(Map.of("frozen", true))
                .build());
    }

    @Override
    @Transactional
    public void unfreeze(Long farmId, Long adminId, String reason, int version) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.REASON_EMPTY, "请填写原因");
        }
        Farm farm = requireFarm(farmId);
        if (!Objects.equals(version, farm.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        if (!Boolean.TRUE.equals(farm.getFrozen())) {
            throw FarmErrors.stateChanged(farm.getName() + "没有冻结");
        }
        LocalDateTime now = BeijingTime.now();
        Duration delta = farm.getFrozenAt() == null ? Duration.ZERO : Duration.between(farm.getFrozenAt(), now);
        int rows = TenantSkip.call(() -> farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                .eq(Farm::getId, farmId)
                .eq(Farm::getVersion, farm.getVersion())
                .set(Farm::getFrozen, false)
                .set(Farm::getFrozenAt, null)
                .set(Farm::getFrozenReason, null)
                .set(Farm::getFrozenBy, null)
                .set(Farm::getUpdatedBy, adminId)
                .setSql("version = version + 1")));
        if (rows == 0) {
            throw FarmErrors.versionConflict();
        }
        farm.setFrozen(false);
        farm.setFrozenAt(null);
        farm.setFrozenReason(null);
        farm.setFrozenBy(null);
        farm.setVersion(farm.getVersion() + 1);
        if (!delta.isNegative() && !delta.isZero()) {
            shiftInvites(farmId, delta);
            shiftTransfers(farmId, delta);
        }
        List<Long> receivers = activeUserIds(farmId);
        cache.evictUsers(farmId, receivers);
        notify.unfrozen(farm, receivers);
        logs.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("FARM")
                .opAction("UNFREEZE")
                .bizType(BizType.FARM.name())
                .bizId(farmId)
                .farmId(farmId)
                .targetName(farm.getName())
                .searchText(farm.getName())
                .reason(reason)
                .before(Map.of("frozen", true))
                .after(Map.of("frozen", false))
                .build());
    }

    @Override
    public List<Long> listActiveUserIds(Long farmId) {
        return activeUserIds(farmId);
    }

    private List<Long> activeUserIds(Long farmId) {
        return TenantSkip.call(() -> memberMapper.selectList(Wrappers.<FarmMember>lambdaQuery()
                        .eq(FarmMember::getFarmId, farmId)
                        .eq(FarmMember::getActiveFlag, true)
                        .eq(FarmMember::getStatus, "ACTIVE"))
                .stream().map(FarmMember::getUserId).toList());
    }

    private void shiftInvites(Long farmId, Duration delta) {
        List<FarmInvite> invites = TenantSkip.call(() -> inviteMapper.selectList(
                Wrappers.<FarmInvite>lambdaQuery()
                        .eq(FarmInvite::getFarmId, farmId)
                        .eq(FarmInvite::getStatus, "PENDING")));
        for (FarmInvite invite : invites) {
            if (invite.getExpiresAt() == null) {
                continue;
            }
            FarmInvite patch = new FarmInvite();
            patch.setId(invite.getId());
            patch.setExpiresAt(invite.getExpiresAt().plus(delta));
            TenantSkip.run(() -> inviteMapper.updateById(patch));
        }
    }

    private void shiftTransfers(Long farmId, Duration delta) {
        List<FarmTransfer> transfers = TenantSkip.call(() -> transferMapper.selectList(
                Wrappers.<FarmTransfer>lambdaQuery()
                        .eq(FarmTransfer::getFarmId, farmId)
                        .eq(FarmTransfer::getStatus, "PENDING")));
        for (FarmTransfer transfer : transfers) {
            if (transfer.getExpiresAt() == null) {
                continue;
            }
            FarmTransfer patch = new FarmTransfer();
            patch.setId(transfer.getId());
            patch.setExpiresAt(transfer.getExpiresAt().plus(delta));
            TenantSkip.run(() -> transferMapper.updateById(patch));
        }
    }

    private Farm requireFarm(Long farmId) {
        Farm farm = TenantSkip.call(() -> farmMapper.selectIncludingDeleted(farmId));
        if (farm == null) {
            throw FarmErrors.notFound(null);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw FarmErrors.deleted(farm.getName());
        }
        return farm;
    }
}
