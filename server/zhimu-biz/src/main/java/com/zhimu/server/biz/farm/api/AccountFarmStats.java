package com.zhimu.server.biz.farm.api;

/** API-A15 的 farm_stats：养殖场总数；本人为所有者的待审核、已驳回数 */
public record AccountFarmStats(int totalCount, int pendingAuditCount, int rejectedCount) {
}