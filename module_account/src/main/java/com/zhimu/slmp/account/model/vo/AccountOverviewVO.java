package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;
import com.zhimu.slmp.common.model.vo.ReminderSummaryVO;

/**
 * API-A15 我的页汇总（带 X-Farm-Id）：个人信息、当前养殖场、养殖场统计、修改申请计数（员工不输出）、提醒摘要、账号安全。
 */
public class AccountOverviewVO {

    @Nullable
    private AccountUserVO user;
    private int profileVersion;
    /** X-Farm-Id 有效时输出 */
    @Nullable
    private FarmBriefVO currentFarm;
    @Nullable
    private AccountFarmStatsVO farmStats;
    /** 员工或没有当前养殖场时不输出 */
    @Nullable
    private Integer changeRequestPendingCount;
    @Nullable
    private ReminderSummaryVO reminderSummary;
    @Nullable
    private AccountSecurityVO security;

    @Nullable
    public AccountUserVO getUser() {
        return user;
    }

    public int getProfileVersion() {
        return profileVersion;
    }

    @Nullable
    public FarmBriefVO getCurrentFarm() {
        return currentFarm;
    }

    @Nullable
    public AccountFarmStatsVO getFarmStats() {
        return farmStats;
    }

    @Nullable
    public Integer getChangeRequestPendingCount() {
        return changeRequestPendingCount;
    }

    @Nullable
    public ReminderSummaryVO getReminderSummary() {
        return reminderSummary;
    }

    @Nullable
    public AccountSecurityVO getSecurity() {
        return security;
    }

    /** 修改姓名成功后在本地更新（version 取 A16 返回值） */
    public void applyProfile(@Nullable AccountUserVO newUser, int newVersion) {
        this.user = newUser;
        this.profileVersion = newVersion;
    }
}
