package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.ProfileUpdateRequest;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.biz.account.vo.AccountOverviewVO;
import com.zhimu.server.biz.account.vo.ProfileVO;
import com.zhimu.server.biz.common.api.ChangeRequestService;
import com.zhimu.server.biz.farm.api.AccountFarmStats;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.biz.today.api.ReminderSettingQueryService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.common.vo.ReminderSummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceImplTest {

    private final AccountStateService accountState = mock(AccountStateService.class);
    private final FarmAccountService farmAccountService = mock(FarmAccountService.class);
    private final ChangeRequestService changeRequestService = mock(ChangeRequestService.class);
    private final ReminderSettingQueryService reminderService = mock(ReminderSettingQueryService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AccountProperties properties = new AccountProperties();
    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        FixedTime.set();
        service = new AccountServiceImpl(accountState, farmAccountService, changeRequestService, reminderService,
                properties);
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
    }

    @AfterEach
    void tearDown() {
        FixedTime.reset();
        UserContext.clear();
    }

    private static User user() {
        User user = new User();
        user.setId(10001L);
        user.setPhone("13800006620");
        user.setName("王建国");
        user.setStatus("NORMAL");
        user.setProfileVersion(3);
        user.setPasswordUpdatedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        return user;
    }

    @Test
    void A15所有者返回当前养殖场与修改申请计数() {
        User user = user();
        when(accountState.requireUser(10001L)).thenReturn(user);
        when(farmAccountService.findMyFarmBrief(10001L, 1001L)).thenReturn(FarmBriefVO.builder()
                .farmId(1001L).name("东山猪场").speciesText("育肥猪").status("NORMAL").statusText("正常")
                .role("OWNER").build());
        when(farmAccountService.farmStats(10001L)).thenReturn(new AccountFarmStats(3, 1, 0));
        when(changeRequestService.countPendingSubmittedBy(10001L, 1001L)).thenReturn(2L);
        when(reminderService.summary(10001L)).thenReturn(ReminderSummaryVO.builder()
                .digestEnabled(true).digestTime(LocalTime.of(7, 0)).quietEnabled(true)
                .quietStart(LocalTime.of(22, 0)).quietEnd(LocalTime.of(7, 0)).build());
        RecoveryCode active = new RecoveryCode();
        active.setCreatedAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        when(accountState.activeCode(10001L)).thenReturn(active);

        AccountOverviewVO vo = service.overview(10001L, 1001L, ClientType.APP_ANDROID);

        assertThat(vo.getUser().getName()).isEqualTo("王建国");
        assertThat(vo.getProfileVersion()).isEqualTo(3);
        assertThat(vo.getCurrentFarm().getFarmId()).isEqualTo(1001L);
        assertThat(vo.getFarmStats().getTotalCount()).isEqualTo(3);
        assertThat(vo.getChangeRequestPendingCount()).isEqualTo(2);
        assertThat(vo.getReminderSummary().getDigestEnabled()).isTrue();
        assertThat(vo.getSecurity().getPhoneMasked()).isEqualTo("138****6620");
        assertThat(vo.getSecurity().getRecoveryCodeGeneratedAt()).isEqualTo("2026-09-20 10:00:00");
        assertThat(vo.getSecurity().getCancelRestricted()).isFalse();
    }

    @Test
    void A15员工不输出修改申请计数() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(farmAccountService.findMyFarmBrief(10001L, 1001L)).thenReturn(FarmBriefVO.builder()
                .farmId(1001L).name("东山猪场").status("NORMAL").role("STAFF").build());
        when(farmAccountService.farmStats(10001L)).thenReturn(new AccountFarmStats(1, 0, 0));
        when(reminderService.summary(10001L)).thenReturn(ReminderSummaryVO.builder().digestEnabled(true).build());

        AccountOverviewVO vo = service.overview(10001L, 1001L, ClientType.APP_ANDROID);

        assertThat(vo.getChangeRequestPendingCount()).isNull();
        verify(changeRequestService, never()).countPendingSubmittedBy(any(), any());
    }

    @Test
    void A15无养殖场上下文不输出当前养殖场与计数() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(farmAccountService.farmStats(10001L)).thenReturn(new AccountFarmStats(0, 0, 0));
        when(reminderService.summary(10001L)).thenReturn(ReminderSummaryVO.builder().digestEnabled(false).build());

        AccountOverviewVO vo = service.overview(10001L, null, ClientType.APP_ANDROID);

        assertThat(vo.getCurrentFarm()).isNull();
        assertThat(vo.getChangeRequestPendingCount()).isNull();
        assertThat(vo.getFarmStats().getTotalCount()).isZero();
    }

    @Test
    void A16姓名乐观锁冲突返回40902() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(accountState.updateProfile(eq(10001L), eq("王建军"), eq(2), any())).thenReturn(0);

        BizException e = (BizException) catchException(() ->
                service.updateProfile(new ProfileUpdateRequest("王建军", 2)));

        assertThat(e.getCode()).isEqualTo(40902);
        assertThat(e.getMessage()).isEqualTo("内容已更新，请刷新后再修改");
    }

    @Test
    void A16成功返回新版本与姓名首字() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(accountState.updateProfile(eq(10001L), eq("王建军"), eq(3), any())).thenReturn(1);

        ProfileVO vo = service.updateProfile(new ProfileUpdateRequest(" 王建军 ", 3));

        assertThat(vo.getName()).isEqualTo("王建军");
        assertThat(vo.getNameInitial()).isEqualTo("王");
        assertThat(vo.getVersion()).isEqualTo(4);
    }

    @Test
    void A16姓名超长按码点拒绝() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        BizException e = (BizException) catchException(() ->
                service.updateProfile(new ProfileUpdateRequest("😀".repeat(21), 3)));
        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getMessage()).isEqualTo("姓名不能超过 20 个字");
    }

    private static Throwable catchException(org.junit.jupiter.api.function.Executable executable) {
        try {
            executable.execute();
        } catch (Throwable e) {
            return e;
        }
        throw new AssertionError("expected exception");
    }
}
