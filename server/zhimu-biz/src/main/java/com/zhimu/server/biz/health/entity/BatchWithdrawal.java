package com.zhimu.server.biz.health.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 批次休药汇总（t_batch_withdrawal）：每批次一行，重算前加锁读取。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_withdrawal")
public class BatchWithdrawal extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private LocalDate releaseDate;
    private Long decisiveRecordId;
    private String decisiveDrugName;
    private LocalDate lastMedDate;
    private Integer withdrawalDays;
    private Integer cycleNo;
    private LocalDateTime recalculatedAt;
}
