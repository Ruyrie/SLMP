package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.request.CreateTransferRequest;
import com.zhimu.server.biz.farm.service.impl.FarmTransferServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNameGuard;
import com.zhimu.server.biz.farm.support.FarmNameLock;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.vo.TransferAcceptVO;
import com.zhimu.server.biz.farm.vo.TransferCandidateVO;
import com.zhimu.server.biz.farm.vo.TransferFarmVO;
import com.zhimu.server.biz.farm.vo.TransferPageVO;
import com.zhimu.server.biz.farm.vo.TransferVO;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmTransferServiceImplTest {

    @Mock private FarmTransferMapper transferMapper;
    @Mock private FarmMemberMapper memberMapper;
    @Mock private FarmMapper farmMapper;
    @Mock private FarmLookup lookup;
    @Mock private FarmAssembler assembler;
    @Mock private FarmNameGuard nameGuard;
    @Mock private FarmNameLock nameLock;
    @Mock private FarmCache cache;
    @Mock private FarmLogs logs;
    @Mock private FarmNotify notify;

    private FarmTransferServiceImpl service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmTransferServiceImpl(transferMapper, memberMapper, farmMapper, lookup, assembler,
                nameGuard, nameLock, cache, logs, notify);
        lenient().when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm());
        lenient().when(lookup.requireNotDeletedFarm(1001L)).thenReturn(farm());
        lenient().when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member(5001L, 10001L, "OWNER", 1));
        lenient().when(lookup.findActiveMember(1001L, 10002L)).thenReturn(member(5002L, 10002L, "MANAGER", 1));
        lenient().when(lookup.findActiveMemberById(1001L, 5002L)).thenReturn(member(5002L, 10002L, "MANAGER", 1));
        lenient().when(lookup.activeMembers(1001L)).thenReturn(List.of(
                member(5001L, 10001L, "OWNER", 1), member(5002L, 10002L, "MANAGER", 1)));
        lenient().when(assembler.account(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (id == 10001L) {
                return new AccountBrief(10001L, "王建国", "13800006620", "NORMAL", false);
            }
            return new AccountBrief(10002L, "刘芳", "13900001234", "NORMAL", false);
        });
        lenient().when(assembler.transferFarm(any())).thenReturn(new TransferFarmVO());
        lenient().when(assembler.transfer(any(), any())).thenReturn(null);
        lenient().when(assembler.transfer(any(), any(), any(), any())).thenReturn(new TransferVO());
        lenient().when(assembler.candidate(any(), any())).thenReturn(new TransferCandidateVO());
        lenient().doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(nameLock).call(anyLong(), any());
        lenient().when(transferMapper.insert(any(FarmTransfer.class))).thenAnswer(invocation -> {
            FarmTransfer transfer = invocation.getArgument(0);
            transfer.setId(7001L);
            return 1;
        });
        lenient().when(transferMapper.update(any(FarmTransfer.class), any())).thenReturn(1);
        lenient().when(memberMapper.updateById(any(FarmMember.class))).thenReturn(1);
        lenient().when(farmMapper.update(any(Farm.class), any())).thenReturn(1);
    }

    @Test
    void pageReturnsManagersAsCandidates() {
        TransferPageVO vo = service.page(1001L, 10001L);

        assertThat(vo.getFarm()).isNotNull();
        assertThat(vo.getCandidates()).hasSize(1);
        assertThat(vo.getPending()).isNull();
    }

    @Test
    void createRejectsTargetThatIsNotManager() {
        when(lookup.findActiveMemberById(1001L, 5003L)).thenReturn(member(5003L, 10003L, "STAFF", 1));
        CreateTransferRequest request = request(5003L);

        assertThatThrownBy(() -> service.create(1001L, 10001L, request))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40903);
    }

    @Test
    void createRejectsSecondPendingTransfer() {
        when(assembler.pendingTransfer(1001L)).thenReturn(pending());
        CreateTransferRequest request = request(5002L);

        assertThatThrownBy(() -> service.create(1001L, 10001L, request))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40903);
    }

    @Test
    void createSendsTransferAndNotifies() {
        when(assembler.pendingTransfer(1001L)).thenReturn(null);
        when(nameGuard.exists(10002L, "东山猪场", null)).thenReturn(false);
        CreateTransferRequest request = request(5002L);

        service.create(1001L, 10001L, request);

        verify(transferMapper).insert(any(FarmTransfer.class));
        verify(notify).transferCreated(any(), any(), any());
    }

    @Test
    void acceptSwapsRolesAndOwner() {
        FarmTransfer transfer = pending();
        when(transferMapper.selectById(7001L)).thenReturn(transfer);
        when(nameGuard.exists(10002L, "东山猪场", null)).thenReturn(false);

        TransferAcceptVO vo = service.accept(7001L, 10002L);

        assertThat(vo.getFarmId()).isEqualTo(1001L);
        assertThat(vo.getRole()).isEqualTo("OWNER");
        FarmMember target = lookup.findActiveMember(1001L, 10002L);
        assertThat(target.getRole()).isEqualTo("OWNER");
        FarmMember old = lookup.findActiveMember(1001L, 10001L);
        assertThat(old.getRole()).isEqualTo("MANAGER");
        verify(transferMapper).update(any(FarmTransfer.class), any());
        verify(farmMapper).update(any(Farm.class), any());
        verify(cache).evictUsers(1001L, List.of(10001L, 10002L));
        verify(notify).transferAccepted(any(), any(), any());
    }

    @Test
    void acceptWithDuplicateNameReturns40905() {
        when(transferMapper.selectById(7001L)).thenReturn(pending());
        when(nameGuard.exists(10002L, "东山猪场", null)).thenReturn(true);

        assertThatThrownBy(() -> service.accept(7001L, 10002L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40905);
    }

    @Test
    void nonRecipientCannotAccept() {
        when(transferMapper.selectById(7001L)).thenReturn(pending());
        assertThatThrownBy(() -> service.accept(7001L, 10003L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40401);
    }

    @Test
    void cancelByOtherUserReturns40401() {
        when(transferMapper.selectById(7001L)).thenReturn(pending());
        assertThatThrownBy(() -> service.cancel(7001L, 10002L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40401);
    }

    @Test
    void rejectUpdatesStatusAndNotifies() {
        when(transferMapper.selectById(7001L)).thenReturn(pending());
        service.reject(7001L, 10002L);
        verify(transferMapper).update(any(FarmTransfer.class), any());
        verify(notify).transferRejected(any(), any(), any());
    }

    private static CreateTransferRequest request(Long memberId) {
        CreateTransferRequest request = new CreateTransferRequest();
        request.setToMemberId(memberId);
        return request;
    }

    private static Farm farm() {
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("东山猪场");
        farm.setOwnerUserId(10001L);
        farm.setAuditStatus("APPROVED");
        farm.setDisabled(false);
        farm.setFrozen(false);
        farm.setVersion(3);
        farm.setDeleted(false);
        return farm;
    }

    private static FarmMember member(Long id, Long userId, String role, int version) {
        FarmMember member = new FarmMember();
        member.setId(id);
        member.setFarmId(1001L);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        member.setVersion(version);
        return member;
    }

    private static FarmTransfer pending() {
        FarmTransfer transfer = new FarmTransfer();
        transfer.setId(7001L);
        transfer.setFarmId(1001L);
        transfer.setFromUserId(10001L);
        transfer.setToUserId(10002L);
        transfer.setToMemberId(5002L);
        transfer.setStatus("PENDING");
        transfer.setPendingFlag(true);
        transfer.setExpiresAt(LocalDateTime.now().plusDays(3));
        return transfer;
    }
}

