package com.zhimu.server.biz.farm;

import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmMemberLoaderImplTest {

    @Mock
    private FarmLookup lookup;

    private FarmMemberLoaderImpl loader;

    @BeforeEach
    void setUp() {
        loader = new FarmMemberLoaderImpl(lookup);
    }

    @Test
    void returnsNullForDeletedFarm() {
        when(lookup.findFarm(1001L)).thenReturn(null);
        assertThat(loader.load(1001L, 10001L)).isNull();
    }

    @Test
    void returnsNullForNonMember() {
        when(lookup.findFarm(1001L)).thenReturn(farm(false, false, "APPROVED"));
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(null);
        assertThat(loader.load(1001L, 10001L)).isNull();
    }

    @Test
    void derivesFrozenBeforeDisabledAndAudit() {
        Farm farm = farm(true, true, "PENDING");
        FarmMember member = member("OWNER", 5001L);
        when(lookup.findFarm(1001L)).thenReturn(farm);
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member);

        FarmContext.FarmMember result = loader.load(1001L, 10001L);

        assertThat(result).isNotNull();
        assertThat(result.farmStatus()).isEqualTo(FarmStatus.FROZEN);
        assertThat(result.role()).isEqualTo(FarmRole.OWNER);
        assertThat(result.penIds()).isEmpty();
        assertThat(result.farmName()).isEqualTo("东山猪场");
    }

    @Test
    void derivesDisabledThenPendingThenRejectedThenNormal() {
        when(lookup.findActiveMember(1001L, 10001L)).thenReturn(member("MANAGER", 5002L));
        when(lookup.findFarm(1001L)).thenReturn(farm(false, true, "APPROVED"));
        assertThat(loader.load(1001L, 10001L).farmStatus()).isEqualTo(FarmStatus.DISABLED);

        when(lookup.findFarm(1001L)).thenReturn(farm(false, false, "PENDING"));
        assertThat(loader.load(1001L, 10001L).farmStatus()).isEqualTo(FarmStatus.PENDING);

        when(lookup.findFarm(1001L)).thenReturn(farm(false, false, "REJECTED"));
        assertThat(loader.load(1001L, 10001L).farmStatus()).isEqualTo(FarmStatus.REJECTED);

        when(lookup.findFarm(1001L)).thenReturn(farm(false, false, "APPROVED"));
        assertThat(loader.load(1001L, 10001L).farmStatus()).isEqualTo(FarmStatus.NORMAL);
    }

    @Test
    void staffPenIdsAreLoadedCompletely() {
        FarmMember staff = member("STAFF", 5003L);
        when(lookup.findFarm(1001L)).thenReturn(farm(false, false, "APPROVED"));
        when(lookup.findActiveMember(1001L, 10003L)).thenReturn(staff);
        MemberPen first = new MemberPen();
        first.setPenId(2003L);
        MemberPen second = new MemberPen();
        second.setPenId(2004L);
        when(lookup.memberPens(1001L, 5003L)).thenReturn(List.of(first, second));

        FarmContext.FarmMember result = loader.load(1001L, 10003L);

        assertThat(result.penIds()).containsExactlyInAnyOrder(2003L, 2004L);
        assertThat(result.role()).isEqualTo(FarmRole.STAFF);
    }

    @Test
    void missingIdsReturnNull() {
        assertThat(loader.load(null, 10001L)).isNull();
        assertThat(loader.load(1001L, null)).isNull();
    }

    private static Farm farm(boolean frozen, boolean disabled, String auditStatus) {
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("东山猪场");
        farm.setFrozen(frozen);
        farm.setDisabled(disabled);
        farm.setAuditStatus(auditStatus);
        farm.setDeleted(false);
        return farm;
    }

    private static FarmMember member(String role, Long id) {
        FarmMember member = new FarmMember();
        member.setId(id);
        member.setFarmId(1001L);
        member.setUserId(10001L);
        member.setRole(role);
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        return member;
    }
}
