package com.zhimu.server.biz.health.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** API-D18 用药记录列表项；API-D23 详情另带末 2 行字段。 */
@Data
public class MedicationRecordVO {

    private Long id;
    private String groupNo;
    private Integer version;
    /** BATCH / PEN */
    private String targetType;
    private Long batchId;
    private String batchNo;
    private Long penId;
    private String penName;
    private Long drugId;
    private String drugName;
    private String routeCode;
    private String routeName;
    private String lotNo;
    private LocalDate lotExpireDate;
    private BigDecimal dose;
    private String doseUnit;
    private BigDecimal recommendedDose;
    private Boolean overDose;
    private Integer courseDays;
    private LocalDate firstDate;
    private LocalDate lastDate;
    private Integer withdrawalDays;
    private LocalDate withdrawalEndDate;
    private BigDecimal estimatedQty;
    private BigDecimal usedQty;
    private String usedUnit;
    private String reason;
    private String creatorName;
    private String executorName;
    @JsonProperty("is_mine")
    private Boolean isMine;
    /** 批次已结束 */
    private Boolean locked;
    private ChangeRequestStateVO changeState;
    private List<String> actions = new ArrayList<>();
    @ManagerView
    private BigDecimal unitPrice;
    @ManagerView
    private BigDecimal costAmount;
    /** 仅详情 */
    private String overDoseNote;
    /** 仅详情 */
    private String usageNote;
    /** 仅详情 */
    private Boolean categoryMismatch;
    /** 仅详情 */
    private Long reportId;
    /** 仅详情 */
    private String reportSummary;
    /** 仅详情：当前批次可出栏日期 */
    private LocalDate releaseDateNow;
    private Long drugLotId;
    private Long executorId;
}
