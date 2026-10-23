package com.zhimu.server.biz.health.vo;

import com.zhimu.server.common.annotation.ManagerView;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-D12 登记接种预填。 */
@Data
public class ImmuneRegisterFormVO {

    private ImmunePlanOptionVO plan;
    private Long drugId;
    private String vaccineName;
    private DrugLotVO defaultLot;
    /** 没有可用批号时的提示（按角色） */
    private String noLotText;
    private BigDecimal templateDose;
    private BigDecimal dose;
    private String doseUnit;
    private Integer quantityDefault;
    private Integer currentCount;
    private String methodCode;
    private Boolean backfill;
    private LocalDate dateDefault;
    private LocalDate dateMin;
    private LocalDate dateMax;
    /** 栏舍负责员工；员工不输出 */
    @ManagerView
    private Long defaultExecutorId;
    private String creatorName;
}
