package com.zhimu.server.biz.account.support;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.CancelBlockReason;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.biz.farm.api.CancelExitFarm;
import com.zhimu.server.biz.farm.api.CancelFarmDisposal;
import com.zhimu.server.biz.farm.api.CancelOwnedFarm;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CancellationRulesTest {

    private final FarmAccountService farmAccountService = mock(FarmAccountService.class);
    private final AccountProperties properties = new AccountProperties();
    private CancellationRules rules;

    @BeforeEach
    void setUp() {
        FixedTime.set();
        rules = new CancellationRules(farmAccountService, properties);
    }

    @AfterEach
    void tearDown() {
        FixedTime.reset();
    }

    private static User user() {
        User user = new User();
        user.setId(10001L);
        user.setPhone("13800006620");
        user.setRecoveredAt(LocalDateTime.of(2026, 10, 19, 8, 0));
        return user;
    }

    private static CancelOwnedFarm farm(long id, String name, String status, Integer stock, int members,
                                        CancelFarmDisposal disposal, boolean transferable, String pendingTo,
                                        LocalDateTime disabledAt) {
        return new CancelOwnedFarm(id, name, status, "正常", stock, "头", members, disposal, transferable, pendingTo,
                disabledAt);
    }

    @Test
    void 找回后7天内与正常养殖场均阻止注销_文案带场名() {
        when(farmAccountService.listOwnedFarmsForCancel(10001L)).thenReturn(List.of(
                farm(1001, "东山猪场", "NORMAL", 870, 3, CancelFarmDisposal.TRANSFER_OR_DISABLE, true, null, null)));
        when(farmAccountService.listExitFarmsForCancel(10001L)).thenReturn(List.of(
                new CancelExitFarm(1002L, "西岭鸡场", "MANAGER", "场长")));

        CancellationRules.CancelCheck check = rules.evaluate(user());

        assertThat(check.canCancel()).isFalse();
        assertThat(check.blockReasonCodes()).containsExactly(
                CancelBlockReason.RECOVERED_WITHIN_7_DAYS.name(), CancelBlockReason.OWNED_FARM_ACTIVE.name());
        assertThat(check.cancelAvailableDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 26));
        assertThat(check.exitFarms()).hasSize(1);
        BizException blocked = rules.blockedException(check);
        assertThat(blocked.getMessage()).isEqualTo("东山猪场仍在使用，请先转让给场长或停用后再注销");
        assertThat(blocked.getData().toString()).contains("OWNED_FARM_ACTIVE", "cancel_available_date");
    }

    @Test
    void 冻结养殖场阻止注销_停用养殖场给180天清理日() {
        when(farmAccountService.listOwnedFarmsForCancel(10001L)).thenReturn(List.of(
                farm(1003, "北湾牛场", "FROZEN", null, 0, CancelFarmDisposal.WAIT_UNFREEZE, false, null, null),
                farm(1004, "西坡猪场", "DISABLED", 100, 1, CancelFarmDisposal.RETAIN_180_DAYS, false, null,
                        LocalDateTime.of(2026, 9, 1, 10, 0))));
        when(farmAccountService.listExitFarmsForCancel(10001L)).thenReturn(List.of());

        CancellationRules.CancelCheck check = rules.evaluate(user());

        assertThat(check.blockReasonCodes()).contains(CancelBlockReason.OWNED_FARM_FROZEN.name());
        assertThat(check.frozenFarmName()).isEqualTo("北湾牛场");
        assertThat(check.farmCleanupDate()).isEqualTo(java.time.LocalDate.of(2027, 2, 28));
    }

    @Test
    void checklist_version只摘要稳定字段() {
        CancelOwnedFarm a = farm(1001, "东山猪场", "NORMAL", 870, 3,
                CancelFarmDisposal.TRANSFER_OR_DISABLE, true, null, null);
        CancelOwnedFarm b = farm(1001, "东山猪场", "NORMAL", 999, 9,
                CancelFarmDisposal.TRANSFER_OR_DISABLE, true, null, null);
        List<CancelExitFarm> exits = List.of(new CancelExitFarm(1002L, "西岭鸡场", "MANAGER", "场长"));

        String v1 = rules.checklistVersion(List.of(a), exits);
        String v2 = rules.checklistVersion(List.of(b), exits);
        String v3 = rules.checklistVersion(List.of(farm(1001, "东山猪场", "NORMAL", 870, 3,
                CancelFarmDisposal.TRANSFER_OR_DISABLE, true, "刘芳", null)), exits);

        assertThat(v1).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(v2).isEqualTo(v1);
        assertThat(v3).isNotEqualTo(v1);
    }

    @Test
    void 没有停用养殖场时清理日为空_可注销() {
        when(farmAccountService.listOwnedFarmsForCancel(10001L)).thenReturn(List.of(
                farm(1001, "东山猪场", "PENDING", null, 0, CancelFarmDisposal.DELETE_NOW, false, null, null)));
        when(farmAccountService.listExitFarmsForCancel(10001L)).thenReturn(List.of());
        User user = new User();
        user.setId(10001L);

        CancellationRules.CancelCheck check = rules.evaluate(user);

        assertThat(check.canCancel()).isTrue();
        assertThat(check.blockReasons()).isEmpty();
        assertThat(check.farmCleanupDate()).isNull();
        assertThat(check.cancelAvailableDate()).isNull();
    }
}

