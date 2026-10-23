package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 注销清单中本人创建的养殖场 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOwnedFarmVO {
    private Long farmId;
    private String name;
    private String farmStatus;
    private String farmStatusText;
    private String disposal;
    private String disposalText;
    private Integer currentStock;
    private String countUnitText;
    private Integer memberCount;
    private Boolean transferable;
    private String transferPendingToName;
}
