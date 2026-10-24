package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 地区明细浮层中的待审核养殖场。 */
@Getter
@Setter
public class PendingFarmRow {

    private Long farmId;
    private Long auditId;
    private String farmName;
    private String categoryName;
    private LocalDateTime submittedAt;
    private Integer submitNo;
}
