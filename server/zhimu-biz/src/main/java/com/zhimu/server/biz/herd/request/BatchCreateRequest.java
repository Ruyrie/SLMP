package com.zhimu.server.biz.herd.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-L18 批次入栏（L08）。capacity_confirmed 只在勾选超容量确认后为 true。 */
@Data
public class BatchCreateRequest {
    private Long penId;
    private Long breedId;
    /** COMMERCIAL / BREEDING，缺省按品类 is_breeding */
    private String purpose;
    /** PURCHASED / SELF_BRED */
    private String source;
    private LocalDate entryDate;
    private Integer entryAgeDays;
    private Integer entryCount;
    private BigDecimal entryAvgWeight;
    /** 外购必填，自繁不传 */
    private BigDecimal introUnitPrice;
    private Boolean capacityConfirmed;
}
