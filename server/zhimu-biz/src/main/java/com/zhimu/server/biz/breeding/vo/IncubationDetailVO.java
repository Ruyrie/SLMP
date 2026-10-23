package com.zhimu.server.biz.breeding.vo;

import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** 孵化详情（API-B28、B29、B30）：current_step 为 1 / 2 / 3，已出雏为 0。 */
@Getter
@Setter
public class IncubationDetailVO {

    private long incubationId;
    private long breedingBatchId;
    private String farmName;
    private String flockText;
    private String breedName;
    private int incubationDays;
    private String setDate;
    private String expectedHatchDate;
    private int currentStep;
    private int setCount;
    private int daysToHatch;
    private int version;
    private String status;
    private String statusText;
    private String reminderText;
    private String candleDate;
    private Integer fertileCount;
    private BigDecimal fertilityRate;
    private String hatchDate;
    private Integer hatchedCount;
    private Integer healthyCount;
    private Integer weakCount;
    private BigDecimal hatchRate;
    private Boolean transferToBatch;
    private Long targetPenId;
    private String targetPenName;
    private String offspringBreedName;
    private Long batchId;
    private String batchNo;
    private List<TimelineItem> timeline;
    private boolean canEdit;
    private boolean canRequestChange;
    private ChangeRequestStateVO changeState;

    /** 步骤登记记录 */
    @Getter
    @Setter
    public static class TimelineItem {
        /** SET / CANDLE / HATCH */
        private String step;
        private String stepText;
        private String registeredAt;
        private UserBriefVO registrant;
    }
}
