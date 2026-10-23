package com.zhimu.server.biz.health.vo;

import com.zhimu.server.common.annotation.ManagerView;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** API-D01 批次免疫概况（D03–D05 返回刷新后的同结构）。 */
@Data
public class ImmunePlanOverviewVO {

    private Long batchId;
    private String batchNo;
    private Long penId;
    private String penName;
    private String breedName;
    private Long categoryId;
    private LocalDate entryDate;
    private Integer entryAgeDays;
    private Integer dayAge;
    private Integer currentCount;
    private Boolean batchEnded;
    private Boolean hasTemplate;
    private Boolean canManage;
    private Counts counts;
    /** 入栏前项目；员工不输出 */
    @ManagerView
    private List<PreEntryItem> preEntryItems = new ArrayList<>();
    /** 入栏前待处理数；员工不输出 */
    @ManagerView
    private Integer preEntryPendingCount;

    @Data
    public static class Counts {
        private Integer all;
        private Integer pending;
        private Integer missed;
        private Integer postponed;
        private Integer done;
        private Integer cancelled;
    }

    /** 入栏前项目（plan_id、疫苗、免次、模板日龄、version） */
    @Data
    public static class PreEntryItem {
        private Long planId;
        private String vaccineName;
        private String immuneRound;
        private Integer templateAgeDay;
        private Integer version;
    }
}
