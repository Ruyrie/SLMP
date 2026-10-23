package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.api.FarmPlatformService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.service.impl.FarmPlatformServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmNotify;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmPlatformServiceImplTest {

    @Mock private FarmMapper farmMapper;
    @Mock private FarmMemberMapper memberMapper;
    @Mock private FarmInviteMapper inviteMapper;
    @Mock private FarmTransferMapper transferMapper;
    @Mock private FarmCache cache;
    @Mock private FarmLogs logs;
    @Mock private FarmNotify notify;

    private FarmPlatformService service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmPlatformServiceImpl(farmMapper, memberMapper, inviteMapper, transferMapper,
                cache, logs, notify);
        lenient().when(farmMapper.selectIncludingDeleted(1001L)).thenReturn(farm(false));
        lenient().when(farmMapper.update(any(Farm.class), any())).thenReturn(1);
        lenient().when(memberMapper.selectList(any())).thenReturn(List.of(member(10001L), member(10002L)));
        lenient().when(inviteMapper.selectList(any())).thenReturn(List.of(invite()));
        lenient().when(transferMapper.selectList(any())).thenReturn(List.of(transfer()));
        lenient().when(inviteMapper.updateById(any(FarmInvite.class))).thenReturn(1);
        lenient().when(transferMapper.updateById(any(FarmTransfer.class))).thenReturn(1);
    }

    @Test
    void freezeSetsFlagsEvictsAndNotifiesAllMembers() {
        service.freeze(1001L, 9L, "违规", false, 3);

        verify(farmMapper).update(any(Farm.class), any());
        verify(cache).evictUsers(1001L, List.of(10001L, 10002L));
        verify(notify).frozen(any(), org.mockito.ArgumentMatchers.eq(List.of(10001L, 10002L)),
                org.mockito.ArgumentMatchers.eq("违规"));
    }

    @Test
    void unfreezeShiftsPendingInvitesAndTransfers() {
        Farm frozen = farm(true);
        frozen.setFrozenAt(LocalDateTime.now().minusDays(2));
        when(farmMapper.selectIncludingDeleted(1001L)).thenReturn(frozen);
        FarmInvite invite = invite();
        FarmTransfer transfer = transfer();
        when(inviteMapper.selectList(any())).thenReturn(List.of(invite));
        when(transferMapper.selectList(any())).thenReturn(List.of(transfer));

        service.unfreeze(1001L, 9L, "整改完成", 3);

        ArgumentCaptor<FarmInvite> inviteCaptor = ArgumentCaptor.forClass(FarmInvite.class);
        verify(inviteMapper).updateById(inviteCaptor.capture());
        assertThat(inviteCaptor.getValue().getExpiresAt()).isAfter(invite.getExpiresAt());
        ArgumentCaptor<FarmTransfer> transferCaptor = ArgumentCaptor.forClass(FarmTransfer.class);
        verify(transferMapper).updateById(transferCaptor.capture());
        assertThat(transferCaptor.getValue().getExpiresAt()).isAfter(transfer.getExpiresAt());
        verify(notify).unfrozen(any(), any());
    }

    @Test
    void listActiveUserIdsReturnsActiveMembers() {
        assertThat(service.listActiveUserIds(1001L)).containsExactly(10001L, 10002L);
    }

    private static Farm farm(boolean frozen) {
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("东山猪场");
        farm.setOwnerUserId(10001L);
        farm.setAuditStatus("APPROVED");
        farm.setFrozen(frozen);
        farm.setVersion(3);
        farm.setDeleted(false);
        return farm;
    }

    private static FarmMember member(Long userId) {
        FarmMember member = new FarmMember();
        member.setId(userId == 10001L ? 5001L : 5002L);
        member.setFarmId(1001L);
        member.setUserId(userId);
        member.setRole("OWNER");
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        return member;
    }

    private static FarmInvite invite() {
        FarmInvite invite = new FarmInvite();
        invite.setId(6001L);
        invite.setFarmId(1001L);
        invite.setStatus("PENDING");
        invite.setExpiresAt(LocalDateTime.now().plusDays(1));
        return invite;
    }

    private static FarmTransfer transfer() {
        FarmTransfer transfer = new FarmTransfer();
        transfer.setId(7001L);
        transfer.setFarmId(1001L);
        transfer.setStatus("PENDING");
        transfer.setExpiresAt(LocalDateTime.now().plusDays(1));
        return transfer;
    }
}
