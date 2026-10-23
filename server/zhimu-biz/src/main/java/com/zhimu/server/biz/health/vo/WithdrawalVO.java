package com.zhimu.server.biz.health.vo;

import com.zhimu.server.common.annotation.ManagerView;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** API-D17 休药状态与按药品计算。 */
@Data
public class WithdrawalVO {

    private Long batchId;
    private String batchNo;
    private String penName;
    private Integer currentCount;
    /** NONE / IN_WITHDRAWAL / ENDED */
    private String status;
    private String statusText;
    private LocalDate releaseDate;
    private Integer daysLeft;
    private String decisiveDrugName;
    private LocalDate lastMedDate;
    private Integer withdrawalDays;
    /** 0–1 */
    private Double progress;
    /** 员工不输出 */
    @ManagerView
    private List<LocalDate> remindDates = new ArrayList<>();
    /** 员工不输出 */
    @ManagerView
    private String remindText;
    private List<WithdrawalDrugVO> drugs = new ArrayList<>();
}
