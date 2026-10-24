package com.zhimu.server.biz.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.convert.UserConvert;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.request.ProfileUpdateRequest;
import com.zhimu.server.biz.account.service.AccountService;
import com.zhimu.server.biz.account.service.AvatarService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.AccountValidation;
import com.zhimu.server.biz.account.support.Errors;
import com.zhimu.server.biz.account.vo.AccountFarmStatsVO;
import com.zhimu.server.biz.account.vo.AccountOverviewVO;
import com.zhimu.server.biz.account.vo.AccountSecurityVO;
import com.zhimu.server.biz.account.vo.AccountUserVO;
import com.zhimu.server.biz.account.vo.ProfileVO;
import com.zhimu.server.biz.common.api.ChangeRequestService;
import com.zhimu.server.biz.farm.api.AccountFarmStats;
import com.zhimu.server.biz.farm.api.CancelOwnedFarm;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.biz.today.api.ReminderSettingQueryService;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** API-A15、A16。 */
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountStateService accountState;
    private final FarmAccountService farmAccountService;
    private final ChangeRequestService changeRequestService;
    private final ReminderSettingQueryService reminderSettingQueryService;
    private final AccountProperties properties;
    private final AvatarService avatarService;

    @Override
    public AccountOverviewVO overview(Long userId, Long farmId, ClientType client) {
        User user = accountState.requireUser(userId);
        FarmBriefVO currentFarm = farmId == null ? null : farmAccountService.findMyFarmBrief(userId, farmId);
        AccountFarmStats stats = farmAccountService.farmStats(userId);
        if (stats == null) {
            stats = new AccountFarmStats(0, 0, 0);
        }
        Integer pendingCount = null;
        if (currentFarm != null && !FarmRole.STAFF.name().equals(currentFarm.getRole())) {
            long count = changeRequestService.countPendingSubmittedBy(userId, farmId);
            pendingCount = (int) count;
        }
        RecoveryCode active = accountState.activeCode(userId);
        com.zhimu.server.common.vo.ReminderSummaryVO reminder = reminderSettingQueryService.summary(userId);
        if (reminder == null) {
            reminder = com.zhimu.server.common.vo.ReminderSummaryVO.builder().digestEnabled(false).build();
        }
        AccountUserVO accountUser = UserConvert.toAccountUser(user);
        if (accountUser != null) {
            accountUser.setAvatarUrl(avatarService.avatarUrl(user.getAvatarFileId()));
        }
        return AccountOverviewVO.builder()
                .user(accountUser)
                .profileVersion(user.getProfileVersion() == null ? 0 : user.getProfileVersion())
                .currentFarm(currentFarm)
                .farmStats(AccountFarmStatsVO.builder()
                        .totalCount(stats.totalCount())
                        .pendingAuditCount(stats.pendingAuditCount())
                        .rejectedCount(stats.rejectedCount())
                        .build())
                .changeRequestPendingCount(pendingCount)
                .reminderSummary(reminder)
                .security(AccountSecurityVO.builder()
                        .phoneMasked(com.zhimu.server.common.util.Masks.phone(user.getPhone()))
                        .passwordUpdatedAt(BeijingTime.format(user.getPasswordUpdatedAt()))
                        .recoveryCodeGeneratedAt(active == null ? null : BeijingTime.format(active.getCreatedAt()))
                        .cancelRestricted(UserConvert.cancelRestricted(user, properties))
                        .cancelAvailableDate(BeijingTime.format(UserConvert.cancelAvailableDate(user, properties)))
                        .build())
                .build();
    }

    @Override
    @Transactional
    public ProfileVO updateProfile(ProfileUpdateRequest request) {
        User user = accountState.requireUser(com.zhimu.server.common.context.UserContext.requireUserId());
        String problem = AccountValidation.nameProblem(request == null ? null : request.name());
        if (problem != null) {
            throw Errors.fail("name", problem);
        }
        if (request == null || request.version() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少资料版本号");
        }
        String name = AccountValidation.trim(request.name());
        int currentVersion = user.getProfileVersion() == null ? 0 : user.getProfileVersion();
        if (name.equals(user.getName())) {
            return UserConvert.toProfile(user);
        }
        LocalDateTime now = BeijingTime.now();
        int updated = accountState.updateProfile(user.getId(), name, request.version(), now);
        if (updated == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        return ProfileVO.builder()
                .name(name)
                .nameInitial(UserConvert.initial(name))
                .version(request.version() + 1)
                .build();
    }
}
