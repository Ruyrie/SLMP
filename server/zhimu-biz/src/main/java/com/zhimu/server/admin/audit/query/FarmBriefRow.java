package com.zhimu.server.admin.audit.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 所有者名下其他养殖场（注册审核详情）。 */
@Getter
@Setter
public class FarmBriefRow {

    private Long farmId;
    private String name;
    private String auditStatus;
    private String categoryName;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
