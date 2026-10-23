package com.zhimu.server.biz.farm.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邀请与转让过期任务（模块02 文档 2.8）：每 5 分钟，把 expires_at 已到、养殖场未冻结的 PENDING 记录置 EXPIRED，
 * pending_flag 置空、handled_at 写过期时间；每批 500 条。转让过期通知原所有者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FarmExpireJob {

    static final int BATCH = 500;

    private final FarmInviteMapper inviteMapper;
    private final FarmTransferMapper transferMapper;
    private final FarmMapper farmMapper;
    private final AccountQueryService accountQuery;
    private final FarmNotify notify;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    @JobLock("farm-expire")
    public void expire() {
        LocalDateTime now = BeijingTime.now();
        expireInvites(now);
        expireTransfers(now);
    }

    void expireInvites(LocalDateTime now) {
        List<FarmInvite> invites = TenantSkip.call(() -> inviteMapper.selectList(
                Wrappers.<FarmInvite>lambdaQuery()
                        .eq(FarmInvite::getStatus, "PENDING")
                        .le(FarmInvite::getExpiresAt, now)
                        .orderByAsc(FarmInvite::getExpiresAt)
                        .last("LIMIT " + BATCH)));
        int count = 0;
        for (FarmInvite invite : invites) {
            Farm farm = farm(invite.getFarmId());
            if (farm == null || Boolean.TRUE.equals(farm.getFrozen()) || Boolean.TRUE.equals(farm.getDeleted())) {
                continue;
            }
            int rows = TenantSkip.call(() -> inviteMapper.update(new FarmInvite(),
                    Wrappers.<FarmInvite>lambdaUpdate()
                            .eq(FarmInvite::getId, invite.getId())
                            .eq(FarmInvite::getStatus, "PENDING")
                            .set(FarmInvite::getStatus, "EXPIRED")
                            .set(FarmInvite::getPendingFlag, null)
                            .set(FarmInvite::getHandledAt, now)));
            count += rows;
        }
        if (count > 0) {
            log.info("邀请过期任务：处理 {} 条", count);
        }
    }

    void expireTransfers(LocalDateTime now) {
        List<FarmTransfer> transfers = TenantSkip.call(() -> transferMapper.selectList(
                Wrappers.<FarmTransfer>lambdaQuery()
                        .eq(FarmTransfer::getStatus, "PENDING")
                        .le(FarmTransfer::getExpiresAt, now)
                        .orderByAsc(FarmTransfer::getExpiresAt)
                        .last("LIMIT " + BATCH)));
        int count = 0;
        for (FarmTransfer transfer : transfers) {
            Farm farm = farm(transfer.getFarmId());
            if (farm == null || Boolean.TRUE.equals(farm.getFrozen()) || Boolean.TRUE.equals(farm.getDeleted())) {
                continue;
            }
            int rows = TenantSkip.call(() -> transferMapper.update(new FarmTransfer(),
                    Wrappers.<FarmTransfer>lambdaUpdate()
                            .eq(FarmTransfer::getId, transfer.getId())
                            .eq(FarmTransfer::getStatus, "PENDING")
                            .set(FarmTransfer::getStatus, "EXPIRED")
                            .set(FarmTransfer::getPendingFlag, null)
                            .set(FarmTransfer::getHandledAt, now)));
            if (rows > 0) {
                count += rows;
                String toName = accountQuery.getBrief(transfer.getToUserId()) == null ? "对方"
                        : accountQuery.getBrief(transfer.getToUserId()).name();
                notify.transferExpired(farm, transfer, toName);
            }
        }
        if (count > 0) {
            log.info("转让过期任务：处理 {} 条", count);
        }
    }

    private Farm farm(Long farmId) {
        return TenantSkip.call(() -> farmMapper.selectIncludingDeleted(farmId));
    }
}
