package com.zhimu.server.biz.farm.job;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.support.FarmNotify;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmExpireJobTest {

    @Mock private FarmInviteMapper inviteMapper;
    @Mock private FarmTransferMapper transferMapper;
    @Mock private FarmMapper farmMapper;
    @Mock private AccountQueryService accountQuery;
    @Mock private FarmNotify notify;

    private FarmExpireJob job;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        job = new FarmExpireJob(inviteMapper, transferMapper, farmMapper, accountQuery, notify);
        lenient().when(farmMapper.selectIncludingDeleted(1001L)).thenReturn(farm(false, false));
        lenient().when(inviteMapper.update(any(FarmInvite.class), any())).thenReturn(1);
        lenient().when(transferMapper.update(any(FarmTransfer.class), any())).thenReturn(1);
        lenient().when(accountQuery.getBrief(10002L))
                .thenReturn(new AccountBrief(10002L, "刘芳", "13900001234", "NORMAL", false));
    }

    @Test
    void expiresPendingInviteForNonFrozenFarm() {
        when(inviteMapper.selectList(any())).thenReturn(List.of(invite()));
        when(transferMapper.selectList(any())).thenReturn(List.of());

        job.expireInvites(LocalDateTime.now());

        verify(inviteMapper).update(any(FarmInvite.class), any());
    }

    @Test
    void skipsInviteOfFrozenFarm() {
        when(farmMapper.selectIncludingDeleted(1001L)).thenReturn(farm(true, false));
        when(inviteMapper.selectList(any())).thenReturn(List.of(invite()));
        when(transferMapper.selectList(any())).thenReturn(List.of());

        job.expireInvites(LocalDateTime.now());

        verify(inviteMapper, never()).update(any(FarmInvite.class), any());
    }

    @Test
    void expiresTransferAndNotifiesOriginalOwner() {
        when(inviteMapper.selectList(any())).thenReturn(List.of());
        when(transferMapper.selectList(any())).thenReturn(List.of(transfer()));

        job.expireTransfers(LocalDateTime.now());

        verify(transferMapper).update(any(FarmTransfer.class), any());
        verify(notify).transferExpired(any(), any(), any());
    }

    private static Farm farm(boolean frozen, boolean deleted) {
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("东山猪场");
        farm.setFrozen(frozen);
        farm.setDeleted(deleted);
        return farm;
    }

    private static FarmInvite invite() {
        FarmInvite invite = new FarmInvite();
        invite.setId(6001L);
        invite.setFarmId(1001L);
        invite.setStatus("PENDING");
        invite.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return invite;
    }

    private static FarmTransfer transfer() {
        FarmTransfer transfer = new FarmTransfer();
        transfer.setId(7001L);
        transfer.setFarmId(1001L);
        transfer.setFromUserId(10001L);
        transfer.setToUserId(10002L);
        transfer.setStatus("PENDING");
        transfer.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return transfer;
    }
}
