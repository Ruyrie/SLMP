package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmInvitePenMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.MemberPenMapper;
import com.zhimu.server.biz.farm.request.InviteMemberRequest;
import com.zhimu.server.biz.farm.service.impl.FarmInviteServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.vo.InviteAcceptVO;
import com.zhimu.server.biz.farm.vo.InviteItemVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.error.BizException;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmInviteServiceImplTest {

    @Mock private FarmInviteMapper inviteMapper;
    @Mock private FarmInvitePenMapper invitePenMapper;
    @Mock private FarmMemberMapper memberMapper;
    @Mock private MemberPenMapper memberPenMapper;
    @Mock private FarmLookup lookup;
    @Mock private FarmAssembler assembler;
    @Mock private AccountQueryService accountQuery;
    @Mock private PenQueryService penQuery;
    @Mock private FarmCache cache;
    @Mock private FarmLogs logs;
    @Mock private FarmNotify notify;

    private FarmInviteServiceImpl service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmInviteServiceImpl(inviteMapper, invitePenMapper, memberMapper, memberPenMapper,
                lookup, assembler, accountQuery, penQuery, cache, logs, notify);
        lenient().when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm());
        lenient().when(lookup.requireNotDeletedFarm(1001L)).thenReturn(farm());
        lenient().when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member(5001L, 10001L, "OWNER"));
        lenient().when(lookup.findActiveMember(1001L, 10003L)).thenReturn(null);
        lenient().when(accountQuery.getBrief(10001L))
                .thenReturn(new AccountBrief(10001L, "王建国", "13800006620", "NORMAL", false));
        lenient().when(accountQuery.getBrief(10003L))
                .thenReturn(new AccountBrief(10003L, "李明", "13700005678", "NORMAL", false));
        lenient().when(penQuery.listPens(1001L, false)).thenReturn(List.of(
                new PenInfo(2003L, 1001L, "YF-03", "3 号育肥舍", "FATTENING", "育肥舍", 500, "ENABLED")));
        lenient().when(inviteMapper.insert(any(FarmInvite.class))).thenAnswer(invocation -> {
            FarmInvite invite = invocation.getArgument(0);
            invite.setId(6003L);
            return 1;
        });
        lenient().when(invitePenMapper.insert(any(com.zhimu.server.biz.farm.entity.FarmInvitePen.class)))
                .thenReturn(1);
        lenient().when(assembler.inviteItem(any(), any(), anyBoolean())).thenReturn(new InviteItemVO());
        lenient().when(assembler.account(anyLong())).thenReturn(
                new AccountBrief(10001L, "王建国", "13800006620", "NORMAL", false));
    }

    @Test
    void duplicatePendingInviteReturns40001WithPhoneFieldError() {
        FarmInvite pending = new FarmInvite();
        pending.setId(6001L);
        pending.setInviteePhone("13904762051");
        pending.setExpiresAt(LocalDateTime.of(2026, 10, 24, 9, 30));
        when(inviteMapper.selectOne(any())).thenReturn(pending);
        InviteMemberRequest request = request("13904762051", "STAFF", List.of());

        assertThatThrownBy(() -> service.send(1001L, 10001L, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException e = (BizException) error;
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).contains("已向这个手机号发出邀请");
                    assertThat(((Map<?, ?>) e.getData()).get("field_errors")).isNotNull();
                });
        verify(inviteMapper, never()).insert(any(FarmInvite.class));
    }

    @Test
    void sendsInviteAndPreallocatesPensForStaff() {
        when(inviteMapper.selectOne(any())).thenReturn(null);
        when(accountQuery.findByPhone("15804763306")).thenReturn(null);
        InviteMemberRequest request = request("15804763306", "STAFF", List.of(2003L));

        service.send(1001L, 10001L, request);

        ArgumentCaptor<FarmInvite> captor = ArgumentCaptor.forClass(FarmInvite.class);
        verify(inviteMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getPendingFlag()).isTrue();
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
        verify(invitePenMapper).insert(any(com.zhimu.server.biz.farm.entity.FarmInvitePen.class));
    }

    @Test
    void disabledPenOnInviteOnlyReturnsMessageWithoutFieldErrors() {
        when(inviteMapper.selectOne(any())).thenReturn(null);
        when(accountQuery.findByPhone("15804763306")).thenReturn(null);
        InviteMemberRequest request = request("15804763306", "STAFF", List.of(9999L));

        assertThatThrownBy(() -> service.send(1001L, 10001L, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException e = (BizException) error;
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("栏舍已停用，请重新选择");
                    assertThat(e.getData()).isNull();
                });
    }

    @Test
    void acceptExpiredInviteMarksExpiredAndReturns40305() {
        FarmInvite invite = invite(6001L, "PENDING", LocalDateTime.now().minusDays(1));
        when(inviteMapper.selectById(6001L)).thenReturn(invite);
        when(inviteMapper.update(any(FarmInvite.class), any())).thenReturn(1);

        assertThatThrownBy(() -> service.accept(6001L, 10003L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException e = (BizException) error;
                    assertThat(e.getCode()).isEqualTo(40903);
                    assertThat(e.getMessage()).contains("过期");
                });
        assertThat(invite.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void acceptInviteCreatesMemberAndCopiesPens() {
        FarmInvite invite = invite(6001L, "PENDING", LocalDateTime.now().plusDays(3));
        when(inviteMapper.selectById(6001L)).thenReturn(invite);
        when(lookup.findActiveMember(1001L, 10003L)).thenReturn(null);
        when(memberMapper.insert(any(FarmMember.class))).thenAnswer(invocation -> {
            FarmMember member = invocation.getArgument(0);
            member.setId(5006L);
            return 1;
        });
        when(invitePenMapper.selectList(any())).thenReturn(List.of(invitePen(2003L)));
        when(inviteMapper.update(any(FarmInvite.class), any())).thenReturn(1);
        when(memberPenMapper.insert(any(com.zhimu.server.biz.farm.entity.MemberPen.class))).thenReturn(1);

        InviteAcceptVO vo = service.accept(6001L, 10003L);

        assertThat(vo.getFarmId()).isEqualTo(1001L);
        assertThat(vo.getFarmName()).isEqualTo("东山猪场");
        assertThat(vo.getRole()).isEqualTo("STAFF");
        ArgumentCaptor<FarmMember> memberCaptor = ArgumentCaptor.forClass(FarmMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getJoinType()).isEqualTo("INVITE");
        assertThat(memberCaptor.getValue().getActiveFlag()).isTrue();
        verify(memberPenMapper).insert(any(com.zhimu.server.biz.farm.entity.MemberPen.class));
        verify(cache).evict(1001L, 10003L);
        verify(notify).inviteAccepted(any(), any(), any());
    }

    @Test
    void samePhoneDoesNotAllowAnotherAccountToAcceptAnIdentityBoundInvite() {
        FarmInvite invite = invite(6001L, "PENDING", LocalDateTime.now().plusDays(3));
        when(inviteMapper.selectById(6001L)).thenReturn(invite);
        when(accountQuery.getBrief(10991L)).thenReturn(
                new AccountBrief(10991L, "隔离测试账号", invite.getInviteePhone(), "NORMAL", false));

        assertThatThrownBy(() -> service.accept(6001L, 10991L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40401);

        verifyNoInteractions(memberMapper, memberPenMapper, cache, notify);
        verify(inviteMapper, never()).update(any(FarmInvite.class), any());
    }

    @Test
    void acceptingAgainReturnsTheOriginalMembershipWithoutDuplicatingIt() {
        FarmInvite invite = invite(6001L, "ACCEPTED", LocalDateTime.now().plusDays(3));
        invite.setMemberId(5006L);
        when(inviteMapper.selectById(6001L)).thenReturn(invite);
        when(lookup.findActiveMember(1001L, 10003L))
                .thenReturn(member(5006L, 10003L, "STAFF"));

        InviteAcceptVO result = service.accept(6001L, 10003L);

        assertThat(result.getFarmId()).isEqualTo(1001L);
        assertThat(result.getRole()).isEqualTo("STAFF");
        verifyNoInteractions(memberMapper, memberPenMapper, cache, notify);
        verify(inviteMapper, never()).update(any(FarmInvite.class), any());
    }

    @Test
    void oldAcceptedInviteCannotRestoreMembershipAfterLeaving() {
        FarmInvite invite = invite(6001L, "ACCEPTED", LocalDateTime.now().plusDays(3));
        invite.setMemberId(5006L);
        when(inviteMapper.selectById(6001L)).thenReturn(invite);
        when(lookup.findActiveMember(1001L, 10003L)).thenReturn(null);

        assertThatThrownBy(() -> service.accept(6001L, 10003L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40903);

        verifyNoInteractions(memberMapper, memberPenMapper, cache, notify);
        verify(inviteMapper, never()).update(any(FarmInvite.class), any());
    }

    @Test
    void rejectInviteUpdatesStatus() {
        FarmInvite invite = invite(6001L, "PENDING", LocalDateTime.now().plusDays(3));
        when(inviteMapper.selectById(6001L)).thenReturn(invite);
        when(inviteMapper.update(any(FarmInvite.class), any())).thenReturn(1);

        service.reject(6001L, 10003L);

        verify(inviteMapper).update(any(FarmInvite.class), any());
    }

    private static InviteMemberRequest request(String phone, String role, List<Long> penIds) {
        InviteMemberRequest request = new InviteMemberRequest();
        request.setInviteePhone(phone);
        request.setRole(role);
        request.setPenIds(penIds);
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

    private static FarmMember member(Long id, Long userId, String role) {
        FarmMember member = new FarmMember();
        member.setId(id);
        member.setFarmId(1001L);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        return member;
    }

    private static FarmInvite invite(Long id, String status, LocalDateTime expiresAt) {
        FarmInvite invite = new FarmInvite();
        invite.setId(id);
        invite.setFarmId(1001L);
        invite.setInviteePhone("13700005678");
        invite.setInviteeUserId(10003L);
        invite.setRole("STAFF");
        invite.setStatus(status);
        invite.setPendingFlag("PENDING".equals(status));
        invite.setExpiresAt(expiresAt);
        invite.setCreatedAt(LocalDateTime.now().minusDays(1));
        return invite;
    }

    private static com.zhimu.server.biz.farm.entity.FarmInvitePen invitePen(Long penId) {
        com.zhimu.server.biz.farm.entity.FarmInvitePen pen =
                new com.zhimu.server.biz.farm.entity.FarmInvitePen();
        pen.setInviteId(6001L);
        pen.setFarmId(1001L);
        pen.setPenId(penId);
        return pen;
    }
}
