package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmAuditMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.query.FarmListQuery;
import com.zhimu.server.biz.farm.query.FarmListRow;
import com.zhimu.server.biz.farm.request.FarmVersionRequest;
import com.zhimu.server.biz.farm.request.UpdateFarmBasicRequest;
import com.zhimu.server.biz.farm.service.impl.FarmServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNameGuard;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.vo.FarmBasicVO;
import com.zhimu.server.biz.farm.vo.FarmContextVO;
import com.zhimu.server.biz.farm.vo.FarmDetailVO;
import com.zhimu.server.biz.farm.vo.FarmListItemVO;
import com.zhimu.server.common.api.PageVO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmServiceImplTest {

    @Mock private FarmMapper farmMapper;
    @Mock private FarmMemberMapper memberMapper;
    @Mock private FarmAuditMapper auditMapper;
    @Mock private FarmLookup lookup;
    @Mock private FarmAssembler assembler;
    @Mock private FarmNameGuard nameGuard;
    @Mock private FarmCache cache;
    @Mock private FarmLogs logs;
    @Mock private FarmNotify notify;
    @Mock private AccountQueryService accountQuery;
    @Mock private BizMediaService bizMedia;

    private FarmServiceImpl service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmServiceImpl(farmMapper, memberMapper, auditMapper, lookup, assembler,
                nameGuard, cache, logs, notify, accountQuery, bizMedia);
        lenient().when(assembler.penCurrentStock(any())).thenReturn(java.util.Map.of(1001L, 870, 1002L, 18600));
        lenient().when(assembler.listItem(any(), any(), any(), any(), anyBoolean(), any(), any(Integer.class)))
                .thenAnswer(invocation -> {
                    Farm farm = invocation.getArgument(0);
                    FarmListItemVO vo = new FarmListItemVO();
                    vo.setFarmId(farm.getId());
                    vo.setName(farm.getName());
                    return vo;
                });
    }

    @Test
    void listFarmsSortsCurrentFirstAndComputesTabCounts() {
        when(farmMapper.selectRowsOfUser(10001L)).thenReturn(List.of(
                row(1001L, "东山猪场", "OWNER", "APPROVED", false, false,
                        LocalDateTime.of(2026, 10, 19, 7, 30)),
                row(1002L, "西岭鸡场", "MANAGER", "APPROVED", false, false,
                        LocalDateTime.of(2026, 10, 16, 18, 10)),
                row(1003L, "北湾牛场", "OWNER", "PENDING", false, false, null)));
        FarmListQuery query = new FarmListQuery();

        PageVO<FarmListItemVO> page = service.listFarms(10001L, 1003L, query);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getList()).extracting(FarmListItemVO::getFarmId)
                .containsExactly(1003L, 1001L, 1002L);
        assertThat(page.getTabCounts()).containsEntry("all", 3)
                .containsEntry("created", 2).containsEntry("joined", 1)
                .containsEntry("pending_audit", 1);
        assertThat(page.isHasMore()).isFalse();
    }

    @Test
    void switchedPendingFarmReturns40305() {
        when(lookup.findFarmIncludingDeleted(1003L)).thenReturn(farm(1003L, "PENDING", false, false));
        when(lookup.findActiveMember(1003L, 10001L)).thenReturn(member("OWNER"));

        assertThatThrownBy(() -> service.switchFarm(1003L, 10001L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40305);
        verify(accountQuery, never()).recordLastFarm(anyLong(), anyLong());
    }

    @Test
    void switchNormalFarmUpdatesLastSwitchAndRecordsLastFarm() {
        Farm farm = farm(1001L, "APPROVED", false, false);
        FarmMember owner = member("OWNER");
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm);
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(owner);
        when(assembler.context(any(), any())).thenReturn(new FarmContextVO());

        service.switchFarm(1001L, 10001L);

        verify(memberMapper).update(any(FarmMember.class), any());
        verify(accountQuery).recordLastFarm(10001L, 1001L);
    }

    @Test
    void frozenContextReturns40306() {
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm(1001L, "APPROVED", false, true));
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member("OWNER"));

        assertThatThrownBy(() -> service.getContext(1001L, 10001L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40306);
    }

    @Test
    void updateBasicReturnsFullVoAndRejectsEmptyPatch() {
        Farm farm = farm(1001L, "APPROVED", false, false);
        farm.setVersion(3);
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm);
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member("OWNER"));
        when(farmMapper.updateById(any(Farm.class))).thenReturn(1);
        FarmBasicVO basic = new FarmBasicVO();
        basic.setContactName("王建军");
        when(assembler.basic(any(Farm.class))).thenReturn(basic);

        UpdateFarmBasicRequest request = new UpdateFarmBasicRequest();
        request.setContactName("王建军");
        request.setVersion(3);

        FarmBasicVO result = service.updateBasic(1001L, 10001L, request);

        assertThat(result.getContactName()).isEqualTo("王建军");
        assertThat(farm.getContactName()).isEqualTo("王建军");

        UpdateFarmBasicRequest empty = new UpdateFarmBasicRequest();
        empty.setVersion(3);
        assertThatThrownBy(() -> service.updateBasic(1001L, 10001L, empty))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40001);
    }

    @Test
    void enableDisabledFarmReturnsDetailAndEvicts() {
        Farm farm = farm(1001L, "APPROVED", true, false);
        farm.setVersion(2);
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm);
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member("OWNER"));
        when(farmMapper.update(any(Farm.class), any())).thenReturn(1);
        FarmDetailVO detail = new FarmDetailVO();
        detail.setFarmId(1001L);
        detail.setName("东山猪场");
        when(assembler.detail(any(), any())).thenReturn(detail);

        FarmVersionRequest request = new FarmVersionRequest();
        request.setVersion(2);

        FarmDetailVO result = service.enable(1001L, 10001L, request);

        assertThat(result.getName()).isEqualTo("东山猪场");
        assertThat(farm.getDisabled()).isFalse();
        verify(cache).evictFarmMembers(1001L);
        verify(notify).farmEnabled(any(), any());
    }

    @Test
    void deletePendingFarmCancelsAuditAndReturnsName() {
        Farm farm = farm(1003L, "PENDING", false, false);
        when(lookup.findFarmIncludingDeleted(1003L)).thenReturn(farm);
        when(lookup.findActiveMember(1003L, 10001L)).thenReturn(member("OWNER"));
        when(lookup.activeMembers(1003L)).thenReturn(List.of(member("OWNER")));
        when(farmMapper.updateById(any(Farm.class))).thenReturn(1);
        when(farmMapper.deleteById(1003L)).thenReturn(1);
        when(auditMapper.update(any(), any())).thenReturn(1);

        String name = service.deleteFarm(1003L, 10001L);

        assertThat(name).isEqualTo("北湾牛场");
        verify(auditMapper).update(any(), any());
        verify(bizMedia).removeAll(any(), anyLong());
    }

    @Test
    void deleteApprovedFarmReturns40305() {
        when(lookup.findFarmIncludingDeleted(1001L)).thenReturn(farm(1001L, "APPROVED", false, false));
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member("OWNER"));

        assertThatThrownBy(() -> service.deleteFarm(1001L, 10001L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40305);
    }

    private static FarmListRow row(Long id, String name, String role, String auditStatus, boolean disabled,
                                   boolean frozen, LocalDateTime lastSwitch) {
        FarmListRow row = new FarmListRow();
        row.setFarmId(id);
        row.setName(name);
        row.setOwnerUserId(10001L);
        row.setMainCategoryId(101L);
        row.setScale(1500);
        row.setAuditStatus(auditStatus);
        row.setDisabled(disabled);
        row.setFrozen(frozen);
        row.setRole(role);
        row.setJoinType("OWNER".equals(role) ? "CREATE" : "INVITE");
        row.setLastSwitchAt(lastSwitch);
        row.setCreatedAt(LocalDateTime.of(2026, 3, 2, 9, 0));
        row.setMemberId(5001L);
        row.setUserId(10001L);
        row.setVersion(3);
        return row;
    }

    private static Farm farm(Long id, String auditStatus, boolean disabled, boolean frozen) {
        Farm farm = new Farm();
        farm.setId(id);
        farm.setName(id == 1003L ? "北湾牛场" : "东山猪场");
        farm.setOwnerUserId(10001L);
        farm.setAuditStatus(auditStatus);
        farm.setDisabled(disabled);
        farm.setFrozen(frozen);
        farm.setVersion(3);
        farm.setDeleted(false);
        return farm;
    }

    private static FarmMember member(String role) {
        FarmMember member = new FarmMember();
        member.setId(5001L);
        member.setFarmId(1001L);
        member.setUserId(10001L);
        member.setRole(role);
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        return member;
    }
}
