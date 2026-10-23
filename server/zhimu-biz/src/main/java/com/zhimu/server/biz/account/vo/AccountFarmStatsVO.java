package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A15 的 farm_stats */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountFarmStatsVO {
    private Integer totalCount;
    private Integer pendingAuditCount;
    private Integer rejectedCount;
}
