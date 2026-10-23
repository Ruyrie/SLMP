package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.mapper.MemberPenMapper;
import com.zhimu.server.biz.farm.request.UpdateMemberPensRequest;
import com.zhimu.server.biz.farm.request.UpdateMemberRoleRequest;
import com.zhimu.server.biz.farm.service.impl.FarmMemberServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.vo.MemberChangeResultVO;
import com.zhimu.server.biz.farm.vo.MemberDetailVO;
import com.zhimu.server.biz.farm.vo.MemberRemoveResultVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.vo.PenBriefVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmMemberServiceImplTest {

    @Mock private FarmMemberMapper memberMapper;
    @Mock private MemberPenMapper memberPenMapper;
    @Mock private FarmTransferMapper transferMapper;
    @Mock private FarmLookup lookup;
    @Mock private FarmAssembler assembler;
    @Mock private FarmCache cache;
    @Mock private FarmLogs logs;
    @Mock private FarmNotify notify;
    @Mock private PenQueryService penQuery;

    private FarmMemberServiceImpl service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmMemberServiceImpl(memberMapper, memberPenMapper, transferMapper, lookup,
                assembler, cache, logs, notify, penQuery);
        lenient().when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm());
        lenient().when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member(5001L, 10001L, "OWNER", 1));
        lenient().when(lookup.activeMembers(1001L)).thenReturn(List.of(
                member(5001L, 10001L, "OWNER", 1), member(5003L, 10003L, "STAFF", 1)));
        lenient().when(lookup.pensOfFarm(1001L)).thenReturn(List.of());
        lenient().when(assembler.memberDetail(any(), any(), any(), any(), any()))
                .thenReturn(memberDetail());
        lenient().when(assembler.penBriefs(any(), anyLong())).thenAnswer(invocation -> {
            java.util.List<Long> penIds = invocation.getArgument(0);
            return penIds.stream().map(penId -> new PenBriefVO(penId, penId + " 号育肥舍", "育肥舍"))
                    .toList();
        });
        lenient().when(assembler.account(anyLong())).thenReturn(null);
        lenient().when(penQuery.listPens(1001L, false)).thenReturn(List.of(
                new PenInfo(2003L, 1001L, "YF-03", "3 号育肥舍", "FATTENING", "育肥舍", 500, "ENABLED"),
                new PenInfo(2004L, 1001L, "YF-04", "4 号育肥舍", "FATTENING", "育肥舍", 500, "ENABLED")));
    }

    @Test
    void roleChangeToManagerClearsPensAndReturnsNewlyUnassigned() {
        FarmMember staff = member(5003L, 10003L, "STAFF", 1);
        when(lookup.findActiveMemberById(1001L, 5003L)).thenReturn(staff);
        when(lookup.memberPens(1001L, 5003L)).thenReturn(List.of(memberPen(2003L), memberPen(2004L)));
        when(memberMapper.updateById(any(FarmMember.class))).thenReturn(1);
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole("MANAGER");
        request.setVersion(1);

        MemberChangeResultVO result = service.updateRole(1001L, 5003L, 10001L, request);

        assertThat(staff.getRole()).isEqualTo("MANAGER");
        assertThat(result.getNewlyUnassignedPens()).hasSize(2);
        verify(memberPenMapper).delete(any());
        verify(cache).evict(1001L, 10003L);
        verify(notify).roleChanged(any(), any(), any());
    }

    @Test
    void pensAdjustmentReturnsOnlyPensWithoutOtherStaff() {
        FarmMember staff = member(5003L, 10003L, "STAFF", 2);
        when(lookup.findActiveMemberById(1001L, 5003L)).thenReturn(staff);
        when(lookup.memberPens(1001L, 5003L)).thenReturn(List.of(memberPen(2003L), memberPen(2004L)));
        when(memberMapper.updateById(any(FarmMember.class))).thenReturn(1);
        when(memberPenMapper.insert(any(MemberPen.class))).thenReturn(1);
        UpdateMemberPensRequest request = new UpdateMemberPensRequest();
        request.setPenIds(List.of(2004L));
        request.setVersion(2);

        MemberChangeResultVO result = service.updatePens(1001L, 5003L, 10001L, request);

        assertThat(result.getNewlyUnassignedPens()).hasSize(1);
        assertThat(result.getNewlyUnassignedPens().get(0).getPenId()).isEqualTo(2003L);
        verify(memberPenMapper).delete(any());
        verify(memberPenMapper).insert(any(MemberPen.class));
        verify(cache).evict(1001L, 10003L);
    }

    @Test
    void pensAdjustmentRejectsNonStaff() {
        FarmMember manager = member(5002L, 10002L, "MANAGER", 1);
        when(lookup.findActiveMemberById(1001L, 5002L)).thenReturn(manager);
        UpdateMemberPensRequest request = new UpdateMemberPensRequest();
        request.setPenIds(List.of());
        request.setVersion(1);

        assertThatThrownBy(() -> service.updatePens(1001L, 5002L, 10001L, request))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40903);
    }

    @Test
    void removeStaffReturnsUnassignedPensAndNotifies() {
        FarmMember staff = member(5003L, 10003L, "STAFF", 2);
        when(lookup.findActiveMemberById(1001L, 5003L)).thenReturn(staff);
        when(lookup.memberPens(1001L, 5003L)).thenReturn(List.of(memberPen(2003L)));
        when(memberMapper.update(any(FarmMember.class), any())).thenReturn(1);
        when(transferMapper.update(any(), any())).thenReturn(1);

        MemberRemoveResultVO result = service.remove(1001L, 5003L, 10001L, 2);

        assertThat(result.getUnassignedPens()).hasSize(1);
        assertThat(result.getUnassignedPens().get(0).getPenId()).isEqualTo(2003L);
        assertThat(staff.getStatus()).isEqualTo("REMOVED");
        verify(notify).memberRemoved(any(), any());
        verify(cache).evict(1001L, 10003L);
    }

    @Test
    void ownerCannotQuit() {
        assertThatThrownBy(() -> service.quit(1001L, 10001L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40303);
    }

    @Test
    void staffSummaryRequiresManagerView() {
        when(lookup.findActiveMember(1001L, 10003L)).thenReturn(member(5003L, 10003L, "STAFF", 1));
        assertThatThrownBy(() -> service.summary(1001L, 10003L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40303);
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

    private static MemberPen memberPen(Long penId) {
        MemberPen pen = new MemberPen();
        pen.setFarmId(1001L);
        pen.setMemberId(5003L);
        pen.setPenId(penId);
        return pen;
    }

    private static MemberDetailVO memberDetail() {
        MemberDetailVO vo = new MemberDetailVO();
        vo.setMemberId(5003L);
        vo.setName("李明");
        vo.setRole("MANAGER");
        return vo;
    }
}
