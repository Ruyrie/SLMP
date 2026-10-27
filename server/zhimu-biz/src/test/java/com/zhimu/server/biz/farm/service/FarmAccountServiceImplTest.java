package com.zhimu.server.biz.farm.service;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.api.CancelFarmDisposal;
import com.zhimu.server.biz.farm.api.CancelOwnedFarm;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmAuditMapper;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.mapper.MemberPenMapper;
import com.zhimu.server.biz.farm.query.FarmListRow;
import com.zhimu.server.biz.farm.service.impl.FarmAccountServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmCancellationDataCleaner;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.vo.FarmBriefVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmAccountServiceImplTest {

    @Mock private FarmMapper farmMapper;
    @Mock private FarmMemberMapper memberMapper;
    @Mock private FarmAuditMapper auditMapper;
    @Mock private FarmChangeMapper changeMapper;
    @Mock private FarmInviteMapper inviteMapper;
    @Mock private FarmTransferMapper transferMapper;
    @Mock private MemberPenMapper memberPenMapper;
    @Mock private FarmLookup lookup;
    @Mock private AccountQueryService accountQuery;
    @Mock private BaseDataQueryService baseData;
    @Mock private PenQueryService penQuery;
    @Mock private BizMediaService bizMedia;
    @Mock private FarmCache cache;
    @Mock private FarmNotify notify;
    @Mock private FarmLogs logs;
    @Mock private FarmCancellationDataCleaner cleaner;

    private FarmAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmAccountServiceImpl(farmMapper, memberMapper, auditMapper, changeMapper,
                inviteMapper, transferMapper, memberPenMapper, lookup, accountQuery, baseData, penQuery,
                bizMedia, cache, notify, logs, new AccountProperties(), cleaner);
        when(farmMapper.selectRowsOfUser(10001L)).thenReturn(List.of(
                row(1001L, "东山猪场", "OWNER", "APPROVED", false, false,
                        LocalDateTime.of(2026, 10, 19, 7, 30)),
                row(1002L, "西岭鸡场", "MANAGER", "APPROVED", false, false,
                        LocalDateTime.of(2026, 10, 18, 9, 20)),
                row(1003L, "北湾牛场", "OWNER", "PENDING", false, false, null)));
        lenient().when(baseData.getCategory(101L))
                .thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD", "头", false, true));
        lenient().when(penQuery.farmCurrentStock(any())).thenReturn(Map.of(1001L, 870, 1003L, 0));
        lenient().when(lookup.activeMembers(1001L)).thenReturn(List.of(
                member(5001L, 10001L, "OWNER"), member(5002L, 10002L, "MANAGER")));
        lenient().when(farmMapper.updateById(any(Farm.class))).thenReturn(1);
        lenient().when(farmMapper.deleteById(1001L)).thenReturn(1);
        lenient().when(memberMapper.update(any(FarmMember.class), any())).thenReturn(1);
        lenient().when(memberPenMapper.delete(any())).thenReturn(1);
        lenient().when(auditMapper.update(any(), any())).thenReturn(1);
        lenient().when(changeMapper.update(any(), any())).thenReturn(1);
        lenient().when(inviteMapper.update(any(), any())).thenReturn(1);
        lenient().when(transferMapper.update(any(), any())).thenReturn(1);
    }

    @Test
    void resolveDefaultFarmPrefersLastFarmWhenNotFrozen() {
        assertThat(service.resolveDefaultFarm(10001L, 1002L)).isEqualTo(1002L);
        assertThat(service.resolveDefaultFarm(10001L, 1003L)).isEqualTo(1003L);
    }

    @Test
    void resolveDefaultFarmUsesF01OrderWhenLastFarmInvalid() {
        assertThat(service.resolveDefaultFarm(10001L, 9999L)).isEqualTo(1001L);
        assertThat(service.resolveDefaultFarm(10001L, null)).isEqualTo(1001L);
    }

    @Test
    void findMyFarmBriefReturnsRoleAndStatus() {
        FarmBriefVO brief = service.findMyFarmBrief(10001L, 1001L);
        assertThat(brief).isNotNull();
        assertThat(brief.getName()).isEqualTo("东山猪场");
        assertThat(brief.getRole()).isEqualTo("OWNER");
        assertThat(brief.getSpeciesText()).isEqualTo("猪");
        assertThat(service.findMyFarmBrief(10001L, 9999L)).isNull();
    }

    @Test
    void farmStatsCountsAllMembershipsAndOwnedAuditStates() {
        var stats = service.farmStats(10001L);
        assertThat(stats.totalCount()).isEqualTo(3);
        assertThat(stats.pendingAuditCount()).isEqualTo(1);
        assertThat(stats.rejectedCount()).isZero();
    }

    @Test
    void ownedFarmsForCancelDescribeDisposalStockAndMembers() {
        List<CancelOwnedFarm> farms = service.listOwnedFarmsForCancel(10001L);
        assertThat(farms).hasSize(2);
        CancelOwnedFarm normal = farms.stream().filter(f -> f.farmId() == 1001L).findFirst().orElseThrow();
        assertThat(normal.disposal()).isEqualTo(CancelFarmDisposal.TRANSFER_OR_DISABLE);
        assertThat(normal.currentStock()).isEqualTo(870);
        assertThat(normal.memberCount()).isEqualTo(1);
        assertThat(normal.transferable()).isTrue();
        assertThat(normal.countUnitText()).isEqualTo("头");
        CancelOwnedFarm pending = farms.stream().filter(f -> f.farmId() == 1003L).findFirst().orElseThrow();
        assertThat(pending.disposal()).isEqualTo(CancelFarmDisposal.DELETE_NOW);
        assertThat(pending.currentStock()).isNull();
    }

    @Test
    void exitFarmsExcludeOwnedFarms() {
        assertThat(service.listExitFarmsForCancel(10001L)).extracting("farmId").containsExactly(1002L);
    }

    @Test
    void accountCancellationDeletesUnapprovedEmptyFarmAndLeavesOtherFarm() {
        when(farmMapper.selectRowsOfUser(10001L)).thenReturn(List.of(
                row(1001L, "东山猪场", "OWNER", "PENDING", false, false,
                        LocalDateTime.of(2026, 10, 19, 7, 30)),
                row(1002L, "西岭鸡场", "MANAGER", "APPROVED", false, false,
                        LocalDateTime.of(2026, 10, 18, 9, 20))));
        when(lookup.findActiveMember(1002L, 10001L)).thenReturn(member(5005L, 10001L, "MANAGER"));
        when(lookup.activeMembers(1001L)).thenReturn(List.of(
                member(5001L, 10001L, "OWNER"), member(5002L, 10002L, "MANAGER")));
        Farm owned = retained(1001L, null);
        owned.setAuditStatus("PENDING"); owned.setDisabled(false);
        when(farmMapper.lockOwnedForCancellation(1001L, 10001L)).thenReturn(owned);

        service.handleAccountCancelled(10001L, "13800006620");

        verify(cleaner).purge(eq(1001L), eq(10001L), any());
        verify(farmMapper, never()).deleteById(any(Long.class));
        verify(notify).farmDeleted(any(), any());
    }

    @Test
    void cancelledOwnerKeepsDisabledFarmAndOtherMembersUntilItsOriginalDeadline() {
        LocalDateTime disabledAt = LocalDateTime.of(2026, 10, 1, 14, 30);
        when(farmMapper.selectRowsOfUser(10001L)).thenReturn(List.of(row(1001L, "保留场", "OWNER", "APPROVED", true, false, null)));
        Farm farm = retained(1001L, disabledAt);
        when(farmMapper.lockOwnedForCancellation(1001L, 10001L)).thenReturn(farm);

        service.handleAccountCancelled(10001L, null);

        assertThat(farm.getDeleted()).isFalse();
        assertThat(farm.getDeletedAt()).isNull();
        assertThat(farm.getDisabledAt()).isEqualTo(disabledAt);
        assertThat(farm.getDeleteType()).isEqualTo("ACCOUNT_CANCEL");
        verify(cleaner, never()).purge(any(), any(), any());
        verify(notify, never()).farmDeleted(any(), any());
        verify(notify).ownerCancelledRetained(farm, List.of(10002L), disabledAt.plusDays(180));
        verify(cache).evictUsers(1001L, List.of(10001L, 10002L));
    }

    @Test
    void cleanupPurgesOnlyDueFarmAndReturnsLaterFarmExactDeadline() {
        LocalDateTime now = LocalDateTime.of(2027, 4, 1, 14, 30);
        cancelledOwner();
        Farm due = retained(1001L, now.minusDays(180));
        Farm later = retained(1002L, now.minusDays(179));
        when(farmMapper.lockCancelledOwnerFarms(10001L)).thenReturn(List.of(due, later));

        assertThat(service.cleanupCancelledOwnerFarms(10001L, now)).isEqualTo(now.plusDays(1));

        verify(cleaner).purge(1001L, 10001L, now);
        verify(cleaner, never()).purge(eq(1002L), any(), any());
        verify(notify).farmDeleted(eq(due), any());
        verify(notify, never()).farmDeleted(eq(later), any());
    }

    @Test
    void cleanupDoesNotTouchActiveOwnerOrTransferredFarm() {
        LocalDateTime now = LocalDateTime.of(2027, 4, 1, 14, 30);
        when(accountQuery.getBrief(10001L)).thenReturn(new AccountBrief(10001L, "正常所有者", null, "NORMAL", false));
        assertThat(service.cleanupCancelledOwnerFarms(10001L, now)).isNull();
        verify(farmMapper, never()).lockCancelledOwnerFarms(any());
        verifyNoInteractions(cleaner);

        cancelledOwner();
        Farm transferred = retained(1001L, now.minusDays(181));
        transferred.setOwnerUserId(10002L);
        when(farmMapper.lockCancelledOwnerFarms(10001L)).thenReturn(List.of(transferred));
        assertThat(service.cleanupCancelledOwnerFarms(10001L, now)).isNull();
        verifyNoInteractions(cleaner);
    }

    @Test
    void cleanupWithoutReliableDisabledDateStartsFullRetentionAndRetriesSafely() {
        LocalDateTime now = LocalDateTime.of(2027, 4, 1, 14, 30);
        cancelledOwner();
        Farm legacy = retained(1001L, null); legacy.setDeleted(true);
        when(farmMapper.lockCancelledOwnerFarms(10001L)).thenReturn(List.of(legacy));
        when(farmMapper.initializeMissingRetentionStart(1001L, 10001L, now)).thenReturn(1);
        assertThat(service.cleanupCancelledOwnerFarms(10001L, now)).isEqualTo(now.plusDays(180));
        verify(cleaner, never()).purge(any(), any(), any());
    }

    @Test
    void frozenFarmIsKeptAndCompletedCleanupCanBeRetriedWithoutAnotherDelete() {
        LocalDateTime now = LocalDateTime.of(2027, 4, 1, 14, 30);
        cancelledOwner();
        Farm frozen = retained(1001L, now.minusDays(181)); frozen.setFrozen(true);
        when(farmMapper.lockCancelledOwnerFarms(10001L)).thenReturn(List.of(frozen), List.of());
        assertThat(service.cleanupCancelledOwnerFarms(10001L, now)).isEqualTo(now.plusDays(1));
        assertThat(service.cleanupCancelledOwnerFarms(10001L, now)).isNull();
        verifyNoInteractions(cleaner);
    }

    @Test
    void unapprovedFarmWithBusinessDataIsNotDeleted() {
        when(farmMapper.selectRowsOfUser(10001L)).thenReturn(List.of(row(1001L, "异常场", "OWNER", "PENDING", false, false, null)));
        Farm pending = retained(1001L, null); pending.setDisabled(false); pending.setAuditStatus("PENDING");
        when(farmMapper.lockOwnedForCancellation(1001L, 10001L)).thenReturn(pending);
        when(cleaner.hasOperationalData(1001L)).thenReturn(true);
        assertThatThrownBy(() -> service.handleAccountCancelled(10001L, null)).hasMessageContaining("存在业务数据");
        verify(cleaner, never()).purge(any(), any(), any());
    }

    private void cancelledOwner() {
        when(accountQuery.getBrief(10001L)).thenReturn(new AccountBrief(10001L, "已注销用户", null, "CANCELLED", true));
    }

    private static Farm retained(long id, LocalDateTime disabledAt) {
        Farm farm = new Farm(); farm.setId(id); farm.setName("保留场"); farm.setOwnerUserId(10001L);
        farm.setAuditStatus("APPROVED"); farm.setDisabled(true); farm.setFrozen(false);
        farm.setDisabledAt(disabledAt); farm.setDeleteType("ACCOUNT_CANCEL"); farm.setDeleted(false); farm.setVersion(1);
        return farm;
    }

    private static FarmListRow row(Long farmId, String name, String role, String auditStatus,
                                   boolean disabled, boolean frozen, LocalDateTime lastSwitch) {
        FarmListRow row = new FarmListRow();
        row.setFarmId(farmId);
        row.setName(name);
        row.setOwnerUserId(10001L);
        row.setMainCategoryId(101L);
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

    private static FarmMember member(Long id, Long userId, String role) {
        FarmMember member = new FarmMember();
        member.setId(id);
        member.setFarmId(1001L);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        member.setVersion(1);
        return member;
    }
}
