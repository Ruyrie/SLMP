package com.zhimu.server.biz.health.vo;

import com.zhimu.server.common.annotation.ManagerView;
import lombok.Data;

import java.time.LocalDate;

/** API-D37 登记消毒的任务下拉项。 */
@Data
public class DisinfectTaskOptionVO {

    private Long taskId;
    private Long penId;
    private String penName;
    private LocalDate taskDate;
    /** 10月19日 今天 */
    private String dateText;
    private Boolean overdue;
    /** 计划：聚维酮碘溶液 1:300 · 喷雾 · 每 3 天 */
    private String planText;
    private String planConcentration;
    private String methodCode;
    private Long drugId;
    private Long defaultLotId;
    @ManagerView
    private Long defaultExecutorId;
}
