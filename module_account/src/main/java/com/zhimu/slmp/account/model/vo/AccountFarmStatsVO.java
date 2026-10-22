package com.zhimu.slmp.account.model.vo;

/**
 * A07“我的养殖场”统计：本人为有效成员的养殖场数、本人为所有者的待审核与已驳回数。
 */
public class AccountFarmStatsVO {

    private int totalCount;
    private int pendingAuditCount;
    private int rejectedCount;

    public int getTotalCount() {
        return totalCount;
    }

    public int getPendingAuditCount() {
        return pendingAuditCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }
}
