package com.zhimu.server.biz.health.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** API-D14–D16 接种记录。 */
@Data
public class ImmuneRecordVO {

    private Long id;
    private Long planId;
    private Integer version;
    private Long batchId;
    private String batchNo;
    private String penName;
    private String vaccineName;
    private String immuneRound;
    private Long drugLotId;
    private String lotNo;
    private LocalDate lotExpireDate;
    private BigDecimal dose;
    private String doseUnit;
    private Integer quantity;
    private BigDecimal totalDose;
    private String methodCode;
    private String methodName;
    private LocalDate vaccinateDate;
    private String executorName;
    private String creatorName;
    private LocalDateTime createdAt;
    private Boolean backfill;
    private ChangeRequestStateVO changeState;
    private String editHint;
    private List<String> actions = new ArrayList<>();
    /** 仅登记返回：待办已自动完成 */
    private Boolean todoCompleted;
    /** 仅登记返回：逾期完成 */
    private Boolean overdueDone;
    @ManagerView
    private BigDecimal unitPrice;
    @ManagerView
    private BigDecimal costAmount;
    private Long drugId;
    private Long executorId;
}
