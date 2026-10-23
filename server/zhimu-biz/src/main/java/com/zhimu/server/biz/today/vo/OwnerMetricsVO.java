package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** 养殖户版首页指标（API-T01 owner_metrics，员工不返回）。 */
@Getter
@Setter
public class OwnerMetricsVO {

    private Integer stockCount;
    private Integer batchCount;
    /** 今天到期和已逾期且未完成的待办数 */
    private Integer todayTodoCount;
    private Integer overdueCount;
    /** 未解除的预警类待办数 */
    private Integer warningCount;
    private Integer receivableOverdueCount;
    private Integer receivableMaxOverdueDays;
    /** HEAD / BIRD */
    private String countUnit;
    private String countUnitText;
    private BigDecimal penOccupancyRate;
    private List<String> warningNames;
    /** 管理视图 */
    private BigDecimal receivableAmount;
}
