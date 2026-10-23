package com.zhimu.server.biz.account.vo;

import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.common.vo.ReminderSummaryVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A15 我的页汇总 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOverviewVO {
    private AccountUserVO user;
    private Integer profileVersion;
    private FarmBriefVO currentFarm;
    private AccountFarmStatsVO farmStats;
    /** 员工或没有当前养殖场时不输出 */
    private Integer changeRequestPendingCount;
    private ReminderSummaryVO reminderSummary;
    private AccountSecurityVO security;
}
