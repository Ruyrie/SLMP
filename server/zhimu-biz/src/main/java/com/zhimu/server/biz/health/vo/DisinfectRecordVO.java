package com.zhimu.server.biz.health.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.MediaItemVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** API-D38–D40 消毒记录。 */
@Data
public class DisinfectRecordVO {

    private Long id;
    private Integer version;
    /** PLAN_TASK / EMPTY_PEN / EMERGENCY */
    private String recordType;
    private String recordTypeText;
    private Long taskId;
    private LocalDate taskDate;
    /** 计划：聚维酮碘溶液 1:500 · 喷雾 · 每 2 天 */
    private String planText;
    private String planConcentration;
    private Long penId;
    private String penName;
    private LocalDateTime actualTime;
    private String executorName;
    private String creatorName;
    private Long drugLotId;
    private String lotNo;
    private String disinfectantName;
    private BigDecimal usageQty;
    private String usageUnit;
    private String concentration;
    private Boolean concentrationConfirmed;
    private String methodCode;
    private String methodName;
    private List<MediaItemVO> photos = new ArrayList<>();
    private Boolean inBatchCost;
    private Boolean inExecRate;
    private Boolean backfill;
    private ChangeRequestStateVO changeState;
    private List<String> actions = new ArrayList<>();
    /** 仅登记返回 */
    private String toast;
    /** 仅登记返回 */
    private Boolean todoCompleted;
    @ManagerView
    private BigDecimal unitPrice;
    @ManagerView
    private BigDecimal costAmount;
    private Long drugId;
    private Long executorId;
}
